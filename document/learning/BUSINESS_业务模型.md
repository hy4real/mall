# mall 业务模型：SPU/SKU、订单流程、RBAC

> 读完本文你应该能口述：商品怎么创建、订单怎么流转、权限怎么校验。
> 代码来源：mall-admin（后台管理）+ mall-portal（C 端用户）+ mall-security（认证授权）

---

## 一、SPU / SKU 商品模型

### 1.1 核心概念

**SPU（Standard Product Unit）= 标准产品单元**
- 一件商品的抽象概念，比如"iPhone 14"
- 不包含具体的颜色、尺寸等规格信息

**SKU（Stock Keeping Unit）= 库存量单位**
- SPU 下的具体可售卖规格，比如"iPhone 14 128GB 黑色"
- 每个有独立的价格、库存、编码

**实际举例：**

```
SPU: Apple iPhone 14 (A2884)
│
├── SKU 1: 128GB 星光色    ¥5999  库存:100  编码: 202605160037001
├── SKU 2: 128GB 午夜色    ¥5999  库存:50   编码: 202605160037002
├── SKU 3: 256GB 星光色    ¥6899  库存:80   编码: 202605160037003
└── SKU 4: 256GB 午夜色    ¥6899  库存:30   编码: 202605160037004
```

SKU 的 `spData` 字段存储规格 JSON：
```json
[{"key":"颜色","value":"星光色"},{"key":"存储","value":"128GB"}]
```

### 1.2 关键表 ER 关系

```
pms_product (SPU 主表)
├── id (PK)
├── name, brand_id, product_category_id, pic, price
├── publish_status, new_status, recommand_status, verify_status
│
├── pms_sku_stock (SKU 库存表) ─── product_id → pms_product.id
│   ├── id, product_id, sku_code
│   ├── price, stock, lock_stock, sale
│   └── sp_data (JSON: 规格属性)
│
├── pms_product_attribute_value (商品规格参数) ─── product_id → pms_product.id
│   ├── product_attribute_id → pms_product_attribute.id
│   └── value (如 "5.8英寸", "A16芯片")
│
├── pms_member_price (会员价格) ─── product_id → pms_product.id
│   └── member_level_id, member_price
│
├── pms_product_ladder (阶梯价格) ─── product_id → pms_product.id
│   └── count, discount, price (买2件95折, 买5件9折)
│
├── pms_product_full_reduction (满减价格) ─── product_id → pms_product.id
│   └── full_price, reduce_price (满500减50)
│
├── cms_subject_product_relation (关联专题) ─── product_id → pms_product.id
└── cms_prefrence_area_product_relation (关联优选) ─── product_id → pms_product.id
```

### 1.3 商品创建流程（8 步）

**代码入口：** `PmsProductServiceImpl.create(PmsProductParam)` (mall-admin)

```
用户提交商品表单
    │
    ▼
[Step 1] 插入 SPU 主表 pms_product
    │       productMapper.insertSelective(product)
    │
    ▼
[Step 2] 插入会员价格 pms_member_price（如黄金会员享95折）
    │       relateAndInsertList(memberPriceDao, memberPriceList, productId)
    │
    ▼
[Step 3] 插入阶梯价格 pms_product_ladder（买2件95折）
    │       relateAndInsertList(productLadderDao, ladderList, productId)
    │
    ▼
[Step 4] 插入满减价格 pms_product_full_reduction（满500减50）
    │       relateAndInsertList(productFullReductionDao, reductionList, productId)
    │
    ▼
[Step 5] 生成 SKU 编码 + 插入 pms_sku_stock
    │       handleSkuStockCode(): "20260516" + "0037" + "001"
    │       relateAndInsertList(skuStockDao, skuStockList, productId)
    │
    ▼
[Step 6] 插入商品规格参数 pms_product_attribute_value
    │       relateAndInsertList(productAttributeValueDao, attrValueList, productId)
    │
    ▼
[Step 7] 关联专题 cms_subject_product_relation
    │       relateAndInsertList(subjectProductRelationDao, relationList, productId)
    │
    ▼
[Step 8] 关联优选 cms_prefrence_area_product_relation
    │       relateAndInsertList(prefrenceAreaProductRelationDao, relationList, productId)
```

**关键代码 — 反射批量插入：**
```java
// relateAndInsertList 通过反射调用任意 DAO 的 insertList 方法
private void relateAndInsertList(Object dao, List dataList, Long productId) {
    for (Object item : dataList) {
        Method setProductId = item.getClass().getMethod("setProductId", Long.class);
        setProductId.invoke(item, productId);   // 反射调用 setProductId
    }
    Method insertList = dao.getClass().getMethod("insertList", List.class);
    insertList.invoke(dao, dataList);            // 反射调用批量插入
}
```
好处：7 种子资源的插入逻辑一样，不需要写 7 遍。

### 1.4 商品更新 — SKU 的 Diff 算法

**代码入口：** `PmsProductServiceImpl.handleUpdateSkuStockList()` (mall-admin)

```
旧 SKU 列表（数据库中）: [id=1 黑色, id=2 白色, id=3 红色]
新 SKU 列表（用户提交）: [id=null 蓝色(新增), id=1 黑色(修改), id=2 白色(修改)]
                                        id=3 红色不在列表中 → 删除

Diff 结果:
  新增: [蓝色]
  修改: [黑色, 白色]
  删除: [红色]
```

判断逻辑：
- `id == null` → 新增
- `id != null` 且在旧列表中 → 修改
- 在旧列表中但不在新列表中 → 删除

---

## 二、订单流程

### 2.1 订单状态机

```
                    ┌──────────────────────────────────────┐
                    │                                      │
                    ▼                                      │
 ┌─────────┐   用户支付   ┌──────────┐  管理发货  ┌──────────┐
 │ 待付款  │──────────→│ 待发货   │─────────→│ 已发货   │
 │   (0)   │           │   (1)    │          │   (2)    │
 └────┬────┘           └──────────┘          └────┬─────┘
      │                                          │
      │ 用户取消 / 超时取消                         │ 用户确认收货
      │ (释放库存、返还优惠券和积分)                │
      ▼                                          ▼
 ┌──────────┐                              ┌──────────┐
 │ 已关闭   │                              │ 已完成   │
 │   (4)    │                              │   (3)    │
 └──────────┘                              └──────────┘

 特殊状态:
 ┌──────────┐
 │ 无效     │  ← 系统自动标记（如重复订单）
 │   (5)    │
 └──────────┘
```

### 2.2 完整下单链路

**代码入口：** `OmsPortalOrderServiceImpl.generateOrder()` (mall-portal, 795行)

```
购物车页面 → 点击"去结算"
    │
    ▼
[1] generateConfirmOrder() — 订单确认页数据
    │   - 加载购物车商品 + 促销信息
    │   - 加载收货地址列表
    │   - 加载可用优惠券
    │   - 计算总金额、活动优惠、应付金额
    │
    ▼
用户选择地址、优惠券、是否使用积分 → 点击"提交订单"
    │
    ▼
[2] generateOrder() — 创建订单（核心方法）
    │
    ├─[2.1] 校验收货地址
    │       if (addressId == null) Asserts.fail("请选择收货地址！");
    │
    ├─[2.2] 构建订单商品列表 OmsOrderItem
    │       从购物车 CartPromotionItem → OmsOrderItem
    │
    ├─[2.3] 校验库存
    │       hasStock(): 遍历购物车商品，检查 realStock >= quantity
    │
    ├─[2.4] 处理优惠券
    │       三种使用范围：全场通用(0) / 指定分类(1) / 指定商品(2)
    │       按比例分摊到每个商品：couponAmount = (商品价格/总价格) * 面额
    │
    ├─[2.5] 处理积分抵扣
    │       校验：积分够不够？能不能和优惠券共用？超过订单百分比？
    │       按比例分摊到每个商品
    │
    ├─[2.6] 计算实付金额
    │       realAmount = 原价 - 促销优惠 - 优惠券抵扣 - 积分抵扣
    │
    ├─[2.7] 锁定库存（不扣减真实库存！）
    │       lockStock: sku.lockStock += quantity
    │       真实库存在支付成功后才扣减
    │
    ├─[2.8] 构建 OmsOrder 主表记录
    │       order.setStatus(0)          // 待付款
    │       order.setOrderSn(...)       // 生成订单号
    │       order.setPayAmount(...)     // 应付金额
    │
    ├─[2.9] 插入数据库
    │       orderMapper.insert(order)              // 订单主表
    │       orderItemDao.insertList(orderItemList)   // 订单商品明细
    │
    ├─[2.10] 更新优惠券状态为已使用
    │
    ├─[2.11] 扣减用户积分
    │       memberService.updateIntegration(userId, integration - useIntegration)
    │
    ├─[2.12] 删除购物车中已下单的商品
    │
    └─[2.13] 发送 RabbitMQ 延迟消息（超时自动取消）
            cancelOrderSender.sendMessage(orderId, delayTimes)
            默认 60 分钟后触发 cancelTimeOutOrder()
```

### 2.3 订单号生成规则

```
18 位: yyyyMMdd + 平台号(2) + 支付方式(2) + Redis 自增 ID(6+)

示例: 2026051611010000001
         │      │  │  │    │
         │      │  │  │    └── Redis 自增（同一天内唯一）
         │      │  │  └────── 支付方式：0=未支付 1=支付宝 2=微信
         │      │  └───────── 来源：0=PC 1=APP
         │      └──────────── 日期
         └─────────────────── Redis INCR 保证原子性和唯一性
```

### 2.4 支付成功

**代码入口：** `OmsPortalOrderServiceImpl.paySuccess(orderId, payType)` (mall-portal)

```
支付宝回调 → paySuccess(orderId, payType)
    │
    ├─[1] 修改订单状态: status=0 → status=1（待发货）
    │       只修改"未付款"的订单，防止重复支付
    │
    └─[2] 扣减真实库存（锁定库存 → 真实库存）
            reduceSkuStock: stock -= quantity, lockStock -= quantity
```

### 2.5 超时取消订单

**代码入口：** `OmsPortalOrderServiceImpl.cancelTimeOutOrder()` (mall-portal)

```
RabbitMQ 延迟消息（60分钟后触发）
    │
    ▼
[1] 查询超时未支付订单
    │
    ├─[2] 修改订单状态 → 已关闭(4)
    │
    ├─[3] 释放锁定库存（lockStock -= quantity）
    │
    ├─[4] 返还优惠券状态
    │
    └─[5] 返还用户积分
```

### 2.6 金额计算公式

```
单品实付 = 商品原价 - 促销优惠 - 优惠券分摊 - 积分分摊

订单总额 totalAmount = Σ(商品原价 × 数量)
订单运费 freightAmount = 0（当前未实现）
促销优惠 promotionAmount = Σ(促销优惠 × 数量)
优惠券抵扣 couponAmount = Σ(优惠券分摊 × 数量)
积分抵扣 integrationAmount = Σ(积分分摊 × 数量)

─────────────────────────────────
应付金额 payAmount = totalAmount + freightAmount - promotionAmount - couponAmount - integrationAmount
```

---

## 三、RBAC 权限模型

### 3.1 数据模型

```
ums_admin (管理员)
    │
    ├── ums_admin_role_relation (管理员-角色关联)
    │       admin_id, role_id
    │
    └── ums_role (角色)
            │
            ├── ums_role_menu_relation (角色-菜单关联)
            │       role_id, menu_id
            │       └── ums_menu (前端菜单)
            │               name, parent_id, level, sort
            │
            └── ums_role_resource_relation (角色-资源关联)
                    role_id, resource_id
                    └── ums_resource (后端API资源)
                            name, url, description
```

**三级关系：Admin → Role → (Menu + Resource)**
- 一个管理员可以有多个角色
- 一个角色可以有多个菜单（控制前端显示哪些菜单）
- 一个角色可以有多个资源（控制后端可以访问哪些 API）

### 3.2 动态权限校验链路

```
HTTP 请求: GET /admin/product/list
    │
    ▼
[1] JwtAuthenticationTokenFilter
    │   - 读取 Authorization: Bearer xxx
    │   - 解析 JWT，提取 username
    │   - 查数据库加载用户信息和角色权限
    │   - 放入 SecurityContextHolder
    │
    ▼
[2] DynamicSecurityFilter  ← 仅 mall-admin 有，mall-portal 没有
    │   - 从 DynamicSecurityService 获取 URL→权限 映射
    │     如 "/admin/product/**" → "pms:product:read"
    │   - 匹配当前请求 URL，得到所需权限
    │
    ▼
[3] DynamicAccessDecisionManager
    │   - 比对用户拥有的权限 vs 当前 URL 需要的权限
    │   - 有权限 → 放行
    │   - 无权限 → 抛 AccessDeniedException → 返回 403
    │
    ▼
[4] Controller 处理请求
```

### 3.3 admin vs portal 的权限差异

| 维度 | mall-admin | mall-portal |
|------|-----------|------------|
| 用户表 | ums_admin | ums_member |
| JWT 密钥 | mall-admin-secret | mall-portal-secret |
| 登录接口 | POST /admin/login | POST /sso/login |
| 权限模型 | 动态 RBAC（URL→权限→角色→管理员） | 仅区分登录/未登录 |
| DynamicSecurityService | 有（注入到 mall-security） | 没有（`@Autowired(required=false)` 不匹配） |
| @ConditionalOnBean | 检测到 DynamicSecurityService → 启用动态过滤器 | 检测不到 → 跳过 |

**实现原理：**
```java
// mall-security 的 CommonSecurityConfig 中：
@Autowired(required = false)
private DynamicSecurityService dynamicSecurityService;

// 条件装配：只有 dynamicSecurityService bean 存在时，才注册动态权限组件
@ConditionalOnBean(name = "dynamicSecurityService")
@Bean
public DynamicSecurityFilter dynamicSecurityFilter(...) { ... }
```

---

## 四、关键数据流图

### 4.1 商品创建数据流

```
PmsProductController.create()
    │
    ▼
PmsProductServiceImpl.create(PmsProductParam)    ← PmsProductParam 是聚合 DTO，包含 SPU + 所有子资源
    │
    ├→ productMapper.insertSelective()             ← pms_product 表
    ├→ memberPriceDao.insertList()                ← pms_member_price 表
    ├→ productLadderDao.insertList()              ← pms_product_ladder 表
    ├→ productFullReductionDao.insertList()       ← pms_product_full_reduction 表
    ├→ skuStockDao.insertList()                   ← pms_sku_stock 表
    ├→ productAttributeValueDao.insertList()       ← pms_product_attribute_value 表
    ├→ subjectProductRelationDao.insertList()     ← cms_subject_product_relation 表
    └→ prefrenceAreaProductRelationDao.insertList() ← cms_prefrence_area_product_relation 表
```

### 4.2 下单数据流

```
OmsPortalOrderController.generateOrder()
    │
    ▼
OmsPortalOrderServiceImpl.generateOrder(OrderParam)
    │
    ├→ cartItemService.listPromotion()            读 购物车 + 促销信息
    ├→ skuStockMapper.selectByPrimaryKey()         读 SKU 库存
    ├→ portalOrderDao.lockStockBySkuId()          写 SKU 锁定库存
    ├→ orderMapper.insert()                       写 oms_order
    ├→ orderItemDao.insertList()                  写 oms_order_item
    ├→ couponHistoryMapper.updateByPrimaryKeySelective()  写 优惠券状态
    ├→ memberService.updateIntegration()          写 用户积分
    ├→ cartItemService.delete()                   删 购物车商品
    └→ cancelOrderSender.sendMessage()            发 RabbitMQ 延迟消息
```

---

## 五、数据库表关系全景

```
                    ┌──────────────────┐
                    │   pms_brand     │
                    │   品牌表         │
                    └────────┬─────────┘
                             │ brand_id
┌──────────────────┐  ┌───────▼──────────┐  ┌──────────────────────┐
│pms_product_cate- │  │   pms_product    │  │ pms_product_attri-   │
│gory 商品分类     │◄─│   SPU 主表       │─►│bute_category 属性分类│
│  (树形结构)      │  │                  │  └──────────┬───────────┘
└──────────────────┘  └───────┬──────────┘             │
       product_category_id    │                       │
                             ▼                       ▼
                    ┌──────────────────┐  ┌──────────────────────┐
                    │  pms_sku_stock   │  │ pms_product_attri-   │
                    │  SKU 库存表      │  │bute 属性表          │
                    │                  │  └──────────┬───────────┘
                    └──────────────────┘             │
                                                      ▼
                                            ┌──────────────────────┐
                                            │ pms_product_attri-   │
                                            │bute_value 属性值表   │
                                            └──────────────────────┘

┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│   oms_order      │  │  oms_order_item  │  │   ums_member     │
│   订单主表       │─►│  订单商品明细    │  │   C 端用户       │
└──────────────────┘  └──────────────────┘  └────────┬─────────┘
                                                       │
┌──────────────────┐  ┌──────────────────┐  ┌────────▼─────────┐
│ sms_coupon       │  │  ums_admin      │  │ oms_cart_item    │
│ 优惠券           │  │  后台管理员     │  │ 购物车           │
└──────────────────┘  └────────┬─────────┘  └──────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  ums_role       │
                    │  角色           │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │  ums_resource   │
                    │  API 资源       │
                    └──────────────────┘
```
