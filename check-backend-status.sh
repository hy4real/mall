#!/bin/bash
set -e

echo "=========================================="
echo "🔍 后端环境状态检查"
echo "=========================================="
echo ""

DOCKER_DIR="/Users/mac/code/Java/mall/document/docker"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 检查 Docker 连接
if ! docker ps &>/dev/null; then
  echo -e "${RED}✗ Docker 未运行${NC}"
  echo "  请启动 Docker Desktop: open /Applications/Docker.app"
  exit 1
fi

cd "$DOCKER_DIR"

echo "📊 容器运行状态:"
echo "---"
docker-compose ps

echo ""
echo "🔗 服务可达性检查:"
echo "---"

# MySQL
if docker exec mysql mysqld_version &>/dev/null 2>&1; then
  echo -e "${GREEN}✓${NC} MySQL (3306)"
else
  echo -e "${YELLOW}⏳${NC} MySQL (3306) 启动中..."
fi

# Redis
if docker exec redis redis-cli PING 2>&1 | grep -q PONG; then
  echo -e "${GREEN}✓${NC} Redis (6379)"
else
  echo -e "${YELLOW}⏳${NC} Redis (6379) 启动中..."
fi

# Elasticsearch
if curl -s http://localhost:9200/_cluster/health 2>/dev/null | grep -q '"status"'; then
  ES_STATUS=$(curl -s http://localhost:9200/_cluster/health | grep -o '"status":"[^"]*' | cut -d'"' -f4)
  echo -e "${GREEN}✓${NC} Elasticsearch (9200) - 状态: $ES_STATUS"
else
  echo -e "${YELLOW}⏳${NC} Elasticsearch (9200) 启动中..."
fi

# Kibana
if curl -s http://localhost:5601/api/status 2>/dev/null | grep -q '"state"'; then
  echo -e "${GREEN}✓${NC} Kibana (5601)"
else
  echo -e "${YELLOW}⏳${NC} Kibana (5601) 启动中（通常需要 2-3 分钟）..."
fi

# RabbitMQ
if docker exec rabbitmq rabbitmq-diagnostics -q ping &>/dev/null 2>&1; then
  echo -e "${GREEN}✓${NC} RabbitMQ (5672/15672)"
elif curl -s -u guest:guest http://localhost:15672/api/overview 2>/dev/null | grep -q '"node"'; then
  echo -e "${GREEN}✓${NC} RabbitMQ (5672/15672)"
else
  echo -e "${YELLOW}⏳${NC} RabbitMQ (5672/15672) 启动中..."
fi

# MongoDB
if docker exec mongo mongosh --eval "db.adminCommand('ping')" &>/dev/null 2>&1; then
  echo -e "${GREEN}✓${NC} MongoDB (27017)"
else
  echo -e "${YELLOW}⏳${NC} MongoDB (27017) 启动中..."
fi

# MinIO
if curl -s http://localhost:9001 2>/dev/null | grep -q "MinIO"; then
  echo -e "${GREEN}✓${NC} MinIO (9090/9001)"
else
  echo -e "${YELLOW}⏳${NC} MinIO (9090/9001) 启动中..."
fi

echo ""
echo "📋 下一步:"
echo "---"
echo "1. 等待所有服务显示 ✓"
echo "2. 导入数据库:"
echo "   docker exec -i mysql mysql -u root -proot < /Users/mac/code/Java/mall/document/sql/mall.sql"
echo ""
echo "3. 验证数据库导入:"
echo "   mysql -u root -proot -h localhost -e 'USE mall; SHOW TABLES;'"
echo ""
echo "4. 启动后端应用:"
echo "   cd /Users/mac/code/Java/mall && mvn clean install && mvn spring-boot:run"
echo ""
echo "=========================================="
echo ""
