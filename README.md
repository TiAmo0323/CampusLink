# CampusLink

CampusLink 是一个面向校园场景的微任务与技能互助平台。项目围绕信用、积分和履约流程，将任务发布、申请接取、过程确认、结果验收、积分结算、技能匹配及平台治理整合在同一套 Web 与 Android 应用中。

当前仓库包含完整的 Java 后端、Vue 3 前端、Capacitor Android 工程和 Docker Compose 部署配置。

## 已实现功能

- 用户注册、登录、令牌鉴权、个人资料和角色权限
- 校园微任务发布、筛选、申请、选择接取者和取消
- 任务履约、完成凭证上传、验收、驳回重提和评价
- 积分冻结、退款、结算、流水记录与信用分变动
- 技能供给、技能需求、可解释匹配和技能互助流程
- 站内通知、普通举报、异常任务、超时检测与管理员裁决
- 用户、任务、举报和技能分类管理台
- 响应式 Web 界面与 Capacitor Android 移动端
- Flyway 数据库迁移、并发状态保护及关键业务集成测试

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.4、MyBatis-Plus、Flyway、SpringDoc |
| 数据库 | MySQL 8（部署）／H2 MySQL 模式（本地演示与测试） |
| Web 前端 | Vue 3、Vite 6、Pinia、Vue Router、Element Plus、Axios |
| Android | Capacitor 8、Android SDK 36、Gradle 8 |
| 部署 | Docker、Docker Compose、Nginx |

## 项目结构

```text
CampusLink/
├─ backend/                   Spring Boot 后端、数据库迁移与集成测试
├─ frontend/
│  ├─ src/                    Vue 3 应用源码
│  ├─ android/                Capacitor Android 原生工程
│  └─ MOBILE.md               Android 模拟器与真机运行说明
├─ docker-compose.yml         MySQL、后端和前端编排
├─ .env.example               Docker 环境变量示例
└─ DEVELOPMENT_PROGRESS.md    开发与验收记录
```

需求分析与详细设计的 Word/PDF 文档由项目方单独保管，不随源码仓库发布。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 20+ 与 npm
- MySQL 8 或 Docker Desktop（持久化部署时需要）
- Android Studio、Android SDK Platform 36 和 Android Emulator（运行移动端时需要）

## 本地快速启动

默认配置使用内存中的 H2 数据库并自动载入演示数据，适合快速体验。后端停止后，本次运行产生的数据会被清空。

### 1. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端地址为 `http://localhost:8080`。

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

### 2. 启动 Web 前端

另开一个终端：

```powershell
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。开发服务器会将 `/api` 和 `/uploads` 转发到 `http://localhost:8080`。

### 演示账户

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 学生用户 | `alice` | `demo123` |
| 学生用户 | `bob` | `demo123` |

## Android 模拟器运行

项目最低支持 Android 7（API 24），编译及目标 SDK 为 API 36。建议创建 Pixel 8、API 36 的模拟器。

1. 先按“本地快速启动”中的命令启动后端，并保持后端终端开启。
2. 同步并打开 Android 工程：

```powershell
cd frontend
npm install
npm run android:sync
npm run android:open
```

3. 在 Android Studio 中选择 JVM 21、`app` 运行配置和目标模拟器，然后点击 Run。

Android 模拟器通过 `http://10.0.2.2:8080` 访问开发电脑上的后端。该地址已配置在 `frontend/.env.android` 中，调试构建允许本地 HTTP；正式发布应改用 HTTPS。

真机运行、SDK 配置和 APK 构建步骤见 [frontend/MOBILE.md](frontend/MOBILE.md)。

## Docker Compose 部署

Docker 模式使用 MySQL 持久化业务数据和上传文件：

```powershell
Copy-Item .env.example .env
# 修改 .env 中的数据库密码和 CAMPUSLINK_TOKEN_SECRET
docker compose up --build -d
```

默认访问地址：

- Web：`http://localhost`
- API：`http://localhost:8080`
- MySQL：`localhost:3306`

可通过 `.env` 中的 `FRONTEND_PORT` 和 `MYSQL_PORT` 调整宿主机端口。生产环境应关闭演示数据，并配置首个管理员账号和高强度令牌密钥。

## 构建与验证

后端测试及打包：

```powershell
cd backend
mvn package
```

Web 前端构建：

```powershell
cd frontend
npm run build
```

Android 资源同步与 Debug APK 构建：

```powershell
cd frontend
npm run android:sync
cd android
.\gradlew.bat assembleDebug
```

APK 输出位置：

```text
frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

校验 Docker Compose 配置：

```powershell
docker compose --env-file .env.example config --quiet
```

## 配置与安全说明

- 不要提交 `.env`、签名密钥、Android `local.properties` 或用户上传文件。
- 本地 H2 模式适合演示；需要保存数据时请使用 Docker Compose/MySQL。
- Android Debug 版本允许访问本机 HTTP 后端；Release 版本应连接 HTTPS 服务。
- 数据库结构由 Flyway 增量迁移管理，部署时不会自动删除已有业务表。

## 进一步文档

- [后端接口与配置说明](backend/README.md)
- [Android 移动端运行说明](frontend/MOBILE.md)
- [开发与验收记录](DEVELOPMENT_PROGRESS.md)
