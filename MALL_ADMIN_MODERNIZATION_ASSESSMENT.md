# mall-admin 现代化改造评估报告

> 评估日期：2026-05-16
> 目标：Spring Boot 3.4 + JDK 21 + springdoc-openapi + record，与 mall-search-modern 对齐

---

## 1. 文件规模

mall-admin/src/main/java/ 下共 **153 个 Java 文件**，按包分布：

| 包 | 文件数 | 说明 |
|---|---|---|
| controller/ | 31 | REST 端点，含全部 Swagger 注解 |
| service/ | 62 | 接口 + impl 各 31 个 |
| dto/ | 29 | 请求/响应数据结构 |
| dao/ | 22 | 自定义 MyBatis DAO 接口 |
| config/ | 5 | SwaggerConfig, MallSecurityConfig, GlobalCorsConfig, MyBatisConfig, OssConfig |
| validator/ | 2 | 自定义 Bean Validation |
| bo/ | 1 | AdminUserDetails |
| 根包 | 1 | MallAdminApplication.java |

另外还有 **22 个 MyBatis XML mapper** 在 `resources/dao/` 下。

**对比**：mall-search-modern 改造前约 20 个 Java 文件。mall-admin 是其 **7.6 倍**。

---

## 2. 模块依赖分析

### 直接依赖链

```
mall-admin
  ├── mall-mbg (230 个 Java 文件, 生成代码)
  │     └── mall-common (15 个 Java 文件)
  │           └── springfox-boot-starter 3.0.0
  ├── mall-security (15 个 Java 文件)
  │     └── mall-common
  │           └── spring-boot-starter-security
  │           └── spring-boot-starter-data-redis
  │           └── jjwt 0.9.1
  ├── aliyun-sdk-oss
  └── minio
```

### mall-admin 对 mall-common 的实际使用（8 个类，34 个文件引用）

| mall-common 类 | 引用次数 | 用途 |
|---|---|---|
| `CommonResult` | 31 (全部 controller) | API 统一响应包装 |
| `CommonPage` | 16 (部分 controller) | 分页响应包装 |
| `BaseSwaggerConfig` | 1 (SwaggerConfig) | Swagger 基础配置 |
| `SwaggerProperties` | 1 (SwaggerConfig) | Swagger 属性 |
| `Asserts` | 1 (UmsAdminServiceImpl) | 断言工具 |
| `RedisService` | 1 (UmsAdminCacheServiceImpl) | Redis 操作封装 |
| `RequestUtil` | 1 (UmsAdminServiceImpl) | HTTP 请求工具 |

### mall-admin 对 mall-mbg 的实际使用

- **38 个 Mapper 接口**（基础 CRUD，MyBatis Generator 生成）
- **70+ 个 Model 类**（POJO + Example 类，MBG 生成）
- Mapper XML 在 mall-mbg 内部，mall-admin 通过 DAO 层（22 个自定义 XML）补充复杂查询

### mall-admin 对 mall-security 的实际使用（4 个类，3 个文件引用）

| mall-security 类 | 引用文件 | 用途 |
|---|---|---|
| `JwtTokenUtil` | UmsAdminServiceImpl | Token 生成/验证 |
| `DynamicSecurityService` | MallSecurityConfig, UmsResourceController | 动态权限加载 |
| `DynamicSecurityMetadataSource` | UmsResourceController | 权限缓存刷新 |
| `SpringUtil` | UmsAdminServiceImpl | 获取 Bean |

### 跨模块影响

mall-portal 也依赖 mall-mbg、mall-security（间接依赖 mall-common）。mall-search 依赖 mall-mbg。**升级 mall-common/mall-security/mall-mbg 会影响所有下游模块。**

---

## 3. 鉴权链路分析

### mall-security 模块（15 个文件）

| 文件 | 作用 | javax 依赖 |
|---|---|---|
| `JwtAuthenticationTokenFilter` | JWT 过滤器 | javax.servlet.* (4 处) |
| `DynamicSecurityFilter` | 动态权限过滤器 | javax.servlet.* (2 处) |
| `DynamicSecurityMetadataSource` | 权限元数据 | javax.annotation.PostConstruct (1 处) |
| `RestAuthenticationEntryPoint` | 未认证处理 | javax.servlet.* (3 处) |
| `RestfulAccessDeniedHandler` | 无权限处理 | javax.servlet.* (3 处) |
| `DynamicAccessDecisionManager` | 权限决策 | 无 |
| `SecurityConfig` | SecurityFilterChain 配置 | 无 |
| `CommonSecurityConfig` | 通用配置 | 无 |
| `IgnoreUrlsConfig` | 白名单配置 | 无 |
| `JwtTokenUtil` | JWT 工具类 | 无 |
| `RedisCacheAspect` | Redis 缓存切面 | 无 |
| `RedisConfig` | Redis 序列化配置 | 无 |
| `SpringUtil` | Spring 上下文工具 | 无 |
| `CacheException` | 异常类 | 无 |
| `DynamicSecurityService` | 动态权限接口 | 无 |

**javax → jakarta 迁移范围**：
- mall-security：**13 处**（5 个文件）
- mall-admin：**18 处**（13 个文件），主要是 `javax.validation` 和 `javax.servlet`
- mall-common：**2 处**（2 个文件），`javax.servlet.http.HttpServletRequest`

**总计**：约 **33 处** javax import 需改为 jakarta。

### 鉴权复杂度评估

鉴权链路本身不复杂，核心就是：
1. `JwtAuthenticationTokenFilter` 解析 Bearer Token
2. `DynamicSecurityFilter` 做基于 URL 的动态权限校验
3. JWT 使用 jjwt 0.9.1（非常老的版本，Spring Boot 3 下需升级到 jjwt 0.12.x）

但它是**被 mall-portal 共用的**，不能简单复制到 mall-admin-modern 里而不影响 mall-portal。

---

## 4. Swagger 依赖统计

### 注解分布

| 注解类型 | 文件数 | 出现次数 | 说明 |
|---|---|---|---|
| `@Api` (v2) | 31 | 31 | Controller 类标签 |
| `@ApiOperation` (v2) | 31 | ~200 | 方法描述 |
| `@ApiImplicitParam` (v2) | 1 | 2 | 参数描述 |
| `@ApiModel` (v2) | 28 | 28 | DTO 类标签 |
| `@ApiModelProperty` (v2) | 28 | ~48 | DTO 字段描述 |
| `@Tag` (v3) | 31 | 31 | 已有 v3 标签（与 @Api 并存） |

**受影响文件**：**59 个**，共 **~340 处**注解需要迁移。

**迁移映射表**：

| Springfox (v2) | springdoc-openapi (v3) |
|---|---|
| `@Api(tags="...")` | `@Tag(name="...")` (已有 31 个) |
| `@ApiOperation("...")` | `@Operation(summary="...")` |
| `@ApiImplicitParam` | `@Parameter` |
| `@ApiModel` | `@Schema` |
| `@ApiModelProperty` | `@Schema` |

**注意**：31 个 Controller 已经同时有 `@Api` 和 `@Tag`，说明之前做过半迁移。只需删 `@Api` 保留 `@Tag`，再加 `@Operation`。

### SwaggerConfig 依赖

当前 `SwaggerConfig extends BaseSwaggerConfig`，依赖 mall-common 的 `BaseSwaggerConfig` 和 `SwaggerProperties`。迁移 springdoc 后，整个 SwaggerConfig 需要重写（springdoc 自动扫描，配置方式完全不同）。

---

## 5. 改造方案评估

### 方案 A：完全独立（像 mall-search-modern 一样）

**做法**：创建 mall-admin-modern，复制代码，去掉 mall-common/mall-security/mall-mbg 依赖。

**不可行，原因**：

1. **mall-mbg 有 230 个生成文件 + 70+ 个 Model**，mall-admin 的 22 个 DAO XML 和所有 service 都直接引用这些 Model/Mapper。复制过来就失去了 MyBatis Generator 的重新生成能力，且维护两份生成代码是噩梦。

2. **mall-security 被 mall-portal 共用**。如果把鉴权代码复制到 mall-admin-modern，后续 mall-portal 也要改造时就得再复制一遍，导致三份鉴权代码。

3. **CommonResult / CommonPage 被 31 个 Controller 使用**。虽然可以内联，但 mall-portal 也在用，语义上应该统一。

**结论**：完全独立方案只适合无数据库、无鉴权、无共享依赖的模块（如 mall-search-modern 对 ES 的独立封装）。mall-admin 不适合。

### 方案 B：最小改动 — 原地升级多模块结构（推荐）

**做法**：保持多模块结构不变，按层升级依赖版本。

#### 需要改动的内容

| 改动项 | 文件数 | 工作量 | 风险 |
|---|---|---|---|
| 根 pom.xml：Spring Boot 2.7.5 → 3.4.x | 1 | 小 | 低（但影响全部模块） |
| 根 pom.xml：JDK 11 → 21 | 1 | 小 | 低 |
| javax → jakarta 全量替换 | ~33 处 / ~20 文件 | 小 | 低（机械替换） |
| jjwt 0.9.1 → 0.12.x | 1 pom + JwtTokenUtil | 中 | 中（API 变化） |
| springfox → springdoc-openapi | 59 文件 / ~340 注解 + SwaggerConfig 重写 | 大 | 中 |
| SwaggerConfig 重写 | 1 文件 | 小 | 低 |
| mall-common 去 springfox 依赖，加 springdoc | 1 pom | 小 | 低 |
| BaseSwaggerConfig / SwaggerProperties 删除 | 2 文件 | 小 | 低 |
| DTO 转 record（可选，渐进式） | 29 文件 | 中 | 低 |
| mysql-connector-java → com.mysql:mysql-connector-j | 1 pom | 小 | 低 |
| Druid 兼容性验证 | 1 pom | 小 | 中（需确认版本） |
| PageHelper 兼容性验证 | 1 pom | 小 | 中（需确认版本） |

#### 风险点

1. **Druid**：当前 1.2.14，需确认是否支持 Spring Boot 3.4。若不支持，可换 HikariCP（Spring Boot 默认）。
2. **PageHelper**：当前 1.4.5 starter，需升级到支持 Spring Boot 3 的版本（pagehelper-spring-boot-starter 2.x）。
3. **jjwt**：0.9.1 → 0.12.x API 变化较大（Jjwt → Jwt），但 JwtTokenUtil 只是一个文件，工作量可控。
4. **Hutool**：当前 5.8.9，Spring Boot 3 + JDK 21 下应升级到最新 5.8.x。
5. **aliyun-sdk-oss 2.5.0**：较老，可能包含 javax 传递依赖，需排查。
6. **mall-portal 兼容性**：升级 mall-common/mall-security 后，mall-portal 必须同步测试。

#### 预估工作量

| 阶段 | 预估时间 | 说明 |
|---|---|---|
| pom 依赖版本升级 + 编译修复 | 2-3h | javax→jakarta, 依赖版本对齐 |
| Swagger 迁移（springfox → springdoc） | 3-4h | 59 文件注解替换 + Config 重写 |
| jjwt 升级 | 1h | JwtTokenUtil 改写 |
| 验证 + 调试 | 2-3h | 全模块编译、启动、接口测试 |
| **总计** | **8-11h** | |

#### 风险等级：**中**

主要风险不在单个改动项（每项都不复杂），而在于**影响面广**：根 pom 升级后所有模块都会受影响。建议：

1. 新建分支 `modernize/spring-boot-3`
2. 先改根 pom + mall-common（最底层）
3. 再改 mall-security、mall-mbg
4. 最后改 mall-admin
5. 每层改完跑 `mvn compile` 确认

### 方案 C：混合方案 — 升级公共层 + mall-admin DTO 现代化

在方案 B 的基础上，对 mall-admin 做额外的代码现代化：
- 29 个 DTO 渐进式转为 record
- service 层构造器注入替代 @Autowired
- 使用 Jakarta Validation 替代 javax.validation

额外增加 3-4h 工作量，风险不变。

---

## 总结

| 维度 | 评估 |
|---|---|
| 完全独立可行性 | **不可行**（mall-mbg 230 文件 + mall-security 共享） |
| 推荐方案 | **方案 B：原地升级多模块结构** |
| 预估工作量 | 8-11 小时 |
| 风险等级 | **中**（改动项简单，但影响面覆盖全部模块） |
| 核心改动 | javax→jakarta (33处) + springfox→springdoc (59文件/340处) + jjwt升级 (1文件) |
| 最大风险点 | Druid/PageHelper/Hutool 版本兼容性、mall-portal 同步验证 |
| 与 mall-search-modern 的关系 | mall-search-modern 保持独立不变；公共模块升级不影响它 |
