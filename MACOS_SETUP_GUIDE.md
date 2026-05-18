# macOS 后端环境搭建指南

## 📋 概述

这份指南将帮助你在 macOS（Apple Silicon 或 Intel）上搭建完整的 Mall 项目后端开发环境。

**搭建时间**: 约 10-15 分钟（取决于网速和磁盘性能）

---

## ✅ 已完成的环境配置

### 1. JDK 版本升级
- **之前**: OpenJDK 25.0.2 ✓ (已是最新)
- **决策**: 保持现有版本，完全兼容 Elasticsearch 8.x

### 2. MySQL 版本升级
- **之前**: MySQL 5.7 ❌ (不支持 ARM64)
- **现在**: MySQL 8.0.42 ✓ (ARM64 兼容)
- **命令**: `brew install mysql@8.0 && brew link mysql@8.0`
- **PATH 配置**: 已添加到 `~/.zshrc`

### 3. 其他工具安装状态
| 工具 | 版本 | 状态 |
|-----|------|------|
| Redis | 8.0.1 | ✓ 已安装 (Homebrew) |
| MongoDB | 8.2.7 | ✓ 已安装 (Homebrew) |
| RabbitMQ | 4.1.0 | ✓ 已安装 (Homebrew) |
| LogStash | 最新 | ⏳ 下载中 (Homebrew) |
| MinIO | 最新 | ⏳ 下载中 (Homebrew) |

### 4. Docker 中间件栈（推荐方案）
Docker Compose 配置已升级，所有镜像均支持 ARM64：

| 服务 | 镜像版本 | 端口 | 用途 |
|-----|---------|------|------|
| MySQL | 8.0 | 3306 | 主数据库 |
| Redis | 7 | 6379 | 缓存 |
| Elasticsearch | 8.10.2 | 9200 | 搜索引擎 |
| Kibana | 8.10.2 | 5601 | ES 可视化 |
| Logstash | 8.10.2 | 4560-4563 | 日志处理 |
| RabbitMQ | 3.12 | 5672/15672 | 消息队列 |
| MongoDB | 7.0 | 27017 | 文档数据库 |
| MinIO | Latest | 9090/9001 | 对象存储 |
| Nginx | 1.22 | 80 | 反向代理 |

---

## 🚀 快速启动

### 方法 1：使用启动脚本（推荐）

```bash
# 执行完整启动脚本（包括数据库初始化）
/Users/mac/code/Java/mall/setup-macos-backend.sh

# 查看实时日志
tail -f ~/.mall-setup.log
```

**脚本会自动完成以下操作**：
1. 启动所有 Docker 容器
2. 等待关键服务就绪
3. 导入 `mall.sql` 数据库
4. 验证所有服务可用性
5. 输出完整的服务清单

### 方法 2：手动启动

```bash
# 进入 Docker 目录
cd /Users/mac/code/Java/mall/document/docker

# 启动所有中间件
docker-compose -f docker-compose-env.yml up -d

# 查看运行状态
docker-compose -f docker-compose-env.yml ps

# 导入数据库
docker exec -i mysql mysql -u root -proot < /Users/mac/code/Java/mall/document/sql/mall.sql

# 查看日志
docker-compose -f docker-compose-env.yml logs -f
```

---

## 🔗 服务连接信息

### MySQL
```
主机: localhost
端口: 3306
用户: root
密码: root
数据库: mall
```

**验证连接**：
```bash
mysql -u root -proot -h localhost -e "USE mall; SHOW TABLES;" | head -20
```

### Redis
```
主机: localhost
端口: 6379
```

**验证连接**：
```bash
redis-cli PING
# 应返回: PONG
```

### Elasticsearch
```
地址: http://localhost:9200
```

**验证连接**：
```bash
curl http://localhost:9200/_cluster/health
```

### Kibana
```
地址: http://localhost:5601
```

**验证连接**：在浏览器中访问 `http://localhost:5601`

### RabbitMQ
```
AMQP: localhost:5672
Web UI: http://localhost:15672
用户名: mall
密码: mall
虚拟主机: /mall

或使用默认凭证:
用户名: guest
密码: guest
```

**验证连接**：
```bash
curl -u guest:guest http://localhost:15672/api/overview
```

### MongoDB
```
主机: localhost
端口: 27017
```

**验证连接**：
```bash
docker exec mongo mongosh --eval "db.adminCommand('ping')"
```

### MinIO
```
地址: http://localhost:9001
用户名: minioadmin
密码: minioadmin
```

---

## ✔️ 完整验证清单

### 1. 检查所有容器运行状态
```bash
docker-compose -f /Users/mac/code/Java/mall/document/docker/docker-compose-env.yml ps
```

**预期输出**：所有容器 STATUS 应为 `Up`

### 2. 验证数据库导入
```bash
mysql -u root -proot -h localhost -e "USE mall; SELECT COUNT(*) as 表数量 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mall';"
```

**预期输出**：表数量应 > 10

### 3. 验证 Elasticsearch 健康状态
```bash
curl http://localhost:9200/_cluster/health | jq .status
```

**预期输出**：`"green"` 或 `"yellow"`（不应是 red）

### 4. 验证 Kibana 就绪
```bash
curl http://localhost:5601/api/status | jq .state
```

**预期输出**：`"green"`

### 5. 验证 RabbitMQ 用户
```bash
curl -u mall:mall http://localhost:15672/api/users/mall | jq .name
```

**预期输出**：`"mall"`

### 6. 验证 MongoDB 连接
```bash
docker exec mongo mongosh --eval "db.version()"
```

**预期输出**：MongoDB 版本号

---

## 🔧 常见问题与解决

### 问题 1: Docker 无法启动镜像

**症状**: `no matching manifest for linux/arm64`

**原因**: 旧镜像版本不支持 Apple Silicon

**解决**:
```bash
# docker-compose-env.yml 已更新到 ARM64 兼容版本
# 如果仍有问题，删除旧镜像后重试
docker system prune -a
docker-compose -f docker-compose-env.yml up -d
```

### 问题 2: MySQL 密码验证失败

**症状**: `mysql: [Warning] Using a password on the command line interface can be insecure`

**解决**:
```bash
# 确保使用正确的凭证
mysql -u root -proot -h localhost

# 或使用 Docker 内部连接
docker exec -it mysql mysql -u root -proot
```

### 问题 3: Elasticsearch 启动缓慢

**原因**: 首次启动需要初始化索引和安全配置

**解决**: 等待 2-3 分钟，监控日志
```bash
docker logs elasticsearch -f
```

### 问题 4: 端口被占用

**症状**: `Bind for 0.0.0.0:3306 failed`

**解决**:
```bash
# 查看占用端口的进程
lsof -i :3306

# 如果是旧的 MySQL 5.7，停止它
brew services stop mysql@5.7

# 然后重新启动 Docker Compose
docker-compose -f docker-compose-env.yml up -d
```

---

## 📊 环境验证脚本

保存以下脚本为 `verify-backend.sh`，一键检查所有服务：

```bash
#!/bin/bash

echo "=========================================="
echo "后端环境验证"
echo "=========================================="

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

check_service() {
  local name=$1
  local cmd=$2
  
  if eval "$cmd" &>/dev/null; then
    echo -e "${GREEN}✓${NC} $name"
  else
    echo -e "${RED}✗${NC} $name"
  fi
}

check_service "MySQL" "mysql -u root -proot -h localhost -e 'SELECT 1'"
check_service "Redis" "redis-cli PING"
check_service "Elasticsearch" "curl -s http://localhost:9200/_cluster/health"
check_service "Kibana" "curl -s http://localhost:5601/api/status"
check_service "RabbitMQ" "curl -s -u guest:guest http://localhost:15672/api/overview"
check_service "MongoDB" "docker exec mongo mongosh --eval 'db.adminCommand(\"ping\")'"

echo "=========================================="
```

---

## 📚 下一步

1. **启动后端应用**
   ```bash
   cd /Users/mac/code/Java/mall
   mvn clean install
   mvn spring-boot:run
   ```

2. **验证应用连接**
   ```bash
   curl http://localhost:8080/swagger-ui.html
   ```

3. **导入 Postman 集合** （在 `document/postman/` 中）

---

## 🆘 获取帮助

### 查看完整日志
```bash
cat ~/.mall-setup.log
```

### 重置环境
```bash
# 停止所有容器
docker-compose -f /Users/mac/code/Java/mall/document/docker/docker-compose-env.yml down

# 删除所有数据卷
docker volume prune -f

# 重新启动
/Users/mac/code/Java/mall/setup-macos-backend.sh
```

### 查看容器详细日志
```bash
# MySQL
docker logs mysql -f

# Elasticsearch
docker logs elasticsearch -f

# RabbitMQ
docker logs rabbitmq -f
```

---

## 📝 版本信息

- **macOS**: 10.15+（测试于 Sequoia 15.2）
- **Docker**: 29.3.1+
- **JDK**: 25.0.2（OpenJDK）
- **MySQL**: 8.0.42
- **Elasticsearch**: 8.10.2
- **搭建日期**: 2026-04-18

---

**一切就绪！🎉**
