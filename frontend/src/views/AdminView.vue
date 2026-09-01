<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { dateText, statusText } from '../utils/format'

const stats = ref({})
const tab = ref('users')
const users = ref({ records: [] })
const tasks = ref({ records: [] })
const reports = ref({ records: [] })
const skills = ref([])
const query = reactive({ page: 1, size: 20 })
const skill = reactive({ name: '', category: '' })
const resolveDialog = ref(false)
const resolvingId = ref(null)
const resolution = reactive({ decision: 'RESUME', liableUserId: null, creditDelta: 0, result: '' })

async function load() {
  [stats.value, users.value, tasks.value, reports.value, skills.value] = await Promise.all([
    http.get('/api/admin/dashboard'),
    http.get('/api/admin/users', { params: query }),
    http.get('/api/admin/tasks', { params: query }),
    http.get('/api/admin/reports', { params: query }),
    http.get('/api/admin/skills')
  ])
}

async function userStatus(user) {
  const next = user.status === 'NORMAL' ? 'BANNED' : 'NORMAL'
  await http.put(`/api/admin/users/${user.id}/status/${next}`)
  ElMessage.success('用户状态已更新')
  await load()
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
  try {
    const { value } = await ElMessageBox.prompt('输入举报处理结论', '处理举报', {
      inputType: 'textarea',
      inputValidator: (input) => input?.length > 3 || '请填写结论'
    })
    await http.post(`/api/admin/reports/${report.id}/handle`, { result: value })
    ElMessage.success('举报处理完成')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error
  }
}

async function addSkill() {
  await http.post('/api/admin/skills', skill)
  skill.name = ''
  skill.category = ''
  ElMessage.success('技能分类已添加')
  await load()
}

async function skillStatus(item) {
  const next = item.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await http.put(`/api/admin/skills/${item.id}/status/${next}`)
  ElMessage.success('技能状态已更新')
  await load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head"><div><h1>平台管理台</h1><p>账户、任务、技能分类与举报治理</p></div></div>
    <div class="stat-grid admin-stats" style="margin-bottom:20px">
      <div class="stat surface"><strong>{{ stats.users || 0 }}</strong><span>学生用户</span></div>
      <div class="stat surface"><strong>{{ stats.recruitingTasks || 0 }}</strong><span>招募任务</span></div>
      <div class="stat surface"><strong>{{ stats.activeTasks || 0 }}</strong><span>履约中</span></div>
      <div class="stat surface"><strong>{{ stats.completedTasks || 0 }}</strong><span>已完成</span></div>
      <div class="stat surface"><strong>{{ stats.pendingReports || 0 }}</strong><span>待处理举报</span></div>
    </div>

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
            <el-table-column prop="category" label="分类" /><el-table-column prop="rewardPoints" label="积分" width="80" />
            <el-table-column label="状态"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column>
            <el-table-column label="发布时间" min-width="150"><template #default="{ row }">{{ dateText(row.createdAt) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="举报治理" name="reports">
          <article v-for="report in reports.records" :key="report.id" class="list-card" style="background:#f5f7f3">
            <div style="display:flex;justify-content:space-between"><strong>{{ report.previousStatus ? '异常任务' : '普通举报' }} #{{ report.id }}</strong><el-tag>{{ statusText(report.status) }}</el-tag></div>
            <p>{{ report.reasonType }} · {{ report.description }}</p>
            <p v-if="report.handleResult" class="muted">处理结果：{{ report.handleResult }}</p>
            <el-button v-if="report.status !== 'RESOLVED'" size="small" type="primary" @click="report.previousStatus ? openResolution(report) : handleReport(report)">进入处理</el-button>
          </article>
          <div v-if="!reports.records.length" class="empty">暂无举报</div>
        </el-tab-pane>

        <el-tab-pane label="技能分类" name="skills">
          <div class="filter-bar" style="padding:0"><el-input v-model="skill.name" placeholder="技能名称" /><el-input v-model="skill.category" placeholder="所属分类" /><el-button type="primary" @click="addSkill">添加</el-button></div>
          <el-table :data="skills">
            <el-table-column prop="name" label="技能" /><el-table-column prop="category" label="分类" /><el-table-column prop="status" label="状态" />
            <el-table-column label="操作"><template #default="{ row }"><el-button text @click="skillStatus(row)">{{ row.status==='ENABLED'?'停用':'启用' }}</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

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
.admin-stats { grid-template-columns: repeat(5, 1fr); }
@media (max-width: 575px) {
  .admin-stats { grid-template-columns: repeat(2, 1fr); }
  .admin-stats .stat:last-child { grid-column: span 2; }
  .el-table { font-size: 12px; }
}
</style>
