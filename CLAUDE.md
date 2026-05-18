# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在本仓库工作时提供指导。

## 🚀 快速开始

### 编译和运行

```bash
# 必须使用 JDK 21 构建（本机 JDK 25 不兼容 Lombok）
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn clean install

# 启动 mall-admin 模块（推荐大部分开发工作）
cd mall-admin
mvn spring-boot:run

# 运行测试（当前跳过，见 pom.xml 中 skipTests=true）
mvn clean test
```

### 启动后端服务

需要先启动 Docker Compose 中间件栈（本地开发必需）：

```bash
# 启动所有服务：MySQL、Redis、Elasticsearch、RabbitMQ、MongoDB、MinIO 等
docker-compose -f document/docker/docker-compose-env.yml up -d

# 检查服务状态
./check-backend-status.sh
```

**默认凭证**：
- **MySQL**: `root:root` @ localhost:3306（数据库：`mall`）
- **Redis**: localhost:6379（无认证）
- **Elasticsearch**: http://localhost:9200
- **RabbitMQ**: `guest:guest` 或 `mall:mall` @ localhost:5672（管理界面：15672）
- **MongoDB**: localhost:27017（无认证）
- **MinIO**: `minioadmin:minioadmin` @ http://localhost:9001

### API 访问

- **Swagger 文档**: http://localhost:8080/swagger-ui.html
- **后台登录**: POST `/admin/login`，请求体：`{ "username": "admin", "password": "admin" }`
- **JWT Token**: `Authorization: Bearer {token}` 请求头中，7 天后过期

---

## 📦 项目结构

### 多模块 Maven 项目

```
mall（pom.xml：Spring Boot 2.7.5 为父项目）
├── mall-common        ← 共用工具类、通用配置、日志设置
├── mall-mbg           ← MyBatis Generator 生成的模型、mapper、DAO
├── mall-security      ← Spring Security 封装、JWT 处理、权限管理
├── mall-admin         ← 后台管理系统 API（主要工作模块）
├── mall-search        ← Elasticsearch 商品搜索模块
├── mall-portal        ← 前台商城 API（面向消费者）
└── mall-demo          ← 框架演示和测试代码
```

### 领域驱动的包结构

按业务领域命名，代码组织遵循 controller → service → dao + dto 分层：

| 前缀 | 含义 | 模块举例 |
|------|------|---------|
| **Pms** | 商品管理 | PmsProductController, PmsProductService |
| **Oms** | 订单管理 | OmsOrderController, OmsOrderService |
| **Sms** | 促销管理 | SmsCouponController, SmsCouponService |
| **Cms** | 内容管理 | CmsSubjectController, CmsSubjectService |
| **Ums** | 用户管理 | UmsAdminController, UmsAdminService |

### 关键目录

| 路径 | 说明 |
|------|------|
| `mall-admin/src/main/java/com/macro/mall/` | Controller、Service、DAO、DTO、Config |
| `mall-common/src/main/java/com/macro/mall/common/` | 共用工具和配置 |
| `document/docker/docker-compose-env.yml` | 中间件栈定义 |
| `document/sql/mall.sql` | 数据库初始化脚本（76 张表） |
| `document/reference/` | 功能说明和开发流程文档 |

---

## 🏗️ 架构和设计模式

### 分层架构：Controller → Service → DAO

- **Controller** (`com.macro.mall.controller.*`)：REST 端点、参数验证、Swagger 文档
- **Service** (`com.macro.mall.service.*`)：业务逻辑、事务边界
- **DAO** (`com.macro.mall.dao.*`)：自定义 SQL 查询；基础 CRUD 由 MyBatis Generator 生成
- **DTO** (`com.macro.mall.dto.*`)：API 请求/响应的数据结构

### 数据访问

- **MyBatis**：XML 格式的 SQL 映射 + 自定义 DAO 接口
- **生成代码**：`mall-mbg` 包含自动生成的 POJO 和基础 CRUD
- **自定义查询**：在 `mall-admin/src/main/resources/dao/*.xml` 和 `**/mapper/*.xml` 中编写
- **连接池**：Druid（application.yml 中配置）
- **分页**：PageHelper 插件自动处理 LIMIT/OFFSET

### 配置管理

- **Profile**: `application.yml`（基础） + `application-dev.yml` / `application-prod.yml`（环境特定）
- **JWT**: 密钥在 `application.yml` 中（`jwt.secret: mall-admin-secret`），有效期 7 天
- **Redis**: 数据库键为 `mall`，以命名空间分组（`ums:admin`、`ums:resourceList` 等）
- **Swagger**: 在 `MallSecurityConfig` 中配置端点白名单
- **CORS**: 在 `GlobalCorsConfig` 中启用
- **文件上传**: 最大 10MB，可存储到 MinIO 或阿里云 OSS

### 安全认证

- **Spring Security**: 登录 → JWT Token 生成 → 无状态 API 调用
- **端点白名单**: `/swagger-ui/**`、`/actuator/**`、`/admin/login`、`/admin/register` 等
- **权限管理**: 自定义 `AdminUserDetails` 实现基于角色的访问控制

---

## 💻 常见开发任务

### 新增 API 端点

1. 在对应 `XxxController` 中添加方法，用 `@PostMapping` / `@GetMapping` 标注
2. 在 `dto/` 文件夹中新建请求/响应 DTO 类
3. 调用对应 `XxxService` 的方法实现业务逻辑
4. Service 调用 DAO（复用已有 `XxxDao` 或在 mapper XML 中新增自定义查询）
5. 为 DAO 方法添加 Swagger 文档注解（`@ApiOperation`、`@ApiImplicitParam` 等）
6. 使用 `CommonResult` 包装器返回结果：`CommonResult.success(data)` 或 `CommonResult.failed(message)`

**示例**：
```java
// Controller
@PostMapping("/create")
@ApiOperation("创建新商品")
public CommonResult<Void> createProduct(@RequestBody PmsProductParam param) {
    productService.create(param);
    return CommonResult.success(null);
}

// Service（事务管理）
@Service
public class PmsProductService {
    private final PmsProductDao productDao;
    
    @Transactional
    public void create(PmsProductParam param) {
        // 业务逻辑处理
        productDao.insert(/* ... */);
    }
}

// DAO：使用 MyBatis 生成的 insert() 或在 mapper XML 中定义的自定义方法
```

### 修改数据库表结构

1. 更新 `document/sql/mall.sql`（如果新增表）
2. 在 `mall-mbg` 目录执行 `mvn mybatis-generator:generate` 重新生成模型和 mapper
3. 若要自定义，在 `mall-admin/src/main/resources/dao/` 创建新的 DAO 接口和 mapper XML
4. 使用 `@Select`、`@Update` 注解或 XML 格式定义复杂查询

### 运行测试

```bash
mvn clean test                          # 运行所有测试
mvn test -Dtest=XxxServiceTest          # 运行指定测试类
mvn test -Dtest=XxxServiceTest#method   # 运行指定测试方法
```

**注意**: 根 pom.xml 中设置了 `skipTests=true`；若要启用默认测试，需删除此配置。

### 本地调试

- 在 IDE 中设置断点
- 运行 `mvn spring-boot:run`（或 IDE 调试模式）
- 若使用远程调试，在 `localhost:5005` 连接 IDE 调试器

### 代码生成

```bash
# 根据数据库表重新生成 MyBatis 模型和 mapper
cd mall-mbg
mvn mybatis-generator:generate

# 配置文件：generatorConfig.xml（参考：mall-mbg/src/main/resources/generator.properties）
```

---

## 📚 核心依赖和版本

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.4.2 | Web 框架（已从 2.7.5 升级） |
| Spring Security | 6.x (managed by SB) | 认证和授权 |
| MyBatis | 3.5.16 | ORM 框架 |
| MyBatis Spring Boot | 3.0.4 | MyBatis 自动配置（已从 2.x 升级） |
| MyBatis Generator | 1.4.2 | 代码生成 |
| Druid | 1.2.23 | 数据库连接池 |
| Hutool | 5.8.9 | Java 工具库 |
| Lombok | 1.18.36 | 代码生成（getter/setter） |
| springdoc-openapi | 2.8.4 | API 文档生成（已从 Springfox 迁移） |
| JJWT | 0.12.6 | JWT Token 处理（已从 0.9.1 升级） |
| PageHelper | 6.1.0 | MyBatis 分页插件 |
| Elasticsearch | 8.15.5 (via ES Java Client) | 搜索引擎（mall-search） |
| RabbitMQ | 3.10.5 | 消息队列 |
| Redis | 7.0 | 缓存存储 |
| MySQL Connector | 8.0.29 | 数据库驱动 |
| Aliyun OSS SDK | 2.5.0 | 阿里云对象存储（可选） |
| MinIO | 8.4.5 | MinIO 对象存储（可选） |

---

## 🧪 测试策略

### 当前测试覆盖

项目中测试覆盖有限（pom.xml 中设置了 `skipTests=true`）：

- `mall-demo/src/test/` — 框架演示测试
- `mall-portal/src/test/` — 门户模块集成测试
- `mall-search/src/test/` — 搜索模块测试

### 新增测试

使用 **JUnit 5**、**Mockito**、**AssertJ**（遵循 Java 编码规范）：

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductDao productDao;
    private ProductService service;
    
    @BeforeEach
    void setUp() { service = new ProductService(productDao); }
    
    @Test
    @DisplayName("createProduct 保存并返回 ID")
    void createProduct_valid_savesCalled() {
        var param = new PmsProductParam();
        // ... 测试逻辑
    }
}
```

集成测试使用 **Testcontainers** 启动真实 MySQL 容器。

---

## ⚠️ 重要注意事项

### 数据库表结构

- `document/sql/mall.sql` 定义了 76 张表（商品、订单、用户、内容、促销等）
- 在 `application-dev.yml` 中配置连接池大小、超时等参数
- 重新导入：`docker exec mysql mysql -u root -proot mall < document/sql/mall.sql`

### API 响应格式

所有端点返回统一的包装响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { }
}
```

包装类：`com.macro.mall.common.api.CommonResult<T>`

### JWT Token 流程

1. POST `/admin/login` → 返回 Token
2. 后续请求在 `Authorization: Bearer {token}` 请求头中携带
3. Spring Security 过滤器校验 Token；续期逻辑在 `UmsAdminController` 中
4. Token 有效期 604800 秒（7 天）；支持刷新端点

### Swagger 配置

- 访问地址：http://localhost:8080/swagger-ui.html
- 端点白名单：`/swagger-ui/**`、`/**/v2/api-docs`、`/swagger-resources/**`
- 所有 Controller 通过注解自动生成文档（`@ApiOperation`、`@ApiImplicitParam` 等）

### macOS Docker Compose 特殊配置

- 所有镜像使用 ARM64 兼容版本
- Volume 挂载由 Docker 管理（非 Windows 的 `/mydata/` 路径）
- Elasticsearch 禁用安全认证（开发环境）
- Nginx 处理静态资源；Spring Boot 处理 API 路由

---

## 📖 参考资源

- **教程**: https://www.macrozheng.com（中文详细教程）
- **GitHub 仓库**: https://github.com/macrozheng/mall
- **前端项目**:
  - 后台管理系统：https://github.com/macrozheng/mall-admin-web
  - 前台商城系统：https://github.com/macrozheng/mall-app-web
- **本地文档**:
  - macOS 快速启动：`QUICK_START.md`
  - macOS 完整指南：`MACOS_SETUP_GUIDE.md`
  - Windows 部署：`document/reference/deploy-windows.md`
  - Docker 部署：`document/reference/docker.md`
  - 功能进度：`document/reference/dev_flow.md`

---

## 🎯 代码规范

遵循 Java 编码规范（见 `~/.claude/rules/java/`）：

- **格式**: google-java-format 或 Checkstyle（Google 或 Sun 风格）
- **不可变性**: 字段标记为 `final`；从 public 方法返回防御性复制
- **命名**: 类用 PascalCase，方法/字段用 camelCase，常量用 SCREAMING_SNAKE_CASE
- **Optional**: 使用 `Optional<T>` 返回可能为空的值；不作为字段或参数类型
- **Stream**: 管道不超过 3-4 个操作；复杂逻辑改用循环
- **异常**: 创建继承 `RuntimeException` 的域特定异常
- **注入**: 使用构造函数注入，不用字段注入
- **DTO 映射**: 在 service/controller 边界处理，使用静态 `from()` 方法

相关技能：
- `java-coding-standards` — 完整规范和示例
- `springboot-patterns` — Spring Boot 架构模式
- `jpa-patterns` — JPA/Hibernate 实体设计
- `springboot-tdd` — Spring Boot 测试驱动开发
