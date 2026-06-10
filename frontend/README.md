# CourtFlow Frontend

该目录为 CourtFlow 前端源码，基于 Vue 3 + Vite 实现，包含用户端和管理端两个多入口页面。

## 目录结构

```text
src/demo       用户端源码
src/admin      管理端源码
src/shared     共享请求、格式化和常量
demo           用户端 HTML 入口
admin          管理端 HTML 入口
```

## 技术栈

- Vue 3
- Vite
- Pinia
- ECharts / vue-echarts
- lucide-vue-next

## 开发命令

安装依赖：

```powershell
npm install
```

本地开发：

```powershell
npm run dev
```

生产构建：

```powershell
npm run build
```

构建并同步到 Spring Boot 静态目录：

```powershell
npm run build:backend
```

## 当前页面说明

- `demo/index.html`：用户端入口
- `admin/index.html`：管理端入口
- 用户端保留原业务逻辑，重构为 Vue 响应式实现
- 管理端支持超级管理员和场地管理员两种后台视图

## 说明

- 该目录下的构建产物会被同步到后端 `src/main/resources/static`
- 管理端总览图表使用 ECharts，初始构建体积会比普通页面更大一些
