# CampusLink 开发与验收记录

更新时间：2026-09-01（Asia/Shanghai）

## 当前完成状态

需求文档所描述的第一版校园互助平台已经形成可运行的全栈工程。后端位于 `backend/`，前端位于 `frontend/`，仓库根目录提供 MySQL、后端和前端三服务的 Docker Compose 配置。

### 后端

- Java 21 + Spring Boot 3.4.5 + MyBatis-Plus 3.5.12。
- 注册、登录、HMAC 令牌认证、角色鉴权、个人资料。
- 积分钱包、冻结与结算流水、信用记录和分页通知。
- 任务发布、筛选、申请、并发安全选人、履约、凭证、验收、驳回、取消。
- 异常任务申请与管理员 `REFUND / SETTLE / RESUME` 裁决，支持责任方信用调整。
- 超时定时扫描、幂等处理以及评价后的信用联动。
- 技能供需档案、可解释匹配、双向互补和技能互助订单。
- 评价、举报、用户治理、任务监管、技能分类维护和统计面板。
- 图片上传与静态访问、Swagger/OpenAPI、统一响应与异常处理。
- Flyway V1-V4 增量迁移、外键/检查约束、任务与订单乐观锁。
- 可通过环境变量关闭演示数据并安全引导首个管理员。

### 前端

- Vue 3 + Vite + Pinia + Vue Router + Element Plus。
- 登录注册、响应式应用框架和桌面/移动端导航。
- 任务大厅、发布、详情、我的任务、申请选择及订单履约。
- 凭证上传、验收/驳回、异常上报、评价和举报。
- 技能档案、匹配推荐、技能互助状态流转。
- 钱包与信用流水、站内消息、个人资料。
- 管理端用户、任务、举报/异常裁决和技能分类维护。
- Nginx 单页应用回退及 `/api`、`/uploads` 反向代理。
- Capacitor Android 原生工程、模拟器 API 环境、原生 hash 路由与移动安全区适配。
- Android 最低 API 24、目标 API 36；支持 Android Studio 模拟器或真机安装调试。

## 已执行验证

- 后端 `mvn package`：通过；`CampusLinkWorkflowTest` 覆盖 6 个集成场景。
- 集成场景包括任务与评价信用闭环、技能匹配/管理员鉴权、异常退款、超时幂等、并发选人唯一性和图片上传。
- MySQL 8 实机验证：Flyway V1-V4 均成功，15 张表（14 张业务表及 Flyway 历史表），登录、公开任务和 OpenAPI 接口正常。
- 前端 `npm run build`：通过。
- Docker Compose 配置解析：通过。
- H2 后端与 Vite 生产预览冒烟检查：根页面、单页应用深层路由、管理员登录、管理端技能接口和 OpenAPI 均返回成功；检查后已停止临时进程。
- Android Web 资源构建和 `cap sync android`：通过；`cap doctor android` 检查通过。
- Android Studio Quail 3、SDK 36 与 Pixel 8/API 36 模拟器已完成安装配置；原生 App 构建、安装和启动通过。
- Android 模拟器经 `10.0.2.2:8080` 与本机后端联调通过，`alice / demo123` 登录成功。
- 修正 Capacitor 本地 HTTPS 页面访问 HTTP 调试后端产生的 Mixed Content 问题，并为任务详情、任务发布和履约详情补充统一返回按钮。
- 使用 Chromium DevTools 以 390×844、触控模式检查登录页、任务大厅和登录后底部导航：无横向溢出，核心触控区域正常。
- 移动端依赖 `npm audit`：0 个已知漏洞。

## 环境与范围说明

- Docker MySQL 验证使用的命名卷仍保留，可继续用于本地调试。
- 完整镜像拉取曾受 Docker Hub OAuth IPv6 网络超时影响；这是外部网络问题，Dockerfile 与 Compose 配置已完成。
- 当前开发机已安装 Android Studio 与 Android SDK 36，Pixel 8/API 36 AVD 可直接用于后续移动端回归测试与 APK 构建。
- Redis、短信、地图和真实支付等外部基础设施不属于当前第一版的必要依赖；积分为平台内部账本。
- 正式部署前建议关闭演示数据、替换所有密钥，并执行一次真实浏览器与目标移动设备的人工走查。

## 常用命令

```powershell
cd D:\CampusLink\backend
mvn package

cd D:\CampusLink\frontend
npm run build

cd D:\CampusLink
docker compose --env-file .env.example config --quiet
```
