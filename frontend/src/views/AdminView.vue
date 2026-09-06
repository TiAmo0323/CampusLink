<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import AdminCharts from '../components/AdminCharts.vue'
import { dateText, statusText } from '../utils/format'

const stats = ref({})
const tab = ref('users')
const users = ref({ records: [] })
const tasks = ref({ records: [] })
const reports = ref({ records: [] })
const skills = ref({records:[],total:0})
const actions = ref({records:[],total:0})
const query = reactive({ page: 1, size: 10 })
const skill = reactive({ name: '', category: '' })
const resolveDialog = ref(false)
const resolvingId = ref(null)
const resolution = reactive({ decision: 'RESUME', liableUserId: null, creditDelta: 0, result: '' })
const reportDialog=ref(false),reportEvidence=ref(null),resolvingReport=ref(null)
const reportAction=reactive({decision:'REJECT_REPORT',liableUserId:null,result:''})
const workload=computed(()=>reports.value.records.filter(x=>x.status==='PENDING'||x.status==='PROCESSING').slice(0,10))

async function load() {
  [stats.value, users.value, tasks.value, reports.value, skills.value, actions.value] = await Promise.all([
    http.get('/api/admin/dashboard'),
    http.get('/api/admin/users', { params: query }),
    http.get('/api/admin/tasks', { params: query }),
    http.get('/api/admin/reports', { params: query }),
    http.get('/api/admin/skills',{params:query}),
    http.get('/api/admin/actions',{params:query})
  ])
}

async function userStatus(user) {
  const next=user.status==='NORMAL'?'BANNED':'NORMAL'
  try{const {value}=await ElMessageBox.prompt(`请填写${next==='BANNED'?'禁用':'恢复'}原因`,'更新用户状态',{inputValidator:v=>v?.trim().length>1||'请填写原因'});await http.put(`/api/admin/users/${user.id}/status/${next}`,{reason:value});ElMessage.success('用户状态已更新');await load()}catch(error){if(error!=='cancel'&&error!=='close')throw error}
}

function openResolution(report) {
  resolvingId.value = report.id
  Object.assign(resolution, { decision: 'RESUME', liableUserId: null, creditDelta: 0, result: '' })
  resolveDialog.value = true
}

async function submitResolution() {
  if (resolution.result.trim().length < 4) {
    ElMessage.warning('请填写至少 4 个字的裁决说明')
    return
  }
  await http.post(`/api/admin/abnormal-tasks/${resolvingId.value}/resolve`, {
    ...resolution,
    liableUserId: resolution.liableUserId || null
  })
  ElMessage.success('异常任务已裁决')
  resolveDialog.value = false
  await load()
}

async function handleReport(report) {
  resolvingReport.value=report;Object.assign(reportAction,{decision:'REJECT_REPORT',liableUserId:null,result:''});reportEvidence.value=await http.get(`/api/admin/reports/${report.id}/evidence`,{params:{page:1,size:10}});reportDialog.value=true
}
async function submitReport(){if(reportAction.result.trim().length<4){ElMessage.warning('请填写至少 4 个字的处理说明');return}await http.post(`/api/admin/reports/${resolvingReport.value.id}/handle`,{...reportAction,liableUserId:reportAction.liableUserId||null});ElMessage.success('举报处理完成');reportDialog.value=false;await load()}

async function addSkill() {
  await http.post('/api/admin/skills', skill)
  skill.name = ''
  skill.category = ''
  ElMessage.success('技能分类已添加')
  await load()
}

async function skillStatus(item) {
  const next=item.status==='ENABLED'?'DISABLED':'ENABLED'
  try{const {value}=await ElMessageBox.prompt(`请填写${next==='DISABLED'?'停用':'启用'}原因`,'更新技能状态',{inputValidator:v=>v?.trim().length>1||'请填写原因'});await http.put(`/api/admin/skills/${item.id}/status/${next}`,{reason:value});ElMessage.success('技能状态已更新');await load()}catch(error){if(error!=='cancel'&&error!=='close')throw error}
}

async function delist(task){const {value}=await ElMessageBox.prompt('下架停止公开展示，不会删除履约历史。请填写原因','下架任务',{inputValidator:v=>v?.trim().length>1||'请填写原因'});await http.post(`/api/admin/tasks/${task.id}/delist`,{reason:value});await load()}
async function claim(report){await http.post(`/api/admin/reports/${report.id}/claim`);await load()}
onMounted(load)
</script>

<template>
  <div>
    <div class="page-head"><div><h1>平台管理台</h1><p>账户、任务、技能分类与举报治理</p></div></div>
    <div class="stat-grid admin-stats" style="margin-bottom:20px">
      <div class="stat surface"><strong>{{ stats.users || 0 }}</strong><span>学生用户 · 环比 {{stats.userGrowthPercent||0}}%</span></div>
      <div class="stat surface"><strong>{{ (stats.recruitingTasks||0)+(stats.activeTasks||0) }}</strong><span>在途任务 · 发布环比 {{stats.taskGrowthPercent||0}}%</span></div>
      <div class="stat surface"><strong>{{ stats.completedTasks || 0 }}</strong><span>已完成 · 环比 {{stats.completedGrowthPercent||0}}%</span></div>
      <div class="stat surface"><strong>{{ stats.pendingReports || 0 }}</strong><span>待处理举报 · 新增环比 {{stats.reportGrowthPercent||0}}%</span></div>
    </div>

    <AdminCharts :stats="stats" />
    <section class="surface panel workload"><div class="page-head"><div><h3>人工待办</h3><p>待领取与处理中举报</p></div></div><el-table :data="workload"><el-table-column prop="id" label="编号" width="75"/><el-table-column prop="targetType" label="对象" width="90"/><el-table-column prop="reasonType" label="原因"/><el-table-column label="状态" width="100"><template #default="{row}">{{statusText(row.status)}}</template></el-table-column><el-table-column label="提交时间" min-width="150"><template #default="{row}">{{dateText(row.createdAt)}}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{row}"><el-button size="small" @click="row.previousStatus ? openResolution(row) : handleReport(row)">处理</el-button></template></el-table-column></el-table><div v-if="workload.length===0" class="empty">暂无人工待办</div></section>
    <section class="surface panel">
      <el-tabs v-model="tab">
        <el-tab-pane label="用户管理" name="users">
          <el-table :data="users.records" style="width:100%">
            <el-table-column prop="username" label="账号" /><el-table-column prop="nickname" label="昵称" />
            <el-table-column prop="creditScore" label="信用" width="80" /><el-table-column prop="availablePoints" label="积分" width="80" />
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status==='NORMAL'?'success':'danger'">{{ statusText(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-if="row.role!=='ADMIN'" text :type="row.status==='NORMAL'?'danger':'success'" @click="userStatus(row)">{{ row.status==='NORMAL'?'禁用':'恢复' }}</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="任务监管" name="tasks">
          <el-table :data="tasks.records">
            <el-table-column prop="id" label="ID" width="65" /><el-table-column prop="title" label="标题" min-width="200" />
            <el-table-column label="操作" width="100"><template #default="{row}"><el-button v-if="!row.isDelisted" text type="danger" @click="delist(row)">下架</el-button><span v-else>已下架</span></template></el-table-column><el-table-column prop="category" label="分类" /><el-table-column prop="rewardPoints" label="积分" width="80" />
            <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
            <el-table-column label="发布时间" min-width="150"><template #default="{ row }">{{ dateText(row.createdAt) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="举报治理" name="reports">
          <article v-for="report in reports.records" :key="report.id" class="list-card" style="background:#f5f7f3">
            <div style="display:flex;justify-content:space-between"><strong>{{ report.previousStatus ? '异常任务' : '普通举报' }} #{{ report.id }}</strong><el-tag>{{ statusText(report.status) }}</el-tag></div>
            <p>{{ report.reasonType }} · {{ report.description }}</p>
            <p v-if="report.handleResult" class="muted">处理结果：{{ report.handleResult }}</p>
            <el-button v-if="report.status==='PENDING'" size="small" @click="claim(report)">领取举报</el-button><el-button v-if="report.status !== 'RESOLVED'" size="small" type="primary" @click="report.previousStatus ? openResolution(report) : handleReport(report)">进入处理</el-button><el-button v-if="!report.previousStatus" size="small" @click="handleReport(report)">查看证据</el-button>
          </article>
          <div v-if="!reports.records.length" class="empty">暂无举报</div>
        </el-tab-pane>

        <el-tab-pane label="技能分类" name="skills">
          <div class="filter-bar" style="padding:0"><el-input v-model="skill.name" placeholder="技能名称" /><el-input v-model="skill.category" placeholder="所属分类" /><el-button type="primary" @click="addSkill">添加</el-button></div>
          <el-table :data="skills.records">
            <el-table-column prop="name" label="技能" /><el-table-column prop="category" label="分类" /><el-table-column prop="status" label="状态" />
            <el-table-column label="操作"><template #default="{ row }"><el-button text @click="skillStatus(row)">{{ row.status==='ENABLED'?'停用':'启用' }}</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="操作审计" name="actions">
          <el-table :data="actions.records">
            <el-table-column prop="id" label="编号" width="75"/>
            <el-table-column label="操作" min-width="150"><template #default="{row}">{{row.reasonType==='ADMIN_USER_STATUS'?'用户状态变更':'技能状态变更'}}</template></el-table-column>
            <el-table-column label="对象" min-width="110"><template #default="{row}">{{row.targetType}} #{{row.targetId}}</template></el-table-column>
            <el-table-column prop="handleResult" label="状态变化" min-width="150"/>
            <el-table-column prop="description" label="操作原因" min-width="200"/>
            <el-table-column prop="handlerId" label="管理员 ID" width="105"/>
            <el-table-column label="操作时间" min-width="160"><template #default="{row}">{{dateText(row.handledAt)}}</template></el-table-column>
          </el-table>
          <div v-if="!actions.records.length" class="empty">暂无管理员操作记录</div>
        </el-tab-pane>
      </el-tabs><el-pagination v-model:current-page="query.page" :total="({users,tasks,reports,skills,actions}[tab]?.total)||0" :page-size="query.size" layout="prev,pager,next" @current-change="load"/>
    </section>

    <el-dialog v-model="reportDialog" title="普通举报处理" width="680">
      <el-descriptions v-if="reportEvidence" :column="2" border><el-descriptions-item label="举报对象">{{reportEvidence.report?.targetType}} #{{reportEvidence.report?.targetId}}</el-descriptions-item><el-descriptions-item label="举报原因">{{reportEvidence.report?.reasonType}}</el-descriptions-item><el-descriptions-item label="举报说明" :span="2">{{reportEvidence.report?.description}}</el-descriptions-item><el-descriptions-item v-if="reportEvidence.order" label="订单状态">{{statusText(reportEvidence.order.status)}}</el-descriptions-item><el-descriptions-item v-if="reportEvidence.task" label="关联任务">{{reportEvidence.task.title}}</el-descriptions-item><el-descriptions-item v-if="reportEvidence.target" label="对象详情" :span="2"><template v-if="reportEvidence.report.targetType==='USER'">{{reportEvidence.target.nickname}}（{{reportEvidence.target.username}}）· {{statusText(reportEvidence.target.status)}}</template><template v-else-if="reportEvidence.report.targetType==='TASK'">{{reportEvidence.target.title}} · {{statusText(reportEvidence.target.status)}}</template><template v-else-if="reportEvidence.report.targetType==='SKILL'">{{reportEvidence.target.name}} / {{reportEvidence.target.category}} · {{statusText(reportEvidence.target.status)}}</template><template v-else>评分 {{reportEvidence.target.rating}} · {{reportEvidence.target.content}}</template></el-descriptions-item></el-descriptions>
      <div v-if="reportEvidence?.completions?.length"><h4>履约凭证</h4><p v-for="c in reportEvidence.completions" :key="c.id">第 {{c.assignmentRound}} 轮 · {{c.description}} · {{statusText(c.reviewStatus)}}</p></div>
      <el-form label-position="top" style="margin-top:18px"><el-form-item label="处理动作"><el-select v-model="reportAction.decision" class="full"><el-option label="驳回举报" value="REJECT_REPORT"/><el-option label="警告用户" value="WARN_USER"/><el-option label="确认违规并扣 10 信用" value="CONFIRM_VIOLATION"/><el-option label="封禁用户" value="BAN_USER"/><el-option v-if="reportEvidence?.report?.targetType==='TASK'" label="下架任务" value="DELIST_TASK"/></el-select></el-form-item><el-form-item v-if="['WARN_USER','CONFIRM_VIOLATION','BAN_USER'].includes(reportAction.decision)" label="责任用户 ID"><el-input-number v-model="reportAction.liableUserId" :min="1"/></el-form-item><el-form-item label="处理说明"><el-input v-model="reportAction.result" type="textarea" :rows="4" maxlength="500" show-word-limit/></el-form-item></el-form>
      <template #footer><el-button @click="reportDialog=false">取消</el-button><el-button type="primary" @click="submitReport">确认处理</el-button></template>
    </el-dialog>
    <el-dialog v-model="resolveDialog" title="异常任务裁决" width="520">
      <el-form label-position="top">
        <el-form-item label="裁决方式">
          <el-radio-group v-model="resolution.decision"><el-radio-button value="RESUME">恢复履约</el-radio-button><el-radio-button value="REFUND">退款发布者</el-radio-button><el-radio-button value="SETTLE">结算接取者</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item label="责任用户 ID（需调整信用时填写）"><el-input-number v-model="resolution.liableUserId" :min="1" controls-position="right" class="full" /></el-form-item>
        <el-form-item label="信用调整（-20 至 20）"><el-input-number v-model="resolution.creditDelta" :min="-20" :max="20" controls-position="right" /></el-form-item>
        <el-form-item label="裁决说明"><el-input v-model="resolution.result" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="resolveDialog=false">取消</el-button><el-button type="primary" @click="submitResolution">确认裁决</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-stats { grid-template-columns: repeat(4, 1fr); }
@media (max-width: 575px) {
  .admin-stats { grid-template-columns: repeat(2, 1fr); }

  .el-table { font-size: 12px; }
}
</style>
