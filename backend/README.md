# CampusLink 后端

## 已实现能力

- 账户注册登录、HMAC 签名令牌、角色鉴权和个人资料。
- 任务发布、积分冻结、分页检索、申请、并发安全选择接取者。
- 开始任务、提交凭证、验收/驳回、积分结算、信用变化与通知。
- 任务异常申请、管理员退款/结算/恢复裁决、自动超时扫描。
- 技能供给与需求、可解释匹配评分、双向互补、技能互助订单。
- 评价信用联动、举报治理、管理员统计和分页管理。
- 图片凭证上传、Flyway 数据库迁移和 Swagger/OpenAPI 文档。

## 本地启动

需要 Java 21 和 Maven 3.9+。默认使用内存 H2，并自动载入演示数据。

```powershell
mvn test
mvn spring-boot:run
```

服务地址为 `http://localhost:8080`，Swagger UI 为 `http://localhost:8080/swagger-ui.html`，OpenAPI JSON 为 `http://localhost:8080/v3/api-docs`。

演示账号：

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | admin | admin123 |
| 学生/发布者 | alice | demo123 |
| 学生/接取者 | bob | demo123 |

登录后在请求头中携带：

```text
Authorization: Bearer <登录接口返回的 token>
```

## Docker + MySQL

在仓库根目录执行：

```powershell
Copy-Item .env.example .env
# 修改 .env 中的三个密码/密钥
docker compose up --build -d
```

MySQL 数据与上传图片分别保存在命名卷 `campuslink_mysql` 和 `campuslink_uploads`。数据库结构由 Flyway 增量迁移，不会在应用启动时 DROP 数据表。

生产环境建议设置 `CAMPUSLINK_SEED_DEMO_DATA=false`，并在首次启动时设置 `CAMPUSLINK_BOOTSTRAP_ADMIN_USERNAME` 和 `CAMPUSLINK_BOOTSTRAP_ADMIN_PASSWORD` 创建首个管理员；后续可移除这两个变量。课程演示环境可保留为 `true`。

## 主要配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | H2 内存库 | JDBC 地址 |
| `SPRING_DATASOURCE_USERNAME` | `sa` | 数据库用户 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | 数据库密码 |
| `SPRING_DATASOURCE_DRIVER` | `org.h2.Driver` | JDBC 驱动 |
| `MYSQL_PORT` | `3306` | Docker MySQL 暴露到宿主机的端口 |
| `CAMPUSLINK_TOKEN_SECRET` | 仅开发默认值 | 令牌签名密钥，生产必须替换 |
| `CAMPUSLINK_SEED_DEMO_DATA` | `true` | 是否在空库载入演示数据 |
| `CAMPUSLINK_BOOTSTRAP_ADMIN_USERNAME` | 空 | 关闭演示数据时的首个管理员账号 |
| `CAMPUSLINK_BOOTSTRAP_ADMIN_PASSWORD` | 空 | 首个管理员密码，至少 8 位 |
| `CAMPUSLINK_UPLOAD_DIR` | `./uploads` | 图片存储目录 |
| `CAMPUSLINK_TIMEOUT_CRON` | 每 10 分钟 | 超时扫描 Cron |

## API 清单

统一响应格式：`{"code":0,"message":"success","data":...}`。业务失败时 `code` 为 400/401/403/404/500 等，具体原因位于 `message`。

### 公共接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册并初始化积分/信用 |
| POST | `/api/auth/login` | 登录并获取令牌 |
| GET | `/api/public/tasks` | 分页查询公开任务 |
| GET | `/api/public/tasks/{id}` | 任务详情 |
| GET | `/api/public/skills` | 启用的技能分类 |

### 账户、钱包和通知

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/PUT | `/api/users/me` | 查询/更新本人资料 |
| GET | `/api/wallet?page=1&size=20` | 余额、冻结积分、分页积分/信用流水 |
| GET | `/api/notifications?page=1&size=20` | 分页站内通知 |
| POST | `/api/notifications/{id}/read` | 标记通知已读 |
| POST | `/api/files/images` | 上传图片凭证，multipart 字段名 `file` |

### 微任务

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tasks` | 发布任务并冻结积分 |
| GET | `/api/tasks/mine` | 我发布/接取的任务 |
| POST | `/api/tasks/{id}/applications` | 申请任务 |
| GET | `/api/tasks/{id}/applications?page=1&size=20` | 发布者分页查看申请人 |
| POST | `/api/applications/{id}/accept` | 原子确认一名接取者 |
| POST | `/api/tasks/{id}/cancel` | 招募期取消并退款 |
| GET | `/api/orders/{id}` | 履约订单详情 |
| POST | `/api/orders/{id}/start` | 接取者开始任务 |
| POST | `/api/orders/{id}/completions` | 提交完成说明/凭证 URL |
| POST | `/api/orders/{id}/approve` | 发布者验收并结算 |
| POST | `/api/orders/{id}/reject` | 发布者驳回并要求重提 |
| POST | `/api/tasks/{id}/abnormal` | 任务双方发起异常处理 |

### 技能互助

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/skills/profile` | 本人技能供给与需求 |
| POST/DELETE | `/api/skills/offers[/{id}]` | 新增、更新或删除供给 |
| POST/DELETE | `/api/skills/needs[/{id}]` | 新增、更新或删除需求 |
| GET | `/api/skills/matches` | Top 10 可解释匹配结果 |
| POST/GET | `/api/exchanges` | 发起/查询技能互助 |
| POST | `/api/exchanges/{id}/{action}` | `accept/reject/start/complete` |

### 评价与治理

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/reviews` | 已完成业务双方评价 |
| GET | `/api/reviews/users/{id}` | 用户收到的评价 |
| POST | `/api/reports` | 提交举报 |
| GET | `/api/reports/mine` | 我的举报 |

### 管理员接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/dashboard` | 平台聚合统计 |
| GET | `/api/admin/users` | 分页用户列表 |
| PUT | `/api/admin/users/{id}/status/{status}` | 正常/禁用账户 |
| GET | `/api/admin/tasks` | 分页任务列表 |
| GET | `/api/admin/reports` | 分页举报列表 |
| POST | `/api/admin/reports/{id}/handle` | 处理普通举报 |
| POST | `/api/admin/abnormal-tasks/{reportId}/resolve` | `REFUND/SETTLE/RESUME` 异常裁决 |
| GET | `/api/admin/skills` | 查询全部技能分类（含已停用） |
| POST | `/api/admin/skills` | 新增技能分类 |
| PUT | `/api/admin/skills/{id}/status/{status}` | 启用/停用技能分类 |

## 数据库迁移

迁移脚本位于 `src/main/resources/db/migration`：

- V1：14 张业务表、索引和唯一约束。
- V2：异常任务恢复所需的原状态字段。
- V3：业务外键。
- V4：积分、信用、任务奖励和评价星级检查约束。

不要修改已经执行过的迁移文件；后续结构变更应新增更高版本迁移。

## 测试范围

`CampusLinkWorkflowTest` 当前覆盖六个集成场景：核心任务闭环、技能匹配/管理员鉴权、异常退款裁决、超时幂等、并发确认唯一订单、图片上传。核心闭环同时验证积分结算和评价信用联动。
