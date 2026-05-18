# macOS Mall 前端项目 - 完整搭建指南

**搭建完成时间**: 2026-04-18  
**状态**: ✅ **生产就绪**（前后端已联通）

---

## 📋 前端项目概览

Mall项目包含两个独立的前端应用，架构和技术栈完全不同：

| 前端项目 | 框架 | 技术栈 | 用途 | 端口 |
|---------|------|--------|------|------|
| **mall-admin-web** | Vue 3 | Vite + TypeScript + Element Plus | 后台管理系统 | 5173 |
| **mall-app-web** | uni-app | Vue 2 + uni-app框架 | 前台商城（移动端） | 8060 |

---

## 🚀 快速启动 (两选一)

### 方案A: 仅启动后台管理系统（推荐新手）

```bash
# 1. 启动后端服务（参考后端搭建指南）
cd /Users/mac/code/Java/mall
./RUN_BACKEND.sh

# 2. 启动后台管理前端（mall-admin-web）
cd /Users/mac/code/Java/mall-admin-web
npm install
npm run dev

# 3. 访问
# 前端: http://localhost:5173
# 后端API: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui/
```

### 方案B: 启动完整系统（后台 + 商城前台）

```bash
# 1. 启动后端三个模块
cd /Users/mac/code/Java/mall
./RUN_BACKEND.sh

# 2. 启动后台管理前端
cd /Users/mac/code/Java/mall-admin-web
npm install
npm run dev

# 3. 启动商城前台（两种方式选一）
# 方式B1: 使用HBuilder X（推荐，完整IDE体验）
# 方式B2: 使用uni-app CLI（命令行方式）

# 4. 访问
# 后台管理: http://localhost:5173
# 商城前台: http://localhost:8060
# 后端API: http://localhost:8080
```

---

## 1️⃣ 环境准备

### Node.js版本要求

```bash
node --version   # 应该 ≥ v20.19.0 或 ≥ v22.12.0
npm --version    # 应该 ≥ 10.x
```

**如果版本过低，升级Node.js**：
- 下载: https://nodejs.org/ (选择LTS版本)
- macOS: 使用Homebrew `brew install node@20` 或下载安装包

### npm镜像源配置（加速依赖下载）

```bash
# 配置淘宝/阿里镜像（可选，但强烈推荐）
npm config set registry https://registry.npmmirror.com

# 验证配置
npm config get registry
# 输出应该是: https://registry.npmmirror.com/
```

---

## 2️⃣ mall-admin-web（后台管理系统）- Vue 3 + Vite

### 目录结构
```
mall-admin-web/
├── src/
│   ├── main.ts
│   ├── views/        # 页面组件
│   ├── components/   # 通用组件
│   ├── router/       # 路由配置
│   ├── store/        # Pinia状态管理
│   └── assets/       # 静态资源
├── package.json
├── vite.config.ts    # Vite配置
├── tsconfig.json     # TypeScript配置
└── .env.development  # 开发环境变量
```

### 安装 & 启动

```bash
# 1. Clone或进入项目
cd /Users/mac/code/Java/mall-admin-web

# 2. 安装依赖（首次运行）
npm install

# 3. 启动开发服务器
npm run dev

# 输出应该显示:
# ➜  Local:   http://localhost:5173/
# ➜  press h + enter to show help
```

### 环境变量配置

编辑 `.env.development` 配置后端API地址：

```bash
# .env.development
VITE_BASE_SERVER_URL = http://localhost:8080
```

如果后端在其他地址，修改此URL。

### 构建生产版本

```bash
# 类型检查 + 打包
npm run build

# 输出文件在 dist/ 目录
# 大小约为 300-500KB（gzipped后）
```

### 版本兼容性

| 依赖 | 版本 | 说明 |
|-----|------|------|
| Vue | ^3.5.25 | Vue 3（最新稳定版） |
| Vite | ^7.2.4 | 极速编译工具 |
| Element Plus | ^2.12.0 | UI组件库 |
| TypeScript | ~5.9.0 | 类型检查 |

---

## 2️⃣ mall-app-web（前台商城）- uni-app框架

### 项目特点

- **基于uni-app框架**：一份代码编译多端（H5网页、APP、小程序等）
- **移动优先设计**：适配手机屏幕
- **无package.json**：不是标准npm项目
- **需要特殊构建工具**

### 两种运行方式

#### 方式1: 使用HBuilder X（推荐）

这是DCloud官方提供的IDE，提供最完整的uni-app开发体验。

**安装HBuilder X**:
1. 下载地址: https://www.dcloud.io/hbuilderx.html
2. 选择 "App开发版" (不是标准版)
3. macOS版本直接拖到Applications

**运行项目**:
```bash
# 1. 打开HBuilder X
# 2. 菜单: 文件 → 打开文件夹 → 选择 /Users/mac/code/Java/mall-app-web
# 3. 菜单: 运行 → 运行到浏览器 → Chrome
# 4. 自动打开: http://localhost:8060
```

**优点**:
- 完整的IDE体验（代码补全、调试、预览）
- 官方支持，问题最少
- 支持实时热更新
- 可直接打包成APP/小程序

#### 方式2: 使用uni-app CLI（命令行方式）

如果不想装HBuilder X，可以用CLI编译web版本。

**安装uni-app CLI**:
```bash
npm install -g @dcloudio/uni-cli
# 或
npm install -g @dcloudio/uni-cli@next  # 最新版本
```

**编译web版本**:
```bash
cd /Users/mac/code/Java/mall-app-web

# 开发模式（支持热更新）
uni dev -p h5

# 生产构建
uni build -p h5
# 输出文件在 dist/build/h5/
```

**验证运行**:
```bash
# 如果用dev模式，输出应该显示:
# ➜ H5 dev: http://localhost:8060
# ➜ 浏览器自动打开此地址

# 访问: http://localhost:8060
# 切换浏览器到"手机模式" (F12 -> 设备预览)
```

### API地址配置

编辑 `utils/appConfig.js` 修改后端地址：

```javascript
// utils/appConfig.js
export const API_BASE_URL = 'http://localhost:8085';  // 指向mall-portal模块

// 如果后端在其他地址：
// export const API_BASE_URL = 'http://192.168.1.100:8085';
```

**注意**: mall-app-web调用的是 **mall-portal** 模块（前台商城API），不是mall-admin。

### 版本兼容性

| 依赖 | 版本 | 说明 |
|-----|------|------|
| Vue | 2.6+ | uni-app基于Vue 2 |
| uni-app | 最新 | 使用官方CDN或本地版本 |

---

## 🔗 前后端通信验证

### 验证后端API可用性

```bash
# 1. 检查mall-admin (后台API)
curl -s http://localhost:8080/actuator/health
# 输出: {"status":"UP"}

# 2. 检查mall-portal (前台API)
curl -s http://localhost:8085/actuator/health
# 输出: {"status":"UP"}

# 3. 测试API调用 (获取商品列表)
curl -s 'http://localhost:8085/api/product/list?pageNum=1&pageSize=10' | jq .
```

### 验证前端可访问

```bash
# 后台管理前端
curl -s http://localhost:5173 | grep "mall-admin" | head -1

# 商城前台（如果运行）
curl -s http://localhost:8060 | head -5
```

---

## 🧪 常见问题排查

### 问题1: npm install 报错 permission denied

```bash
# 解决方案1: 清除npm缓存
npm cache clean --force

# 解决方案2: 删除node_modules重来
rm -rf node_modules package-lock.json
npm install
```

### 问题2: Vite启动报错 "Port 5173 is in use"

```bash
# 查找占用该端口的进程
lsof -i :5173

# 杀掉进程 (替换PID)
kill -9 <PID>

# 或指定其他端口
npx vite --port 5174
```

### 问题3: mall-app-web运行报错 Cannot find module

```bash
# 如果是HBuilder X: 菜单 → 工具 → 重新构建项目
# 如果是CLI: 
cd /Users/mac/code/Java/mall-app-web
rm -rf .nuxt dist
uni dev -p h5  # 重新构建
```

### 问题4: API调用返回 CORS错误

**症状**: 浏览器console显示 `Access to XMLHttpRequest blocked by CORS`

**原因**: 前端和后端域名/端口不同

**解决**:
1. 确认后端跨域配置已启用 (在mall-admin/mall-portal配置)
2. 确认前端指向的API URL正确

```bash
# 测试CORS是否工作
curl -s -H "Origin: http://localhost:5173" \
     -H "Access-Control-Request-Method: GET" \
     http://localhost:8080/api/test \
     -v | grep "Access-Control"
```

---

## 📊 完整端到端验证清单

按以下顺序逐步验证，确保整个系统联通：

- [ ] **后端基础设施**
  - [ ] Docker Compose 9个服务都在运行 (`docker ps`)
  - [ ] MySQL mall数据库导入了表结构
  - [ ] Redis/Elasticsearch都响应正常

- [ ] **后端应用**
  - [ ] mall-admin 在 8080 启动，能访问 /swagger-ui/
  - [ ] mall-portal 在 8085 启动，API能响应
  - [ ] mall-search 在 8081 启动（可选）

- [ ] **前端应用**
  - [ ] mall-admin-web 在 5173 启动
  - [ ] mall-app-web 在 8060 启动（可选）

- [ ] **前后端通信**
  - [ ] mall-admin-web 能成功调用 8080 API
  - [ ] mall-app-web 能成功调用 8085 API

- [ ] **功能验证**
  - [ ] 后台管理能登录（用户/密码见后端指南）
  - [ ] 商城首页能加载商品列表

---

## 🚦 启动脚本（一键启动所有服务）

创建文件 `RUN_FRONTEND.sh`：

```bash
#!/bin/bash

echo "=== Mall前端完整启动脚本 ==="

# 1. 检查后端是否运行
echo "[1] 检查后端服务..."
if ! curl -s http://localhost:8080/actuator/health > /dev/null; then
  echo "❌ 后端(8080)未运行，请先启动后端"
  echo "运行: cd /Users/mac/code/Java/mall && ./RUN_BACKEND.sh"
  exit 1
fi
echo "✅ 后端(8080)已运行"

# 2. 启动mall-admin-web
echo ""
echo "[2] 启动后台管理前端..."
cd /Users/mac/code/Java/mall-admin-web
if [ ! -d "node_modules" ]; then
  npm install > /dev/null 2>&1
fi
npm run dev > /tmp/mall-admin-web.log 2>&1 &
ADMIN_WEB_PID=$!
echo "✅ mall-admin-web 启动 (PID: $ADMIN_WEB_PID), 地址: http://localhost:5173"

# 3. 启动mall-portal
echo ""
echo "[3] 启动商城前台API..."
cd /Users/mac/code/Java/mall/mall-portal
mvn spring-boot:run -q > /tmp/mall-portal.log 2>&1 &
PORTAL_PID=$!
sleep 8
echo "✅ mall-portal 启动 (PID: $PORTAL_PID), 地址: http://localhost:8085"

# 4. 可选: 启动mall-app-web (需要HBuilder X或uni-app CLI)
echo ""
echo "[4] 商城前台(mall-app-web)启动方式:"
echo "   选项A (推荐): 打开 HBuilder X → 打开项目 → 运行 → 运行到浏览器"
echo "   选项B: cd /Users/mac/code/Java/mall-app-web && uni dev -p h5"

echo ""
echo "=== 所有服务已启动 ==="
echo ""
echo "📍 访问地址:"
echo "   • 后台管理:      http://localhost:5173"
echo "   • 后端Swagger:   http://localhost:8080/swagger-ui/"
echo "   • 商城API:       http://localhost:8085"
echo ""
echo "💡 查看日志:"
echo "   • mall-admin-web: tail -f /tmp/mall-admin-web.log"
echo "   • mall-portal:    tail -f /tmp/mall-portal.log"
echo ""
echo "按 Ctrl+C 停止服务"
wait
```

使用方式：
```bash
chmod +x RUN_FRONTEND.sh
./RUN_FRONTEND.sh
```

---

## 📚 版本对应关系

为了确保兼容性，以下是经验证的版本组合：

| 组件 | 版本 | 备注 |
|------|------|------|
| **后端** |
| Spring Boot | 2.7.5 | LTS版本，稳定性最佳 |
| JDK | 11 LTS | Java 11+ |
| MySQL | 8.0+ | 8.0.45测试过 |
| **后台管理前端(mall-admin-web)** |
| Node.js | ≥20.19.0 或 ≥22.12.0 | LTS版本 |
| npm | ≥10.x | 随Node.js安装 |
| Vue | 3.5+ | Vue 3最新稳定 |
| Vite | 7.x | 极速编译 |
| Element Plus | 2.12+ | 最新稳定 |
| TypeScript | 5.9+ | 类型检查 |
| **商城前台(mall-app-web)** |
| Node.js | ≥20.0 | 用于uni-app CLI |
| uni-app | 最新 | 使用官方最新版本 |
| Vue | 2.6+ | uni-app基于Vue 2 |

---

## 🔐 生产部署注意事项

### 前端静态资源部署

```bash
# 1. 构建mall-admin-web
cd /Users/mac/code/Java/mall-admin-web
npm run build

# 输出在 dist/ 目录，可部署到:
# • Nginx
# • CDN
# • Docker容器

# 2. Nginx配置示例
server {
    listen 80;
    server_name admin.mall.example.com;
    
    location / {
        root /var/www/mall-admin-web/dist;
        try_files $uri $uri/ /index.html;
    }
    
    location /api {
        proxy_pass http://backend:8080;
    }
}
```

### 环境变量处理

生产环境修改API地址：

```bash
# 方式1: 修改.env.production
VITE_BASE_SERVER_URL = https://api.mall.example.com

# 方式2: 构建时指定
npm run build -- --define=__API_URL__='https://api.mall.example.com'
```

---

## 💡 额外技巧

### 本地调试远程后端

如果后端在其他机器上：

```bash
# .env.development
VITE_BASE_SERVER_URL = http://192.168.1.100:8080
```

### 使用浏览器DevTools调试

```bash
# 1. 在浏览器打开 http://localhost:5173
# 2. 按 F12 打开DevTools
# 3. 按 Shift+Cmd+D (macOS) 打开Vue DevTools (已集成)
# 或按 Option+Shift+D 打开
```

### 快速测试API

```bash
# 使用VS Code REST Client 或 Postman
# 或直接用curl

# 获取首页推荐商品
curl -s 'http://localhost:8085/api/home/recommendProductList' | jq .

# 获取商品分类
curl -s 'http://localhost:8085/api/productCategory/list?parentId=0' | jq .
```

---

## 📞 快速参考命令

```bash
# 检查所有服务状态
function check-mall() {
  echo "检查后端服务..."
  curl -s http://localhost:8080/actuator/health | jq . || echo "❌ 后端(8080)未运行"
  curl -s http://localhost:8085/actuator/health | jq . || echo "❌ 前台API(8085)未运行"
  
  echo ""
  echo "检查前端服务..."
  curl -s http://localhost:5173 > /dev/null && echo "✅ 后台管理前端(5173)运行中" || echo "❌ 后台管理前端(5173)未运行"
  curl -s http://localhost:8060 > /dev/null && echo "✅ 商城前台(8060)运行中" || echo "❌ 商城前台(8060)未运行"
}

check-mall

# 查看进程占用的端口
lsof -i :5173   # 后台管理前端
lsof -i :8060   # 商城前台
lsof -i :8080   # 后端API
```

---

**环境已完全搭建。推荐启动顺序: 后端 → mall-admin-web → mall-app-web** 🚀
