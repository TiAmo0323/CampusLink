**CampusLink 全项目设计符合性审查（2026-09-05）**

结论：**当前项目不严格符合指定设计文档，不宜按“全部要求已完成”验收。** 主干任务闭环和已有自动化测试可以通过，但仍有可复现的错误操作、业务入口缺失、接口契约偏差及验收覆盖缺口。

**依据、范围与证据口径**

- 第1章至第2.3节（含其全部子节）采用《CampusLink需求分析与详细设计书_正式版2.pdf》；第2.4—2.6节采用《CampusLink需求分析与详细设计书_后续章节修订版.pdf》。修订版第2.6节明确覆盖的冲突条款按修订版执行，未明确覆盖的正式版内容仍有效。
- 下文页码为 **PDF物理页码**，不是页脚页码。正式版共69页，修订版共11页。
- 审查对象为当前工作区，包括已有未提交修改和新增文件，不只审查HEAD。文档内的操作指令作为文档内容辨识，不视为用户另行授权。
- 已阅读两份PDF文本、全部后端业务服务/控制器及配置/DTO/实体/Mapper/迁移、全部Vue业务页面及导航/请求/样式、现有测试、部署和移动工程配置，并复核已有性能证据。未进行每张原型图的逐像素视觉验收。
- 本轮未修改业务源代码、迁移或设计PDF；新增审查材料，更新构建输出和测试结果。动态复现仅使用本机18089后端、独立H2内存库及15189前端，不连接现有业务库。
- [输入与源码SHA-256清单](design-audit-20260905/source-hashes.json)标识本次审查快照。原有README及验收报告仅作线索，不代替代码和测试证据。

**本轮实际验证**

| 检查 | 结果 | 证据与限制 |
|---|---|---|
| `mvn.cmd -o -f backend/pom.xml test` | 18项通过，0失败/错误 | [业务测试](design-audit-20260905/com.campuslink.CampusLinkWorkflowTest.txt)、[修订回归](design-audit-20260905/com.campuslink.RevisionWorkflowTest.txt)；H2数据库 |
| `npm.cmd --prefix frontend run build` | 成功 | Element Plus分包约944KB，存在大包警告；不代表运行性能已验收 |
| 现有Playwright回归 | 8项通过，约29.8秒 | [本轮原始结果](design-audit-20260905/playwright-results.json)；Chrome headless，360/390/412/1920px等视口，隔离H2后端 |
| 补充API边界/契约复现 | 发现多个不符合项 | [API结果](design-audit-20260905/api-results.json)，未保存登录令牌 |
| 补充管理端浏览器复现 | 确认技能按钮误下架任务 | [浏览器结果](design-audit-20260905/ui-results.json)、[点击前截图](design-audit-20260905/admin-skill-before.png) |
| `docker compose --env-file .env.example config --quiet` | 退出码0 | 仅配置解析，本轮未部署生产实例 |
| 历史JMeter原始JTL重新核算 | 与既有报告重测数字一致 | 本轮未重新执行MySQL大规模压测；覆盖限制见V01 |

**确定的不符合项**

P1表示可能错误修改其他资源或阻碍关键治理；P2表示明确功能、规则或契约不符合；P3表示结构及界面设计偏差。

**F01 · P1：技能分类的“下架”按钮实际下架同ID任务。**

- 依据：正式版§1.1.8、§2.2.7；修订版§2.6区分任务下架与技能分类治理。
- [AdminView.vue:134](D:/CampusLink/frontend/src/views/AdminView.vue:134)把技能行传给任务`delist(row)`；[同文件:85](D:/CampusLink/frontend/src/views/AdminView.vue:85)调用`/api/admin/tasks/{id}/delist`。
- 实际浏览器复现：点击技能ID=1“Java编程”的下架，发送`POST /api/admin/tasks/1/delist`并返回200；任务1公开详情从200变为404，技能1仍是`ENABLED`。
- 应移除错误按钮或连接技能停用操作，并验证技能/任务ID相同时不会误操作其他资源。

**F02 · P1：管理员可以裁决，却无法取得订单履约证据。**

- 依据：正式版§1.1.5.4（PDF14）、§2.2.4.4（PDF40）要求管理员查看双方证据后裁决。
- [AdminView.vue:124](D:/CampusLink/frontend/src/views/AdminView.vue:124)仅展示举报原因和说明，没有订单详情、完成凭证、双方信息及完整履约历史入口；裁决对话框要求手填责任用户ID。
- [TaskService.java:77](D:/CampusLink/backend/src/main/java/com/campuslink/service/TaskService.java:77)只准当前双方及历史接取者查看订单，其他控制器也没有管理员证据读取接口。实测管理员访问已有订单详情返回403。
- 管理列表存在订单/任务ID不能代替凭证详情。应增加经角色鉴权的治理详情，在裁决前展示证据和参与者。

**F03 · P2：普通举报缺少“核实违规扣10信用”的闭环，封禁没有处理说明留痕。**

- 依据：正式版§1.1.7.4、§1.1.8、§2.2.7.1；修订版PDF8明确核实违规`-10`，信用变化需保存原因。
- [GovernanceService.java:33](D:/CampusLink/backend/src/main/java/com/campuslink/service/GovernanceService.java:33)普通举报处理只写结果文字、状态和通知，不调用信用服务，没有结构化“核实违规”决策。下一行的封禁只改状态和撤销凭证，不接收管理员身份、理由或写治理审计。
- 实测举报用户并填写“核实违规”结案，信用仍为`100→100`。问题不是要求解析这几个汉字，而是不存在能够执行设计规则的明确操作。
- 异常订单手动信用调整只适用于订单双方，不能补足普通举报的处罚。应设计处理动作、责任用户和固定扣分规则，结案与处罚事务一致，保存操作者及说明。

**F04 · P2：已失效的技能互助请求仍可接受。**

- 依据：正式版§1.1.6.4（PDF17）要求请求失效或用户被封禁时不能接受，§2.2.5.3要求校验有效供需关系。
- [SkillService.java:60](D:/CampusLink/backend/src/main/java/com/campuslink/service/SkillService.java:60)接受时仅检查角色、PENDING及发起者账户状态，不重新检查技能启用状态、供给是否仍存在。
- 实测先发起互助，再由管理员停用技能，提供方仍能接受，返回`ACCEPTED`。
- 发起接口也不检查发起者结构化需求，无需求用户可直接创建互助。应明确有效供需判定，并在接受这一实际承诺节点再次校验。

**F05 · P2：服务端合法性校验和错误状态不完整。**

- 依据：正式版§1.2.2、§2.2.2.2（PDF31）；修订版§2.6校园身份格式校验、§2.6.2的400/404/500语义。
- [AccountService.java](D:/CampusLink/backend/src/main/java/com/campuslink/service/AccountService.java)直接保存头像URL，[Requests.java:16](D:/CampusLink/backend/src/main/java/com/campuslink/dto/Requests.java:16)只有长度限制。实测`javascript:invalid-audit-url`被保存并返回200。这证明URL合法性校验缺失，**不等于已证明脚本执行漏洞**。
- `studentNo`没有格式及最大长度校验，任务`category/location`等没有与数据库一致的长度限制，技能方式没有枚举校验。实测含`!!!`的学号可注册。文档未规定具体学校学号正则，不能擅定格式，但当前确实没有格式校验层。
- 实测51字符任务分类返回500；`GET /api/public/tasks?page=abc`返回500，而非参数错误400。
- [GlobalExceptionHandler.java:24](D:/CampusLink/backend/src/main/java/com/campuslink/common/GlobalExceptionHandler.java:24)将未单独处理的类型转换、路由不存在等异常统一映射500；F13中的不存在路由也返回500。

**F06 · P2：任务检索和卡片信息没有达到正式版要求。**

- 依据：正式版§1.1.4.2（PDF9）、§2.2.3.2（PDF33）、图2-13说明（PDF49）。
- [TaskController.java:4](D:/CampusLink/backend/src/main/java/com/campuslink/controller/TaskController.java:4)、[TaskService.java:13](D:/CampusLink/backend/src/main/java/com/campuslink/service/TaskService.java:13)仅支持分页、关键词、分类、状态，没有奖励区间、独立地点/时间条件或可选择的排序。
- 关键词能匹配地点，但不能完成规定的多条件组合筛选。默认创建时间倒序，不按申请截止时间优先展示真正可申请任务。
- [TaskListView.vue:5](D:/CampusLink/frontend/src/views/TaskListView.vue:5)卡片缺少描述摘要、申请截止时间和已申请人数；详情里存在部分信息不能代替要求的卡片展示。

**F07 · P2：发布者缺少选择申请者所需的技能、评分和历史信息。**

- 依据：正式版§1.1.4.4（PDF11）、图2-15说明（PDF50）要求技能档案、历史接单、综合评分、近期评价和信用降序展示。
- [TaskService.java:37](D:/CampusLink/backend/src/main/java/com/campuslink/service/TaskService.java:37)按申请时间倒序，仅返回申请和通用用户信息，没有技能/历史/评分聚合。
- [TaskDetailView.vue:7](D:/CampusLink/frontend/src/views/TaskDetailView.vue:7)只有昵称、信用、说明及选择按钮，无详情抽屉或替代页面。后端虽有用户评价查询接口，前端没有调用。

**F08 · P2：学生普通举报界面只覆盖任务。**

- 依据：正式版§1.1.8（PDF20）、修订版PDF8保留`USER/TASK/SKILL/REVIEW`四类普通举报。
- 全前端仅[TaskDetailView.vue:2](D:/CampusLink/frontend/src/views/TaskDetailView.vue:2)使用`ReportButton`，目标固定TASK；没有用户、技能信息或评价内容举报入口。
- 后端接受四种类型不能替代双端功能交付。另，SKILL目前查技能字典，不是具体学生技能供给；设计中“技能信息”指何种对象需要明确，不能宣称个人技能内容治理已完整覆盖。

**F09 · P2：互助完成后的双方评价没有页面入口，历史评价缺少展示。**

- 依据：正式版§2.2.5.3（PDF42）、§1.1.7.3、§2.2.8评价功能。
- [SkillsView.vue](D:/CampusLink/frontend/src/views/SkillsView.vue)操作仅到接受、开始、完成和取消，没有COMPLETED后评价。唯一创建评价的页面逻辑在OrderDetailView，业务类型写死TASK。
- 后端支持SKILL_EXCHANGE评价，但学生不能通过页面完成该闭环；前端也没有调用评价列表接口展示本人或他人近期评价。

**F10 · P2：技能提供方式/期望方式无法由用户维护。**

- 依据：正式版§1.1.6.1—1.1.6.2（PDF15）要求填写供给方式、期望方式并维护档案。
- [SkillsView.vue:16](D:/CampusLink/frontend/src/views/SkillsView.vue:16)把`availableMode/preferredMode`初始化BOTH，模板没有选择器；已有卡片也不展示方式。
- 没有从已有记录回填的编辑入口。选择相同技能再保存虽然可以更新，但用户需重填，且可能将原方式覆盖成BOTH，未实现完整档案编辑。

**F11 · P2：重新分配后的历史履约在前端无法完整访问。**

- 依据：修订版PDF8、11要求原接取者只读本人历史、保留原凭证并分页。
- [TaskService.java:47](D:/CampusLink/backend/src/main/java/com/campuslink/service/TaskService.java:47)“我接取的”只查询当前accepter，原接取者被替换后订单从入口消失。“我申请的”不返回历史订单ID，[MyTasksView.vue](D:/CampusLink/frontend/src/views/MyTasksView.vue)因此跳到公开任务详情。
- 即使手工输入订单URL，[OrderDetailView.vue](D:/CampusLink/frontend/src/views/OrderDetailView.vue)的historical分支也只显示说明和状态，无凭证、提交时间、驳回原因或翻页控件。后端历史分页存在，前端没有完整接通。

**F12 · P2：技能档案分页契约不完整，互换技能选项受当前页限制。**

- 依据：修订版§2.6.2（PDF11）明确分别返回`offersTotal/needsTotal`。
- [SkillService.java:20](D:/CampusLink/backend/src/main/java/com/campuslink/service/SkillService.java:20)只有`total=max(offersTotal,needsTotal)`，缺两个独立总数，已动态验证。
- [SkillsView.vue:20](D:/CampusLink/frontend/src/views/SkillsView.vue:20)仅由当前页10条供给生成互换技能选项，其他页的技能不能直接选择，应单独逐页加载或提供检索。
- 后端列表默认10、最大50大体已落实；钱包、消息、管理员页面主动设首屏20条，与“统一默认10条”的界面口径仍有差异。

**F13 · P2：未明确被修订版替换的正式接口契约仍有偏差。**

依据：正式版§2.2.9表2-24（PDF54—56），修订版§2.6.2（PDF10—11）。下表不将修订版明确引入的`/api/public`及`/api/exchanges/{id}/...`等列为错误。

| 功能 | 正式契约 | 当前实现 | 核验 |
|---|---|---|---|
| 选择申请者 | `POST /api/tasks/{taskId}/applications/{applicationId}/accept` | `POST /api/applications/{id}/accept` | 原路径实测500，无映射 |
| 验收通过 | `POST /api/orders/{orderId}/accept` | `POST /api/orders/{id}/approve` | 原路径实测500，无映射 |
| 技能匹配 | `GET /api/skills/match` | `GET /api/skills/matches` | 原路径实测500，无映射 |
| 技能档案 | `POST /api/users/skills`、`/api/users/skill-needs` | `/api/skills/offers`、`/api/skills/needs` | 控制器静态核对 |
| 积分流水 | `GET /api/points/transactions` | `GET /api/wallet`内嵌流水 | 原路径实测500，无映射 |
| ORDER申诉 | `POST /api/reports`按目标类型处理 | 普通举报拒绝ORDER，另调`/api/tasks/{id}/abnormal` | 服务/控制器静态核对 |

实际前后端互相适配，不代表上述业务不能运行，但对按设计接入的调用方属于契约破坏。[TaskController](D:/CampusLink/backend/src/main/java/com/campuslink/controller/TaskController.java)、[SkillController](D:/CampusLink/backend/src/main/java/com/campuslink/controller/SkillController.java)、[AccountController](D:/CampusLink/backend/src/main/java/com/campuslink/controller/AccountController.java)需补兼容路径，或明确形成新的设计修订。

响应也未严格一致：申请/取消空成功而非表2-24业务对象，完成提交返回订单聚合而非TaskCompletionVO。`application.yml`的`non_null`使空响应省略data，实测错误体只有code/message，未保持修订版三字段结构。

**F14 · P2：管理员Dashboard缺少规定的统计图和趋势。**

- 依据：正式版§2.2.7.2（PDF45）、图2-19说明（PDF53）要求近7日任务发布、分类分布、完成率、活跃/用户增长趋势、环比及ECharts。
- [GovernanceService.java:29](D:/CampusLink/backend/src/main/java/com/campuslink/service/GovernanceService.java:29)只有5个总数，[AdminView.vue:94](D:/CampusLink/frontend/src/views/AdminView.vue:94)只有计数卡片；前端依赖没有ECharts。
- `pendingReports`只统计PENDING，超时/驳回异常和用户申诉直接产生PROCESSING，计数不能反映全部未结案事项，应明确指标含义。

**F15 · P2：注册未写设计要求的信用初始化记录。**

- 依据：正式版§2.2.2.1（PDF29—30）明确积分、信用初始化均写对应记录，失败整体回滚。
- [AuthService.java:26](D:/CampusLink/backend/src/main/java/com/campuslink/service/AuthService.java:26)设置credit_score=100、写积分流水，没有信用初始事件。
- 新用户实测：信用100、积分流水1条、信用记录0条。注册事务和积分初始化本身已实现，缺的是信用初始化留痕。

**F16 · P2：账本数据库约束未完整落实修订版。**

- 依据：修订版§2.6.1（PDF10）规定信用变化等于前后差、积分变化等于可用余额差、允许0但新积分事件至少一种余额变化。
- V1—V5未见这些差额相等及至少一种余额变化的CHECK；用户积分非负、当前信用、奖励和评价范围CHECK已存在。
- 当前[PointCreditService](D:/CampusLink/backend/src/main/java/com/campuslink/service/PointCreditService.java)按差额写入，不能因此说正常账本已经错误；缺口是设计规定的数据库兜底。本轮未向业务库注入坏账数据。
- V5正确将未知旧冻结余额留NULL，也未添加修订版禁止的非零约束，这两项符合。

**其他严格设计偏差（P3）**

- 正式版§1.2.5、§2.1.1规定`Controller→Service→Mapper→Database`及Entity/DTO/VO分层。Controller未直接查Mapper，但TaskService/SkillService直接用JdbcTemplate执行查询或审计写入；DTO集中Requests嵌套记录，大量返回Map，没有规划的VO层和若干独立服务/页面。合并资源可保持功能，但无设计变更说明时不能说类/资源设计逐项一致。
- 修订版§2.6.2要求核心状态集中于States.java，该文件存在且正常协议值大体一致，但GovernanceService、AbnormalTaskService等仍大量写状态字符串，未全面使用对应枚举。
- 正式版§1.1.6.3（PDF17）写权重可经application.yml配置，目前`.5/.2/.2/.1`写死。数值符合，配置能力缺失；文档允许百分制四舍五入，不将此算作评分错误。
- 正式版§2.2.8要求登录/注册同卡片Tab、移动注册单列表单、5步履约时间线、匹配分圆环、钱包/信用/评价三Tab、信用档位。实际为独立登录注册页、注册用户名/昵称始终两列、4步履约条、数字匹配分和钱包双Tab。这些是界面设计偏差，严重性低于错误业务操作。
- 发布表单缺少当前可用积分即时提示及字段下方校验反馈；上传区关闭文件清单。窄屏不溢出不能证明这些具体UI要求已满足。

**额外风险及需要明确的条款**

- AuthService.userView将学号、邮箱、可用/冻结积分用于申请者、匹配候选和互助双方响应，超出页面所需。本文不将其表述为“公开任务详情泄露发布者隐私”，公开详情采用精简卡片。建议区分本人、管理员和其他学生模型。
- 正式版表2-11写“低于60限制接取高积分任务”，却没有高积分阈值。当前按任务minCreditScore限制申请、按60限制发布。应明确或废止前一规则，不能擅定阈值后验收。
- 互助接受时记录约定时间目前只能沿用发起时可空时间，无接受时修改参数、列表不展示。是否必须非空需细化；页面不足以支持双方确认时间的完整交互。
- 生产要求关闭演示账号并配置随机密钥；Compose示例仍默认开启演示数据。本轮无生产部署证据，只确认可以配置，不能声称生产设置已满足。

**按章节核对的覆盖矩阵**

“主规则有实现”只针对表内列出的内容，不代表模块所有细节均符合。

| 文档范围 | 核对结果 | 缺口/证据 |
|---|---|---|
| §1.1.1—1.1.2角色/模块 | 学生、管理员及六模块骨架存在 | 治理、评价、检索未齐 |
| §1.1.3.1注册 | 唯一性、BCrypt、200初始积分及流水、自动登录 | F05/F15 |
| §1.1.3.2、修订登录安全 | 2h JWT、7d哈希刷新、轮换、5次锁10分钟、退出/封禁撤销 | 18项回归通过，不等于穷尽全部并发交错 |
| §1.1.3.3资料 | 本人更新、头像上传、手机首屏可用 | F05/F10/F12 |
| §1.1.4.1发布 | 时间/信用/积分校验、事务冻结、预计时长 | F05及表单细则 |
| §1.1.4.2检索 | 匿名、分页、发布者批量读取 | F06 |
| §1.1.4.3、修订撤回 | 鉴权/状态/信用/自申请/重复检查，撤回留痕、通知 | 响应偏差F13 |
| §1.1.4.4选择 | 唯一订单、并发锁、申请/任务事务 | F07/F13 |
| §1.1.5.1—1.1.5.3履约 | 开始、提交、驳回重提、结算、信用+5、通知 | F13及UI细则 |
| §1.1.5.4、修订取消 | 招募退款不扣信用、待执行发起方扣5、原因和双方通知 | F02/F13 |
| 修订驳回上限/超时 | 3次转异常、超时扣2、单任务事务、新期限 | 已有后端场景通过 |
| 修订重分配历史 | 复用订单、轮次、旧提交人/期限审计 | F11 |
| §1.1.6.1—1.1.6.2供需 | 分表、字典关联、唯一约束、available_time | F10/F12 |
| §1.1.6.3匹配 | 50/20/20/10、无评价0.6、近30日完成、互补及ID同分排序、批量数据 | 配置能力缺失 |
| §1.1.6.4及修订互助 | 接受、开始、提供方先完成/发起方确认、取消 | F04/F09 |
| §1.1.7积分/信用/评价 | 事务账本、冻结前后、评价信用、重复限制、边界零事件 | F03/F09/F15/F16 |
| §1.1.8治理 | 举报API、领取、结案、封禁撤销、独立下架标记 | F01/F02/F03/F08 |
| §1.2.1性能 | 原始压测证据可复算 | V01，未覆盖所有规定接口 |
| §1.2.2—1.2.3安全/一致性 | 参数绑定、BCrypt、鉴权、主要事务/锁、图片解码和大小检查 | F05；H2不能替代MySQL全面并发验证 |
| §1.2.4/1.2.6及修订§2.4 | 一套Vue、响应式、Chrome设备模拟主链通过 | 具体UI/缺失功能未齐；真机不是本版最低要求 |
| §1.2.5/§2.1—2.2架构 | 主技术栈、分层骨架存在 | P3资源/类设计偏差 |
| §2.2.7.2统计 | 聚合计数存在 | F14 |
| §2.2.9及修订§2.6.2契约 | JSON/JWT/公共读取/分页主结构存在 | F05/F12/F13 |
| §2.3及修订§2.6.1数据库 | 14业务表+2技术审计表，主要唯一键/索引/外键和V5字段齐备 | F16；V1—V4相对HEAD无工作区改写 |
| 修订§2.4.4部署 | Nginx/Vue、Spring Boot、MySQL，持久卷、Flyway | Compose解析通过，生产未验证 |
| 修订§2.5范围 | 基本技术/命名，未依赖短信/Redis/支付 | 不因缺可选扩展判失败 |
| 修订§2.6.3七目标 | 有显著实现及回归证据 | 18+8用例不能证明所有目标无缺口 |

**V01 · 性能与测试验收不完整**

独立读取[JMeter归档](verification/jmeter-20260905.zip)并按近邻秩重算p95：

| 重测标签 | 样本 | 成功 | p95 |
|---|---:|---:|---:|
| task-list | 9,708 | 9,708 | 269ms |
| task-detail | 9,682 | 9,682 | 200ms |
| skill-match | 5,816 | 5,816 | 55ms |
| task-publish | 2,453 | 2,453 | 175ms |

重测合计27,659次，与既有报告一致。首次发布4,709次，成功3,444、失败1,265，原始失败确实保留。这部分证据应认可。

但正式版§1.2.1还明确点名**申请列表、完成提交和验收结算**的响应指标，归档没有这三类性能标签。MySQL双订单并发结算脚本验证正确性，不能替代提交/结算p95测试。正确结论是“所测四类请求的归档结果达标”，不是“所有性能要求通过”。本轮未重新执行十万数据MySQL/JMeter，也无生产容量证据。

现有8项UI用例虽以UI-M01—M08命名，却未验证用户/技能/评价举报、互助后评价、管理裁决证据、历史接取者凭证翻页、技能误下架。补充测试说明其可遗漏实际缺陷。

Android真机/WebView本轮未重跑仅作为边界记录：修订版§2.4.2允许Chrome设备模拟满足最低要求，不能单独据此判不符合。浏览器模拟与APK构建也不能宣称真机功能通过。

**建议验收顺序**

1. 先修F01跨资源误下架、F02管理证据读取，再补F03/F04治理及技能有效性。
2. 补检索、申请者信息、举报及互助评价入口、历史查看、字段和分页校验。
3. 锁定两份PDF的保留契约，逐条修实现；若改变契约，应形成明确修订，不能仅改README后宣称符合。
4. 为已发现问题增加要求导向的回归；补申请列表、提交、结算的MySQL性能验收，再作逐条结论。

复现材料保存在[design-audit-20260905](design-audit-20260905/)。probe脚本从项目根目录运行，目标固定为本机审查端口，须先启动全新隔离实例；它们是缺陷复现材料，不是生产操作脚本。API脚本后的新用户信用初始化检查结果一并保存在api-results.json。

本轮收尾：审查临时后端和前端已停止，18089/15189端口无监听；H2测试数据随进程退出释放。报告链接全部可解析，归档后源码及输入文件哈希未变化。
