# macOS 后端环境快速启动指南

> **状态**: ✅ 已完成搭建于 2026-04-18

## 🚀 一键启动

```bash
# 进入项目目录
cd /Users/mac/code/Java/mall

# 启动所有后端服务
docker-compose -f document/docker/docker-compose-env.yml up -d

# 检查服务状态
./check-backend-status.sh
```

## 📋 服务清单

| 服务 | 地址 | 用户 | 密码 | 状态 |
|------|------|------|------|------|
| **MySQL** | localhost:3306 | root | root | ✅ |
| **Redis** | localhost:6379 | - | - | ✅ |
| **Elasticsearch** | http://localhost:9200 | - | - | ✅ |
| **Kibana** | http://localhost:5601 | - | - | ✅ |
| **RabbitMQ** | localhost:5672 | mall | mall | ✅ |
| **RabbitMQ UI** | http://localhost:15672 | guest | guest | ✅ |
| **MongoDB** | localhost:27017 | - | - | ✅ |
| **MinIO** | http://localhost:9001 | minioadmin | minioadmin | ✅ |
| **Nginx** | http://localhost | - | - | ✅ |

## ✅ 已验证
- ✓ MySQL 8.0.45（76 个表已导入）
- ✓ Redis 7.0
- ✓ Elasticsearch 8.10.2（集群状态: green）
- ✓ Kibana 8.10.2
- ✓ RabbitMQ 3.12
- ✓ MongoDB 7.0.31
- ✓ MinIO（最新版）
- ✓ Logstash 8.10.2

## 🔍 关键改动

### Windows → macOS 适配
1. **MySQL**: 5.7 → 8.0（ARM64 兼容）
2. **所有镜像**: 更新到 ARM64 兼容版本
3. **Volume 挂载**: `/mydata/*` → Docker 管理 volumes（解决文件共享问题）
4. **Elasticsearch**: 禁用安全认证（开发环境）

### 项目文件修改
- ✅ `document/docker/docker-compose-env.yml` - 已更新
- ✅ 生成: `MACOS_SETUP_GUIDE.md` - 详细指南
- ✅ 生成: `setup-macos-backend.sh` - 自动化脚本
- ✅ 生成: `check-backend-status.sh` - 状态检查

## 🎯 下一步

### 1. 验证数据库连接
```bash
# 从 macOS 本地连接
mysql -u root -proot -h localhost -e "USE mall; SHOW TABLES;" | head -20

# 或使用 Docker 内部
docker exec mysql mysql -u root -proot mall -e "SHOW TABLES;"
```

### 2. 启动后端应用
```bash
cd /Users/mac/code/Java/mall
mvn clean install
mvn spring-boot:run
```

### 3. 验证应用
```bash
curl http://localhost:8080/swagger-ui.html
```

## 🆘 常见问题

### 问题: Docker 容器无法启动
```bash
# 解决: 确保 Docker Desktop 运行
open /Applications/Docker.app

# 等待 Docker daemon 就绪后重试
docker-compose -f document/docker/docker-compose-env.yml up -d
```

### 问题: 端口被占用
```bash
# 查看占用端口的进程
lsof -i :3306

# 停止旧的 MySQL 5.7 Homebrew 版本
brew services stop mysql@5.7
```

### 问题: 导入 SQL 失败
```bash
# 手动创建数据库并导入
docker exec mysql mysql -u root -proot -e "CREATE DATABASE IF NOT EXISTS mall CHARACTER SET utf8mb4;"
docker exec -i mysql mysql -u root -proot mall < document/sql/mall.sql
```

## 📊 环境信息

- **OS**: macOS 15.2 (Sequoia) / Apple Silicon
- **Docker**: 29.3.1+
- **JDK**: OpenJDK 25.0.2
- **MySQL**: 8.0.45
- **Elasticsearch**: 8.10.2
- **搭建日期**: 2026-04-18

## 📚 详细文档

- 完整指南: [MACOS_SETUP_GUIDE.md](./MACOS_SETUP_GUIDE.md)
- 自动化脚本: [setup-macos-backend.sh](./setup-macos-backend.sh)
- 状态检查: [check-backend-status.sh](./check-backend-status.sh)

---

**一切就绪！🎉 开发环境已完全搭建并验证通过。**
