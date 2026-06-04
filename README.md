# CourtFlow Smart Venue Booking System

CourtFlow 是一个智能场地预约系统，包含用户端和管理端两个页面。

- 用户端支持登录、注册、场地浏览、预约、取消预约、个人设置
- 管理端支持场馆、场地、预约记录等基础管理
- 本地可直接使用 `demo` 配置启动，无需额外安装 MySQL、Redis、RabbitMQ

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

- `caojinshuo / 12345`
- `zhangxiang / 12345`
- 管理员账号可在 `src/main/resources/db/demo/data.sql` 中查看

## 目录说明

```text
src/main/java/com/courtflow/homework   后端代码
src/main/resources/static/demo         用户端页面
src/main/resources/static/admin        管理端页面
src/main/resources/db/demo             demo 初始化数据
deploy/mysql/init                      MySQL 初始化脚本
```

## 说明

- 本地演示模式使用 H2 内存数据库
- 生产部署仍保留 MySQL、Redis、RabbitMQ、Docker 相关配置
- 如果需要远端部署，可继续使用 `deploy` 目录下的脚本和配置
