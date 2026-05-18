#!/bin/bash
set -e

echo "=========================================="
echo "macOS Mall 后端环境完整启动脚本"
echo "=========================================="

DOCKER_DIR="/Users/mac/code/Java/mall/document/docker"
SQL_FILE="/Users/mac/code/Java/mall/document/sql/mall.sql"
LOG_FILE="$HOME/.mall-setup.log"

echo "日志输出到: $LOG_FILE"
exec >> "$LOG_FILE" 2>&1

echo "[$(date)] 开始启动后端环境"

# 第1步：启动 Docker Compose
echo "[$(date)] 第1步: 启动 Docker Compose 中间件栈..."
cd "$DOCKER_DIR"
docker-compose -f docker-compose-env.yml up -d

# 第2步：等待关键服务就绪
echo "[$(date)] 第2步: 等待服务启动（最多等待 300 秒）..."
for i in {1..60}; do
  if docker-compose -f docker-compose-env.yml ps | grep -q "mysql.*running"; then
    echo "[$(date)] MySQL 已启动"
    break
  fi
  echo "[$(date)] 等待 MySQL... ($i/60)"
  sleep 5
done

# 第3步：等待 MySQL 完全就绪
echo "[$(date)] 第3步: 等待 MySQL 接收连接..."
for i in {1..30}; do
  if docker exec mysql mysqld_version &>/dev/null; then
    echo "[$(date)] MySQL 连接成功"
    break
  fi
  echo "[$(date)] 等待 MySQL 就绪... ($i/30)"
  sleep 2
done

# 第4步：导入 SQL
if [ -f "$SQL_FILE" ]; then
  echo "[$(date)] 第4步: 导入 mall 数据库..."
  docker exec -i mysql mysql -u root -proot < "$SQL_FILE"
  echo "[$(date)] 数据库导入成功"
else
  echo "[$(date)] ⚠️  SQL 文件未找到: $SQL_FILE"
fi

# 第5步：验证所有服务
echo "[$(date)] 第5步: 验证服务可用性..."

# MySQL
if docker exec mysql mysql -u root -proot -e "SELECT 1" &>/dev/null; then
  echo "[✓] MySQL (3306): 可用"
else
  echo "[✗] MySQL (3306): 不可用"
fi

# Redis
if docker exec redis redis-cli PING | grep -q "PONG"; then
  echo "[✓] Redis (6379): 可用"
else
  echo "[✗] Redis (6379): 不可用"
fi

# Elasticsearch
sleep 5
if curl -s http://localhost:9200/_cluster/health | grep -q '"status"'; then
  echo "[✓] Elasticsearch (9200): 可用"
else
  echo "[✗] Elasticsearch (9200): 不可用"
fi

# Kibana (需要更多时间启动)
sleep 10
if curl -s http://localhost:5601/api/status | grep -q '"state"'; then
  echo "[✓] Kibana (5601): 可用"
else
  echo "[✗] Kibana (5601): 未就绪 (继续启动中...)"
fi

# RabbitMQ
if curl -s -u guest:guest http://localhost:15672/api/overview | grep -q '"node"'; then
  echo "[✓] RabbitMQ (5672/15672): 可用"
else
  echo "[✗] RabbitMQ (5672/15672): 不可用"
fi

# MongoDB
if docker exec mongo mongosh --eval "db.adminCommand('ping')" &>/dev/null; then
  echo "[✓] MongoDB (27017): 可用"
else
  echo "[✗] MongoDB (27017): 不可用"
fi

# MinIO
if curl -s http://localhost:9001 | grep -q "MinIO"; then
  echo "[✓] MinIO (9090/9001): 可用"
else
  echo "[✗] MinIO (9090/9001): 可能未就绪"
fi

echo ""
echo "=========================================="
echo "后端环境启动完成！"
echo "=========================================="
echo ""
echo "📋 服务清单:"
echo "  • MySQL 8.0: localhost:3306 (root/root)"
echo "  • Redis 7.0: localhost:6379"
echo "  • Elasticsearch 8.10.2: http://localhost:9200"
echo "  • Kibana 8.10.2: http://localhost:5601"
echo "  • RabbitMQ 3.12: http://localhost:15672 (guest/guest 或 mall/mall)"
echo "  • MongoDB 7.0: localhost:27017"
echo "  • MinIO: http://localhost:9001 (minioadmin/minioadmin)"
echo "  • Nginx: http://localhost"
echo ""
echo "✅ 所有服务已启动。日志: $LOG_FILE"
echo "=========================================="

