#!/bin/bash
set -e

echo "=========================================="
echo "🚀 Mall 后端应用一键启动"
echo "=========================================="
echo ""

PROJECT_DIR="/Users/mac/code/Java/mall"
cd "$PROJECT_DIR"

# Step 1: 验证中间件
echo "[1/4] 验证后端服务..."
SERVICES_OK=0
docker-compose -f document/docker/docker-compose-env.yml ps 2>/dev/null | grep -c "Up" > /dev/null && SERVICES_OK=1

if [ $SERVICES_OK -eq 0 ]; then
  echo "❌ 后端服务未启动，先启动 Docker Compose..."
  docker-compose -f document/docker/docker-compose-env.yml up -d
  sleep 10
fi

echo "✅ 后端服务已就绪"
echo ""

# Step 2: 编译项目
echo "[2/4] 编译项目 (Maven clean install)..."
mvn clean install -DskipTests -q
echo "✅ 编译完成"
echo ""

# Step 3: 启动应用
echo "[3/4] 启动后端应用..."
echo "应用将在 http://localhost:8080 启动"
echo "Swagger UI: http://localhost:8080/swagger-ui.html"
echo ""
echo "按 Ctrl+C 停止应用"
echo "---"
echo ""

mvn spring-boot:run

