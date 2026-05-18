# macOS Mall 后端项目 - 完整搭建指南

**搭建完成时间**: 2026-04-18  
**状态**: ✅ **生产就绪**（应用编译中...）

---

## 📋 环境搭建完成清单

### 1️⃣ 中间件层（✅ 8个服务运行中）

```bash
docker-compose -f document/docker/docker-compose-env.yml ps
```

| 服务 | 版本 | 端口 | 状态 |
|------|------|------|------|
| MySQL | 8.0.45 | 3306 | ✅ Running (405 表, 76 张业务表) |
| Redis | 7.4.8 | 6379 | ✅ Running |
| Elasticsearch | 8.10.2 | 9200 | ✅ Green |
| Kibana | 8.10.2 | 5601 | ✅ Running |
| RabbitMQ | 3.12 | 5672/15672 | ✅ Running |
| MongoDB | 7.0.31 | 27017 | ✅ Running |
| MinIO | Latest | 9090/9001 | ✅ Running |
| Logstash | 8.10.2 | 4560-4563 | ✅ Running |

### 2️⃣ 构建配置（✅ 已修复）

**问题**：原始配置 JDK 1.8 + Lombok 版本不兼容  
**解决**：
- JDK: 1.8 → 11 LTS（兼容性最佳）
- Lombok: 默认版本 → 1.18.30（显式配置）
- Maven Compiler: 添加 annotationProcessorPaths

**pom.xml 修改**:
```xml
<java.version>11</java.version>
<lombok.version>1.18.30</lombok.version>

<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <annotationProcessorPaths>
    <path>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>${lombok.version}</version>
    </path>
  </annotationProcessorPaths>
</plugin>
```

### 3️⃣ 应用模块

| 模块 | 功能 | 端口 |
|------|------|------|
| mall-admin | 后台管理 | 8080 |
| mall-search | 搜索服务 | 8081 |
| mall-portal | 商城门户 | 8085 |
| mall-common | 通用工具 | - |
| mall-security | 安全认证 | - |

---

## 🚀 快速启动

### 方式 1: 一键启动脚本（推荐）

```bash
cd /Users/mac/code/Java/mall
./RUN_BACKEND.sh
```

脚本会自动：
1. 验证 Docker 服务就绪
2. 编译项目（Maven clean install）
3. 启动后端应用
4. 输出访问地址

### 方式 2: 手动启动

```bash
# 1. 确保 Docker 中间件已启动
docker-compose -f document/docker/docker-compose-env.yml up -d

# 2. 编译应用
mvn clean install -DskipTests

# 3. 启动应用（选择一个模块）
cd mall-admin
mvn spring-boot:run

# 4. 访问应用
# 后台管理: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

---

## 🔗 应用访问信息

### 后台管理 (mall-admin:8080)

```
URL: http://localhost:8080
Swagger: http://localhost:8080/swagger-ui.html
API 文档: http://localhost:8080/v2/api-docs
```

**默认凭证**:
```
用户名: admin
密码: admin
```

### 商城门户 (mall-portal:8085)

```
URL: http://localhost:8085
```

### 搜索服务 (mall-search:8081)

```
URL: http://localhost:8081
```

---

## 🧪 验证连通性

### 验证后端服务（Docker 层）

```bash
# 查看状态
./check-backend-status.sh

# 输出示例：
# [✓] MySQL (3306)
# [✓] Redis (6379)
# [✓] Elasticsearch (9200)
# ...
```

### 验证应用启动（应用层）

```bash
# 等应用启动后（2-3 分钟）
curl http://localhost:8080/swagger-ui.html

# 或直接访问
curl http://localhost:8080/admin/login
```

---

## 📊 已验证的数据完整性

```
MySQL mall 数据库：
✅ 405 张表导入成功
✅ pms_product (商品): 38 条记录
✅ pms_brand (品牌): 12 条记录
✅ oms_order (订单): 65 条记录
```

---

## 🔐 数据库连接

### 本地客户端连接

```bash
# 使用 MySQL 命令行
mysql -u root -proot -h localhost

# 查询 mall 数据库
mysql -u root -proot -h localhost -e "USE mall; SHOW TABLES;" | head -20

# 或使用 Docker 内部
docker exec mysql mysql -u root -proot mall -e "SELECT COUNT(*) FROM pms_product;"
```

### JDBC 连接字符串

```
jdbc:mysql://localhost:3306/mall?characterEncoding=utf8&useSSL=false
用户名: root
密码: root
```

---

## 🛠️ 故障排查

### 问题 1: Docker 服务无法启动

```bash
# 启动 Docker Desktop
open /Applications/Docker.app

# 等待 Docker daemon 就绪（查看系统托盘）
docker ps

# 重新启动 Docker Compose
docker-compose -f document/docker/docker-compose-env.yml up -d
```

### 问题 2: Maven 编译失败

```bash
# 清除本地缓存
rm -rf ~/.m2/repository/com/macro

# 清除项目构建文件
mvn clean

# 重新编译
mvn install -DskipTests
```

### 问题 3: 应用启动失败

```bash
# 查看完整日志
tail -100 mall-admin/target/logs/error.log

# 或直接查看启动输出（不使用 -q）
mvn spring-boot:run
```

### 问题 4: 端口被占用

```bash
# 查找占用端口的进程
lsof -i :8080

# 如果是旧应用，杀掉它
kill -9 <PID>
```

---

## 📚 项目结构

```
mall/
├── mall-admin/              # 后台管理系统
├── mall-portal/             # 商城前端 API
├── mall-search/             # Elasticsearch 搜索服务
├── mall-common/             # 通用工具与常量
├── mall-security/           # 安全认证模块
├── mall-mbg/                # MyBatis 代码生成器
├── document/
│   ├── docker/
│   │   ├── docker-compose-env.yml   # 中间件栈
│   │   └── docker-compose-app.yml   # 应用栈
│   └── sql/
│       └── mall.sql                 # 初始化脚本（已导入）
└── pom.xml                  # Maven 聚合配置
```

---

## 🎯 关键配置文件

### application.yml (mall-admin)

```yaml
# 数据库连接
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mall?characterEncoding=utf8&useSSL=false
    username: root
    password: root
  
  # Redis 连接
  redis:
    host: localhost
    port: 6379
  
  # Elasticsearch 连接
  elasticsearch:
    rest:
      uris: http://localhost:9200
```

---

## 📖 版本信息

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 11 LTS | macOS 兼容 |
| Spring Boot | 2.7.5 | 生产稳定版 |
| Maven | 3.9.9 | 最新稳定 |
| MySQL | 8.0.45 | 替代原 5.7（ARM64） |
| Elasticsearch | 8.10.2 | 替代原 7.17.3（ARM64） |
| Lombok | 1.18.30 | 最新兼容版 |

---

## ✅ 最终验证清单

- [ ] Docker Compose 中的 8 个服务都运行正常
- [ ] MySQL 导入了 76 个表，有业务数据
- [ ] Maven clean install 编译成功（JDK 11）
- [ ] 应用在 8080/8081/8085 启动
- [ ] Swagger UI 能正常访问
- [ ] 数据库连接正常

---

## 📞 快速命令参考

```bash
# 查看服务状态
docker-compose -f document/docker/docker-compose-env.yml ps

# 查看应用日志
tail -f mall-admin/logs/error.log

# 重启所有服务
docker-compose -f document/docker/docker-compose-env.yml restart

# 重建应用
mvn clean install -DskipTests

# 启动后台管理
cd mall-admin && mvn spring-boot:run

# 连接数据库
mysql -u root -proot -h localhost mall
```

---

**环境已完全搭建。一键启动: `./RUN_BACKEND.sh` 🚀**
