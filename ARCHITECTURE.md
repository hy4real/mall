# mall 项目架构地图

> AI 辅助生成的速读文档。目标：让一个**不熟悉 Java 项目**的开发者在 30 分钟内建立对仓库的全局认知。
> 不替代源码阅读，但能让你知道**该读哪些类**、**它们怎么连起来**。

---

## 1. 模块依赖图

7 个 Maven 模块，分两层：基础设施层 + 业务层。父 `pom.xml` 锁定 Spring Boot 2.7.5、JDK 11。

```mermaid
graph TD
    subgraph 业务层
        ADMIN[mall-admin<br/>后台管理 API<br/>端口 8080]
        PORTAL[mall-portal<br/>前台商城 API<br/>端口 8085]
        SEARCH[mall-search<br/>ES 商品搜索<br/>端口 8081]
        DEMO[mall-demo<br/>演示模块]
    end

    subgraph 基础设施层
        SECURITY[mall-security<br/>JWT + Spring Security]
        MBG[mall-mbg<br/>MyBatis Generator 产物<br/>实体 + 基础 CRUD]
        COMMON[mall-common<br/>工具类 + CommonResult + 日志]
    end

    ADMIN --> SECURITY
    ADMIN --> MBG
    ADMIN --> COMMON
    PORTAL --> SECURITY
    PORTAL --> MBG
    PORTAL --> COMMON
    SEARCH --> MBG
    SEARCH --> COMMON
    SECURITY --> COMMON
    MBG --> COMMON
```

**关键事实**：
- `mall-admin` 和 `mall-portal` 是两个独立可启动的 Spring Boot 应用，**不是微服务**——共享数据库的两个单体。
- `mall-mbg` 几乎都是自动生成代码（`mvn mybatis-generator:generate`），不要手改。
- 自定义 SQL 在 `mall-admin/src/main/resources/dao/*.xml`（不在 mall-mbg）。
- `mall-search` 独立用 Spring Data Elasticsearch，跟 MyBatis 那套并行。

---

## 2. 调用分层

```
HTTP 请求
   ↓
Controller (com.macro.mall.controller / portal.controller)
   ↓  参数校验 + Swagger 注解
Service (XxxService → XxxServiceImpl)
   ↓  业务逻辑 + @Transactional 边界
DAO (com.macro.mall.dao 自定义 / com.macro.mall.mapper MBG 生成)
   ↓
MySQL / Redis / RabbitMQ / ES
```

返回统一包装 `CommonResult<T>`（在 mall-common），格式 `{code, message, data}`。

---

## 3. 数据库领域模型

76 张表按前缀分 5 域。**记前缀就记住了一半架构**：

| 前缀 | 域 | 代表表 |
|------|---|--------|
| `pms_` | 商品 Product | product / sku_stock / product_category / brand |
| `oms_` | 订单 Order | order / order_item / cart_item / order_return_apply |
| `sms_` | 营销 Sales/Marketing | coupon / flash_promotion / home_advertise |
| `cms_` | 内容 Content | subject / topic / help |
| `ums_` | 用户 User | member / admin / role / resource / menu |

### 核心 ER（精简版）

```mermaid
erDiagram
    PMS_PRODUCT ||--o{ PMS_SKU_STOCK : "1商品多SKU"
    PMS_PRODUCT }o--|| PMS_BRAND : "品牌"
    PMS_PRODUCT }o--|| PMS_PRODUCT_CATEGORY : "分类"
    UMS_MEMBER ||--o{ OMS_CART_ITEM : "购物车"
    OMS_CART_ITEM }o--|| PMS_SKU_STOCK : "选定SKU"
    UMS_MEMBER ||--o{ OMS_ORDER : "下单"
    OMS_ORDER ||--o{ OMS_ORDER_ITEM : "订单项"
    OMS_ORDER_ITEM }o--|| PMS_PRODUCT : "快照引用"
    UMS_MEMBER }o--|| UMS_MEMBER_LEVEL : "等级"
```

### RBAC 体系（mall-admin 后台用）

```
ums_admin ⟷ ums_admin_role_relation ⟷ ums_role
                                          ⟷ ums_role_resource_relation ⟷ ums_resource (API 资源)
                                          ⟷ ums_role_menu_relation     ⟷ ums_menu     (前端菜单)
```

### 关键状态字段（必记）

| 表.字段 | 取值 |
|---|---|
| `oms_order.status` | 0=待付款 / 1=待发货 / 2=已发货 / 3=已完成 / 4=已关闭 / 5=无效 |
| `oms_order.pay_type` | 0=未支付 / 1=支付宝 / 2=微信 |
| `pms_product.publish_status` | 0=下架 / 1=上架 |
| `pms_product.verify_status` | 0=未审核 / 1=审核通过 |
| `pms_product.promotion_type` | 0=原价 / 1=促销价 / 2=会员价 / 3=阶梯价 / 4=满减 / 5=限时购 |

---

## 4. 关键流程一：后台登录 + JWT 鉴权

```mermaid
sequenceDiagram
    autonumber
    actor U as 前端
    participant C as UmsAdminController
    participant S as UmsAdminServiceImpl
    participant JW as JwtTokenUtil
    participant F as JwtAuthenticationTokenFilter
    participant SC as SecurityContext

    Note over U,SC: 登录阶段
    U->>C: POST /admin/login {username, password}
    C->>S: login(username, password)
    S->>S: loadUserByUsername (查 ums_admin)
    S->>S: BCrypt.matches(原文, 哈希)
    S->>JW: generateToken(userDetails)
    JW-->>S: HS512 JWT (7 天)
    S-->>C: token
    C-->>U: {token, tokenHead:"Bearer "}

    Note over U,SC: 后续请求
    U->>F: GET /xxx  Header: Authorization: Bearer xxx
    F->>JW: getUserNameFromToken
    F->>S: loadUserByUsername (再查一次拿权限)
    F->>JW: validateToken
    F->>SC: 写入 Authentication
    F->>C: 放行 → Controller
```

**关键类**（**精读这五个，鉴权就通了**）：

| 文件 | 作用 |
|---|---|
| `mall-admin/.../controller/UmsAdminController.java#login` | HTTP 入口 |
| `mall-admin/.../service/impl/UmsAdminServiceImpl.java#login` | 密码校验 + 发 Token |
| `mall-security/.../util/JwtTokenUtil.java` | JWT 编解码（HS512，7 天） |
| `mall-security/.../component/JwtAuthenticationTokenFilter.java` | 每请求过滤器 |
| `mall-security/.../config/SecurityConfig.java` | 白名单 + 过滤器链 |

**动态权限**：URL → 资源映射由 `DynamicSecurityMetadataSource` 从 `ums_resource` 加载（缓存到 Redis），`DynamicAccessDecisionManager` 决策。要加新接口的权限控制，去 `ums_resource` 表加记录即可，不用改代码。

---

## 5. 关键流程二：下单（mall-portal 最复杂的一条链）

入口 `OmsPortalOrderController#generateOrder`，核心实现在 `OmsPortalOrderServiceImpl#generateOrder`（约 150 行）。

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant C as OmsPortalOrderController
    participant S as OmsPortalOrderServiceImpl
    participant DAO as PortalOrderDao
    participant R as Redis
    participant MQ as RabbitMQ
    participant DB as MySQL

    U->>C: POST /order/generateOrder
    C->>S: generateOrder(orderParam)
    S->>DAO: 取购物车 + 优惠信息
    S->>DAO: hasStock 校验库存
    S->>DAO: lockStockBySkuId<br/>(lock_stock += qty)
    Note right of DAO: 只加锁，不真扣
    S->>S: 计算金额 (优惠券/积分/运费/分摊)
    S->>R: generateOrderSn() 拿订单号
    S->>DB: INSERT oms_order (status=0 待付款)
    S->>DB: INSERT oms_order_item
    S->>DB: 更新优惠券状态 / 扣积分 / 清购物车
    S->>MQ: sendDelayMessage<br/>(TTL = 超时分钟数)
    S-->>U: 订单号

    Note over MQ: TTL 过期未支付
    MQ->>S: CancelOrderReceiver
    S->>DAO: releaseStockBySkuId<br/>(lock_stock -= qty)
    S->>DB: UPDATE order status=4

    Note over U,DB: 或：用户支付成功
    U->>C: POST /order/paySuccess
    C->>S: paySuccess
    S->>DAO: reduceSkuStock<br/>(stock -= qty, lock_stock -= qty)
    S->>DB: UPDATE order status=1
```

**库存三态**（理解了这个就理解了电商的核心难点）：

| 操作 | SQL | 时机 |
|---|---|---|
| `lockStockBySkuId` | `lock_stock += qty` | 下单时（锁定） |
| `reduceSkuStock` | `stock -= qty; lock_stock -= qty` | 支付成功（真扣） |
| `releaseStockBySkuId` | `lock_stock -= qty` | 超时取消（释放） |

可售库存 = `stock - lock_stock`。

**延迟取消机制**（这是 RabbitMQ 在本项目里的核心用法）：
- TTL 队列 `mall.order.cancel.ttl` + 死信交换机 → 主队列 `mall.order.cancel`
- 下单时把订单 ID 投到 TTL 队列，过期未消费就被死信转发到主队列，监听器触发取消逻辑
- 超时时间从 `oms_order_setting.normal_order_overtime` 读取

---

## 6. 配置和中间件

| 中间件 | 用途 | 在代码里的位置 |
|---|---|---|
| MySQL | 主存储 | `application-dev.yml` 数据源 |
| Redis | Token 黑名单 / 订单号生成 / 验证码 / 资源-角色映射缓存 | `RedisService` |
| RabbitMQ | 订单延迟取消 | `RabbitMqConfig` + `CancelOrder*` 组件 |
| Elasticsearch | 商品搜索 | `mall-search` 整个模块 |
| MongoDB | 商品浏览历史 | mall-portal 下 MemberReadHistory |
| MinIO / 阿里云 OSS | 商品图片 / 上传文件 | `MinioController` / `OssController` |

配置文件入口：`application.yml`（基础） + `application-dev.yml`（本地开发）。JWT 密钥 `jwt.secret: mall-admin-secret` 在 yml 里——**生产环境必须移到环境变量**。

---

## 7. 给阅读者的建议读图顺序

1. **先看本文档** + Swagger（http://localhost:8080/swagger-ui/）建立 API 体感
2. **跑通登录**：UmsAdminController#login → ServiceImpl#login → JwtTokenUtil → JwtAuthenticationTokenFilter（这五个文件按顺序读一遍，鉴权就懂了）
3. **跑通下单**：OmsPortalOrderController#generateOrder → ServiceImpl#generateOrder → PortalOrderDao.xml（库存三个 SQL）→ RabbitMqConfig（延迟队列）
4. **其他模块按需查阅**，不必通读

**不建议读**：所有 `mall-mbg` 下的代码（自动生成的、机械化的 CRUD），以及 `*Example.java`（MBG 的查询条件 DSL，写法老旧）。

---

## 8. 2026 视角下的"过时点"提示

读到下列模式时，知道**这不是 2026 该学的写法**：

- `@Autowired` 字段注入 → 应改构造器注入
- Springfox `@ApiOperation`/`@ApiImplicitParam` → springdoc-openapi 的 `@Operation`/`@Parameter`
- MBG 生成的 `XxxExample.createCriteria()` 链式查询 → MyBatis-Plus QueryWrapper 或 JOOQ
- JJWT 0.9.1 老 API（`Jwts.parser().setSigningKey()`） → JJWT 0.12+ 用 `Jwts.parser().verifyWith()`
- `application-dev.yml` 里的明文密钥 → 环境变量 / Vault
- 没有 Testcontainers、没有 CI、`skipTests=true`
- 没有 Micrometer / OpenTelemetry 接入

把"读懂老写法 + 知道现代写法"作为产出，比"模仿老写法"更有价值。
