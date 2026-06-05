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
- 如果需要把 demo 数据同步到远端数据库，可参考 `scripts/apply-remote-demo-data.ps1`
