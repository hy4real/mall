# Mall 用户下单流程：老写法 vs 2026 现代写法对照笔记

> 基于精读以下文件的产出：
> - `mall-portal/.../controller/OmsPortalOrderController.java`
> - `mall-portal/.../service/impl/OmsPortalOrderServiceImpl.java`
> - `mall-portal/src/main/resources/dao/PortalOrderDao.xml`
> - `mall-portal/.../component/CancelOrderSender.java`
> - `mall-portal/.../component/CancelOrderReceiver.java`
> - `mall-portal/.../config/RabbitMqConfig.java`
> - `mall-portal/.../domain/QueueEnum.java`

---

## 1. 库存三态 SQL（旧写法分析）

三个库存状态操作位于 `PortalOrderDao.xml`（行 91-113），核心逻辑如下：

### lockStockBySkuId（下单时锁定库存）

```sql
UPDATE pms_sku_stock
SET lock_stock = lock_stock + #{quantity}
WHERE id = #{productSkuId}
  AND lock_stock + #{quantity} <= stock
```

**问题分析：**

| 问题 | 说明 |
|------|------|
| **check-then-act 的 TOCTOU gap** | 虽然 `WHERE` 条件和 `SET` 在同一条 UPDATE 里保证了行级原子性，但在 Java 层 `lockStock()` 方法（行 750-758）中先 `selectByPrimaryKey` 读取了一次 `skuStock`（读后未使用，纯粹浪费了一次 DB 调用），再执行 `lockStockBySkuId`。这两步之间没有事务保护，且那条读操作本身毫无意义 |
| **无版本号/CAS 保护** | 没有用 `version` 字段做乐观锁，仅靠 `lock_stock + #{quantity} <= stock` 条件过滤。在极端并发下（同一 SKU 被多个订单同时锁定），MySQL 的行锁会串行化，但锁等待时间不可控，无法给用户明确的「排队中」反馈 |
| **单品逐条锁定，非批量** | `lockStock()` 用 for 循环逐 SKU 调用，如果第 3 个 SKU 锁定失败，前 2 个已经锁定的不会回滚（方法没有 `@Transactional`），造成**库存泄漏**——用户看到下单失败，但部分 SKU 的 lock_stock 已经增加且不会自动释放 |

### reduceSkuStock（支付成功，扣减真实库存）

```sql
UPDATE pms_sku_stock
SET lock_stock = lock_stock - #{quantity},
    stock = stock - #{quantity}
WHERE id = #{productSkuId}
  AND stock - #{quantity} >= 0
  AND lock_stock - #{quantity} >= 0
```

**问题分析：**

| 问题 | 说明 |
|------|------|
| **逻辑错误** | `paySuccess()` 中先 `updateByExampleSelective` 把订单状态改为已支付，然后才逐条 `reduceSkuStock`。如果中间某个 SKU 扣减失败（`count == 0`），抛出 `Asserts.fail`，但订单状态已经改为 1（待发货）了——**订单状态和库存状态不一致** |
| **缺乏幂等性** | 没有唯一约束或幂等键。如果支付回调重试，会重复扣减库存 |
| **没有兜底补偿** | 扣减失败后没有回滚订单状态的逻辑 |

### releaseStockBySkuId（取消订单，释放锁定库存）

```sql
UPDATE pms_sku_stock
SET lock_stock = lock_stock - #{quantity}
WHERE id = #{productSkuId}
  AND lock_stock - #{quantity} >= 0
```

**问题分析：**

| 问题 | 说明 |
|------|------|
| **release 可能失败但操作不可回滚** | `cancelOrder()` 中逐条 release，如果某条失败抛异常，已 release 的 SKU 无法恢复 |
| **lock_stock 溢出保护用 `>= 0` 是对的**，但缺少 `lock_stock - #{quantity} < 0` 时的告警日志——正常业务不应该出现，出现了说明数据已经不一致了 |

---

## 2. 延迟取消机制（旧写法分析）

### 架构

```
Producer → TTL Queue (mall.order.cancel.ttl)
              ↓ TTL到期 / 死信
         Dead Letter Exchange (mall.order.direct)
              ↓
         Real Queue (mall.order.cancel)
              ↓
         CancelOrderReceiver.handle(Long orderId)
```

### 关键组件

| 组件 | 文件 | 说明 |
|------|------|------|
| QueueEnum | `domain/QueueEnum.java` | 硬编码 exchange/name/routeKey |
| RabbitMqConfig | `config/RabbitMqConfig.java` | 声明 Exchange、Queue、Binding |
| CancelOrderSender | `component/CancelOrderSender.java` | 发送带 TTL 的消息 |
| CancelOrderReceiver | `component/CancelOrderReceiver.java` | `@RabbitListener(queues = "mall.order.cancel")` 硬编码队列名 |

### 问题分析

| 问题 | 说明 |
|------|------|
| **RabbitMQ per-message TTL 的队头阻塞问题** | `CancelOrderSender` 用 `message.getMessageProperties().setExpiration()` 设置每条消息的 TTL。RabbitMQ 的行为是：只有队头消息过期后才检查下一条。如果先发了一个 60 分钟 TTL 的消息，后面再发一个 5 分钟的，5 分钟的那条要等 60 分钟那条过期后才能被消费。这直接导致**取消延迟不准确** |
| **Receiver 中硬编码队列名字符串** | `@RabbitListener(queues = "mall.order.cancel")` 没有用 `QueueEnum.QUEUE_ORDER_CANCEL.getName()`，两边不一致的风险 |
| **无消息确认机制** | Receiver 直接调用 `cancelOrder()`，没有 `acknowledge-mode` 配置，默认 auto-ack。如果处理过程中抛异常，消息丢失，订单永远不会被取消 |
| **无重试机制** | 没有配置 `RetryOperationsInterceptor` 或 Spring Retry。处理失败直接丢消息 |
| **无死信兜底** | 已经用了一次死信队列做 TTL，但真正消费失败时没有二级死信队列来兜底 |
| **消息体仅传 orderId（Long）** | 没有消息版本号、发送时间戳。无法判断消息是否过期（比如服务停机 2 小时后再消费，会取消一个已经付款的订单） |
| **缺少幂等检查** | Receiver 没有检查订单当前状态。如果消息重复投递（网络抖动），`cancelOrder` 内部虽有 `status == 0` 检查，但那是查+改两步，非原子操作 |

---

## 3. 逐方法对照表

| 方法 | 文件路径 | 行号 | 旧写法要点 | 2026 现代写法 | 改动价值 |
|------|----------|------|-----------|--------------|---------|
| `generateConfirmOrder` | `OmsPortalOrderServiceImpl.java` | 72-93 | 6 个 `@Autowired` 字段注入；方法内 5 次独立查询无缓存；`integrationConsumeSettingMapper.selectByPrimaryKey(1L)` 硬编码 ID | 构造器注入 + `@RequiredArgsConstructor`；配置项用 `@Cacheable` 缓存；配置 ID 提取为常量或配置属性 | 消除隐式依赖，降低 DB 压力，消除 magic number |
| `generateOrder` | `OmsPortalOrderServiceImpl.java` | 96-251 | **巨型方法 155 行**，无 `@Transactional`，混合校验+计算+DB写入+MQ 发送；`lockStock` 失败后已锁库存不释放；`SimpleDateFormat` 非线程安全；手动 `new HashMap` 组装返回值；`new BigDecimal(0)` 而非 `BigDecimal.ZERO` | 拆分为 `validateOrder()` / `calculateAmounts()` / `persistOrder()` / `sendOrderCreatedEvent()` 四个私有方法；整个方法加 `@Transactional(rollbackFor = Exception.class)`；用 `DateTimeFormatter`（不可变、线程安全）；返回 `record GenerateOrderResult(OmsOrder order, List<OmsOrderItem> items)` | 消除 155 行上帝方法；事务保证库存原子性；消除线程安全隐患；类型安全的返回值 |
| `lockStock` | `OmsPortalOrderServiceImpl.java` | 750-758 | for 循环逐 SKU 调用 `selectByPrimaryKey` + `lockStockBySkuId`；先 SELECT 再 UPDATE（SELECT 结果未使用）；无事务 | 用批量 SQL `lockStockBatch(itemList)` 一次锁定所有 SKU；去掉无用的 SELECT；`@Transactional` 包裹 | 减少一次无意义的 DB 往返；批量操作消除部分锁定后失败的不一致问题 |
| `hasStock` | `OmsPortalOrderServiceImpl.java` | 764-774 | 依赖 `CartPromotionItem.realStock` 字段做前置判断，但该值可能是快照数据 | 如果用了 Redis Lua 预扣减，前置判断就不需要了；如果要保留，用 `skuStockMapper` 批量查真实库存 | 消除快照与真实数据不一致导致的误判 |
| `generateOrderSn` | `OmsPortalOrderServiceImpl.java` | 462-477 | `SimpleDateFormat` 非线程安全；Redis INCR 无 TTL，key 永远累积 | `DateTimeFormatter.ISO_LOCAL_DATE`；Redis key 加 `EXPIRE 86400`（保留 24 小时防重复） | 消除并发下的日期格式化 bug；防止 Redis key 泄漏 |
| `paySuccess` | `OmsPortalOrderServiceImpl.java` | 255-283 | 先改订单状态，再扣库存。中间失败导致状态不一致；无幂等性检查；逐条 SKU 扣减无事务 | 先扣库存再改状态（或用 Saga 编排）；加幂等校验（`payment_status` 字段或唯一约束）；整个方法加 `@Transactional` | 保证订单-库存一致性；支持支付回调安全重试 |
| `cancelTimeOutOrder` | `OmsPortalOrderServiceImpl.java` | 286-312 | 一次性查出所有超时订单，逐个处理；无分页，订单量大时 OOM；批量改状态 + 逐个释放库存，中间失败无回滚 | 分页查询 + 逐页处理（`LIMIT 100`）；整个方法加 `@Transactional` 或用 Saga 保证最终一致；释放库存失败记录到补偿表 | 防止大查询 OOM；保证取消操作的原子性 |
| `cancelOrder` | `OmsPortalOrderServiceImpl.java` | 315-348 | 查+改非原子操作（`selectByExample` + `updateByPrimaryKeySelective`）；逐条 release SKU，失败则部分释放；`updateCouponStatus` 查 `get(0)` 不检查 index | 用 `UPDATE ... SET status=4 WHERE id=? AND status=0` 原子操作，通过 affected rows 判断是否成功；批量 release SQL；用 Optional 包装查询结果 | 消除 TOCTOU 竞态；保证补偿操作完整性 |
| `sendDelayMessageCancelOrder` | `OmsPortalOrderServiceImpl.java` | 351-357 | 每次从 DB 查 `OmsOrderSetting` 获取超时时间；用 per-message TTL（有队头阻塞问题） | 配置项缓存在内存（`@PostConstruct` 加载）或 Redis；改用 RabbitMQ Delayed Message Plugin 或 RocketMQ 延迟消息 | 消除冗余 DB 查询；解决队头阻塞导致的延迟不准 |
| `confirmReceiveOrder` | `OmsPortalOrderServiceImpl.java` | 360-373 | 先 `selectByPrimaryKey` 全量读取，再修改 3 个字段后 `updateByPrimaryKey`（覆盖所有列，包括可能被并发修改的列） | `UPDATE oms_order SET status=3, confirm_status=1, receive_time=NOW() WHERE id=? AND member_id=? AND status=2`，单条原子 SQL | 防止并发覆盖其他字段更新 |
| `list` | `OmsPortalOrderServiceImpl.java` | 376-416 | 两阶段查询：先查订单列表，再 `IN` 查 order_item；手动拼接 `OmsOrderDetail`；`status == -1` 魔数转 null | 用 MyBatis 嵌套查询或 `JOIN` 一次查出；定义 `OrderStatus` 枚举替代魔数；用 MapStruct 或 `BeanUtil` 替代手动 copy | 减少一次 DB 往返；消除魔数；减少样板代码 |
| `deleteOrder` | `OmsPortalOrderServiceImpl.java` | 431-443 | `order.getStatus() == 3 || order.getStatus() == 4` 魔数判断；`updateByPrimaryKey` 全量更新 | `DELETE_FLAG = 1 WHERE id=? AND member_id=? AND status IN (3,4)` 原子操作；用 `OrderStatus.COMPLETED, OrderStatus.CLOSED` 枚举 | 防并发覆盖；消除魔数 |
| `handleCouponAmount` | `OmsPortalOrderServiceImpl.java` | 653-667 | `coupon.getUseType().equals(0)` / `equals(1)` / `equals(2)` 魔数 + if-else 链 | 定义 `enum CouponUseType { ALL, CATEGORY, PRODUCT }`；用 switch 表达式匹配 | 消除魔数；编译器强制穷举检查 |
| `calcTotalAmount` / `calcPromotionAmount` / `calcCouponAmount` | `OmsPortalOrderServiceImpl.java` | 739-610 | `new BigDecimal("0")` / `new BigDecimal(0)` 创建临时对象；for 循环手动累加 | `BigDecimal.ZERO`；`orderItemList.stream().map(...).reduce(BigDecimal.ZERO, BigDecimal::add)` | 减少对象创建；流式表达更清晰 |
| `generateOrder` 返回值 | `OmsPortalOrderServiceImpl.java` | 248-251 | `Map<String, Object>` 无类型安全，调用方需强转 | `record GenerateOrderResult(OmsOrder order, List<OmsOrderItem> items)` | 编译期类型检查，IDE 友好 |

---

## 4. 架构级改进建议

### 4.1 Saga 模式替代同步大事务

当前 `generateOrder()` 155 行代码中混合了库存锁定、优惠券核销、积分扣除、购物车清理、MQ 发送等操作，任何一步失败都无法保证一致性。改用 Saga 编排器（如 Camunda / Seata / 自研状态机），将每个步骤定义为可补偿的 Saga Activity，失败时按反序执行补偿操作（释放库存、退还优惠券、返还积分、恢复购物车）。

### 4.2 Redis Lua 脚本替代 MySQL 乐观锁做库存预扣

当前 lock/reduce/release 三个 SQL 全部走 MySQL 行锁，高并发下行锁竞争严重，TPS 上限受制于单行锁等待。改用 Redis Lua 脚本原子预扣库存（`stock >= quantity` 时原子减库存），订单取消/超时时回补到 Redis，异步同步到 MySQL 做终态持久化。Redis 单线程执行 Lua 天然串行，无竞态问题，TPS 可达 10w+。

### 4.3 事件驱动架构解耦订单创建后的副作用

当前 `generateOrder()` 末尾同步调用：删购物车、发延迟消息、更新优惠券状态、扣积分。应改为发布 `OrderCreatedEvent`，由独立的 EventHandler 异步处理（删购物车、发延迟取消消息等），主流程只负责核心的「校验 → 锁库存 → 写订单」。这样即使下游服务（如积分服务）短暂不可用，也不影响下单主流程，事件消费端可通过重试保证最终一致。

### 4.4 用 RocketMQ 延迟消息替代 RabbitMQ TTL 死信方案

RabbitMQ 的 per-message TTL 存在队头阻塞问题，且死信链路复杂难调试。RocketMQ 原生支持 18 个延迟级别（1s/5s/10s/30s/1m/.../2h），消息精准投递，不依赖死信转发。如果技术栈允许迁移，这是最干净的方案。如果不迁移，至少改用 RabbitMQ Delayed Message Plugin 来解决队头阻塞。

### 4.5 补偿表 + 定时扫描作为最后兜底

任何分布式方案都可能出现消息丢失或消费失败的情况。增加一张 `order_compensation` 表，记录「需要取消的超时订单 ID + 预期执行时间」，定时任务每分钟扫描一次执行取消操作。这是成本最低的兜底方案，确保即使 MQ 全部故障，超时订单也一定会被关闭、库存一定会被释放。
