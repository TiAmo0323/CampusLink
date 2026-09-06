# CampusLink 设计符合性整改记录

日期：2026-09-05

基线：2.3 节及之前采用《CampusLink需求分析与详细设计书_正式版2》，2.3 节之后采用《CampusLink需求分析与详细设计书_后续章节修订版》。本记录对应 `DESIGN_CONFORMANCE_AUDIT_20260905.md` 中的 F01—F16。

## 已完成整改

| 审查项 | 实施结果 | 主要落点 |
|---|---|---|
| F01 | 删除技能分类表中错误调用任务下架接口的按钮；技能只执行启用/停用 | `frontend/src/views/AdminView.vue` |
| F02 | 新增管理员举报证据接口，聚合订单、任务、双方用户、完成凭证、重新分配历史与举报处理记录；管理端可查看 | `GovernanceService`、`GovernanceController`、`AdminView.vue` |
| F03 | 普通举报改为结构化处置；确认违规固定扣 10 信用，支持警告、封禁、任务下架，并把处置动作和实际责任用户记录到举报处理结果 | `GovernanceService`、`report.handle_result` |
| F04 | 技能互助发起与接受时均校验双方账户、启用技能、请求方需求、提供方供给及交换技能供给 | `SkillService` |
| F05 | 补充学号、邮箱、头像、任务字段和技能方式校验；参数类型、缺参、404、405采用正确 HTTP 状态；响应始终保留 `data` | `Requests`、`GlobalExceptionHandler`、`ApiResponse` |
| F06 | 任务广场支持关键词、分类、地点、奖励区间、时间区间、状态和四种排序；卡片显示摘要、截止时间和申请人数 | `TaskService`、`TaskController`、`TaskListView.vue` |
| F07 | 申请者列表按信用降序，并返回技能档案、历史完成单量、综合评分和近期评价；前端加入档案抽屉 | `TaskService`、`TaskDetailView.vue` |
| F08 | 增加用户、任务、技能和评价举报入口 | `TaskDetailView.vue`、`SkillsView.vue`、`ReportButton.vue` |
| F09 | 技能互助完成后双方均可评价，防止重复评价，并显示近期收到的评价 | `SkillService`、`SkillsView.vue` |
| F10 | 技能供给/需求可维护线上、线下或两者方式，并可从已有记录回填编辑 | `SkillsView.vue`、`Requests` |
| F11 | 原接取者可从我的任务进入历史订单，只读分页查看本人提交时间、凭证、状态和驳回原因 | `TaskService`、`MyTasksView.vue`、`OrderDetailView.vue` |
| F12 | 技能供给和需求独立分页、独立总数；互换技能选项加载本人全部供给 | `SkillService`、`SkillController`、`SkillsView.vue` |
| F13 | 补齐选择申请者、验收、技能匹配、技能档案和积分流水兼容路径；申请、取消和完成提交包含业务对象 | `TaskController`、`SkillController`、`AccountController` |
| F14 | 管理端返回近 7 日用户/任务/完成趋势、分类分布、完成率和用户环比，并使用 ECharts 展示 | `GovernanceService`、`AdminCharts.vue` |
| F15 | 注册事务写入 `REGISTER_INIT` 信用初始化记录 | `AuthService` |
| F16 | 新增信用差额、积分差额和至少一种积分余额变化的数据库检查约束 | `V6__governance_and_ledger_constraints.sql` |

## 验证结果

- 后端：`mvn test` 原有 18 个用例通过；新增兼容契约、参数语义、信用初始化、失效交换和治理审计用例后，`RevisionWorkflowTest` 14/14 通过，第二轮扩充后合计 21 个用例。
- 前端：`npm.cmd run build` 通过；ECharts 已按需加载。
- 端到端：隔离端口 18081/15173 上执行 `npx playwright test`，8/8 通过，覆盖 360、390、412、1920 像素布局和任务主流程。
- 静态检查：`git diff --check` 无空白错误；仅提示仓库现有 Windows 行尾转换。

## 部署注意

部署到已有数据库时需先备份并执行 Flyway V6。若历史账本中存在不满足差额关系的数据，迁移会拒绝添加检查约束，应先按业务原始凭证修复历史记录，再重新执行迁移。