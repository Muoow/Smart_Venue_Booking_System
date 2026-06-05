# CourtFlow Smart Venue Booking System

CourtFlow 是一个智能场地预约系统，包含用户端和管理端两个页面，当前仓库版本已经完成本地 demo 体验、管理员履约操作和基础部署配置整理。

## 当前版本功能

- 用户端支持用户名密码登录、注册、昵称展示与修改、密码修改
- 用户端支持场馆与场地浏览、预约、取消预约、预约记录查看
- 预约规则已限制为今天起 14 天内，且当天过去时段不可预约
- 用户端预约取消已补全 toast 提示，不再出现“点击没反应”
- 管理端支持场馆、场地、用户、订单、支付、预约记录管理
- 管理端预约履约支持 `待使用 -> 到场签到 -> 结束使用 -> 已完成`
- 管理端页面已做大屏和中小屏自适应，适配不同尺寸电脑

## 技术栈

- Spring Boot 3
- MyBatis-Plus
- JWT
- H2 / MySQL
- Redis / RabbitMQ
- HTML、CSS、JavaScript

## 本地启动

### 环境要求

- JDK 21 及以上
- 使用仓库自带的 Maven Wrapper

### 启动命令

Windows PowerShell：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=--courtflow.middleware.enabled=false --server.port=8081"
```

### 访问地址

- 用户端：`http://127.0.0.1:8081/demo/index.html`
- 管理端：`http://127.0.0.1:8081/admin/index.html`
- 根路径：`http://127.0.0.1:8081/`
- H2 控制台：`http://127.0.0.1:8081/h2-console`

## 测试账号

- 用户：`caojinshuo / 12345`
- 用户：`zhangxiang / 12345`
- 管理员：`admin / 12345`

## 管理端预约履约流程

1. 用户预约并支付后，状态为`待使用`
2. 用户到场后，管理员在后台点击`到场签到`
3. 使用开始后，状态变为`使用中`
4. 使用结束后，管理员点击`结束使用`
5. 最终状态变为`已完成`

## 目录说明

```text
src/main/java/com/courtflow/homework   后端代码
src/main/resources/static/demo         用户端页面
src/main/resources/static/admin        管理端页面
src/main/resources/db/demo             demo 初始化数据
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
