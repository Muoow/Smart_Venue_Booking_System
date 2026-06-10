# CourtFlow Smart Venue Booking System

CourtFlow 是一个智能场地预约系统，当前仓库包含 Spring Boot 后端、Vue 3 用户端和 Vue 3 管理端，支持本地 demo 演示、后台履约处理和基础部署。

## 当前版本功能

- 用户端支持用户名密码登录、注册、昵称修改、密码修改和退出登录
- 用户端支持场馆浏览、资源选择、日期时段预约、我的预约查看与取消
- 预约规则限制为今天起 14 天内，且当天过去时段不可预约
- 管理端支持预约履约，完整流转为 `待使用 -> 到场签到 -> 使用中 -> 已完成`
- 超级管理员支持总览、资源、预约、用户、订单、支付等完整后台能力
- 场地管理员支持登录后台，仅可查看自己场馆范围内的 `预约管理` 与 `资源管理`
- 后端已修复预约越权访问和订单 / 预约状态竞态问题
- 管理端已接入运营总览图表、语义化图标和多端适配样式

## 功能清单

### 用户端

- 用户注册、登录、退出登录
- 昵称修改、密码修改
- 场馆浏览、资源筛选、搜索
- 预约日期与时段选择
- 我的预约查看、取消预约

### 超级管理员

- 运营总览可视化看板
- 资源管理
- 预约管理与履约处理
- 用户信息管理
- 订单管理
- 支付记录与审核处理

### 场地管理员

- 登录后台
- 查看自己场馆范围内的预约
- 到场签到、结束使用等履约操作
- 查看自己场馆范围内的资源

### 后端保障

- JWT 鉴权与角色区分
- 预约越权访问拦截
- 订单 / 预约状态竞态防护
- demo 数据初始化与多角色演示账号

## 角色说明

- `USER`：普通用户，可在用户端完成预约、查看和取消预约
- `ADMIN`：超级管理员，可使用完整管理后台
- `VENUE_ADMIN`：场地管理员，仅可管理绑定场馆范围内的预约和资源

## 技术栈

- Spring Boot 3
- MyBatis-Plus
- JWT
- H2 / MySQL
- Redis / RabbitMQ
- Vue 3
- Vite
- Pinia
- ECharts / vue-echarts
- Lucide Icons

## 本地启动

### 环境要求

- JDK 21 及以上
- 使用仓库自带的 Maven Wrapper
- Node.js 18 及以上

### 启动命令

Windows PowerShell：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=--courtflow.middleware.enabled=false --server.port=8081"
```

如果 `8081` 被占用，可改成 `8082` 或 `8083`：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=--courtflow.middleware.enabled=false --server.port=8083"
```

### 前端构建

前端源码位于 `frontend` 目录，修改后可执行：

```powershell
cd .\frontend
npm install
npm run build:backend
```

该命令会先构建 Vue 多入口前端，再将产物同步到后端 `src/main/resources/static` 目录。

### 访问地址

- 用户端：`http://127.0.0.1:8081/demo/index.html`
- 管理端：`http://127.0.0.1:8081/admin/index.html`
- 根路径：`http://127.0.0.1:8081/`
- H2 控制台：`http://127.0.0.1:8081/h2-console`

## 测试账号

- 用户：`caojinshuo / 12345`
- 用户：`zhangxiang / 12345`
- 超级管理员：`admin / 12345`
- 场地管理员：`venueadmin1 / 12345`
- 场地管理员：`venueadmin2 / 12345`

## 管理端履约流程

1. 用户预约并支付后，状态为`待使用`
2. 用户到场后，管理员在后台点击`到场签到`
3. 使用开始后，状态变为`使用中`
4. 使用结束后，管理员点击`结束使用`
5. 最终状态变为`已完成`

## 管理端权限边界

- 超级管理员可查看 `运营总览 / 资源管理 / 预约管理 / 用户信息 / 订单管理 / 支付记录`
- 场地管理员登录后仅显示 `预约管理 / 资源管理`
- 场地管理员只能看到自己绑定场馆范围内的数据
- 支付、订单、用户和总览仍由超级管理员负责

## 目录说明

```text
src/main/java/com/courtflow/homework   后端代码
src/main/resources/static/demo         构建后的用户端静态资源
src/main/resources/static/admin        构建后的管理端静态资源
src/main/resources/db/demo             demo 初始化数据
frontend/src/demo                      Vue 用户端源码
frontend/src/admin                     Vue 管理端源码
deploy/mysql/init                      MySQL 初始化脚本
scripts                                辅助脚本
```

## 部署说明

- 本地演示模式使用 H2 内存数据库，便于直接启动和体验
- Docker 部署可使用仓库根目录的 `Dockerfile` 和 `docker-compose.yml`
- 生产环境默认使用 `prod` 配置，依赖 MySQL、Redis、RabbitMQ
- GitHub Actions 会在推送到 `main` 后自动构建并推送 Docker 镜像

### 远端初始化

- 正式远端初始化脚本：`scripts/bootstrap-remote-prod.ps1`
- 远端 `compose` 模板同步脚本：`scripts/sync-remote-compose.ps1`
- 脚本会执行以下动作：
  - 上传 `deploy/mysql/init/01_schema.sql`
  - 上传 `deploy/mysql/init/02_seed.sql`
  - 进入远端 `mysql` 容器幂等执行建表和基线数据初始化
  - 按需重启远端应用容器
  - 对 `actuator/health` 做健康检查

Windows PowerShell 示例：

```powershell
.\scripts\bootstrap-remote-prod.ps1 `
  -ServerHost 150.158.132.178 `
  -ServerUser ubuntu `
  -KeyPath "c:\Users\GALAXY\Desktop\新建文件夹\main\myserver_ssh.pem" `
  -RemoteDir /home/ubuntu/courtflow `
  -DbContainer mysql `
  -DbName courtflow `
  -DbUser admin `
  -DbPassword admin123 `
  -AppContainer courtflow `
  -RestartApp
```

- 如果只想补库表，不重启应用，可去掉 `-RestartApp`
- 如果只想补表不补数据，可加 `-SkipSeed`
- 如果只想补数据不补表，可加 `-SkipSchema`
- 当前远端基线数据已与现版本账号体系对齐：
  - `caojinshuo / 12345`
  - `zhangxiang / 12345`
  - `admin / 12345`
  - `venueadmin1 / 12345`
  - `venueadmin2 / 12345`

### 初始化文件

- `deploy/mysql/init/01_schema.sql`：MySQL 正式表结构
- `deploy/mysql/init/02_seed.sql`：当前版本标准基线数据，可重复执行
- `deploy/remote/docker-compose.remote.yml`：远端运行目录使用的正式 `compose` 模板
- `scripts/sync-remote-compose.ps1`：把正式 `compose` 模板同步到远端运行目录，并自动备份旧文件
- `scripts/apply-remote-demo-data.ps1`：旧的本地隧道灌库脚本，仅适合临时场景，不再作为正式远端初始化入口

### 推荐顺序

1. 先执行 `scripts/sync-remote-compose.ps1`，把远端运行目录的 `docker-compose.yml` 对齐到仓库模板
2. 再执行 `scripts/bootstrap-remote-prod.ps1`，完成建表、灌数和健康检查
3. 如需重新拉起整套服务，再到远端执行 `docker compose up -d`
