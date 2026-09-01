<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'
import { statusText } from '../utils/format'

const auth = useAuthStore()
const tab = ref('match')
const skills = ref([])
const profile = ref({ offers: [], needs: [] })
const matches = ref([])
const exchanges = ref([])
const offer = reactive({ skillId: null, proficiency: 'INTERMEDIATE', description: '', availableMode: 'BOTH' })
const need = reactive({ skillId: null, priority: 1, description: '', preferredMode: 'BOTH' })
const dialog = ref(false)
const exchange = reactive({ providerId: null, requestSkillId: null, exchangeSkillId: null, message: '', scheduledTime: '' })
const myOfferOptions = computed(() => profile.value.offers.map((item) => item.skill))

async function load() {
  [skills.value, profile.value, matches.value, exchanges.value] = await Promise.all([
    http.get('/api/public/skills'),
    http.get('/api/skills/profile'),
    http.get('/api/skills/matches'),
    http.get('/api/exchanges')
  ])
}

async function save(type, payload) {
  await http.post(`/api/skills/${type}`, payload)
  ElMessage.success('技能档案已更新')
  await load()
}

async function remove(type, id) {
  await http.delete(`/api/skills/${type}/${id}`)
  ElMessage.success('已删除')
  await load()
}

function openExchange(match) {
  exchange.providerId = match.user.id
  exchange.requestSkillId = match.matchedSkills[0]?.id
  exchange.exchangeSkillId = null
  exchange.message = '希望和你约时间互相学习'
  exchange.scheduledTime = ''
  dialog.value = true
}

async function submitExchange() {
  await http.post('/api/exchanges', exchange)
  ElMessage.success('互助申请已发送')
  dialog.value = false
  await load()
}

async function action(id, name) {
  await http.post(`/api/exchanges/${id}/${name}`)
  ElMessage.success('状态已更新')
  await load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <div><h1>技能互助</h1><p>基于供需覆盖、信用、评价与活跃度的可解释推荐</p></div>
    </div>
    <section class="surface panel">
      <el-tabs v-model="tab">
        <el-tab-pane label="匹配推荐" name="match">
          <div class="grid">
            <article v-for="match in matches" :key="match.user.id" class="task-card surface" style="box-shadow:none">
              <div class="card-top"><strong>{{ match.user.nickname }}</strong><el-tag v-if="match.mutualMatch" type="success">双向互补</el-tag></div>
              <div style="display:flex;align-items:baseline;gap:5px;margin:18px 0"><span class="points" style="font-size:34px">{{ match.score }}</span><span class="muted">匹配分</span></div>
              <p class="muted">{{ match.reason }}</p>
              <div><el-tag v-for="item in match.matchedSkills" :key="item.id" style="margin:0 6px 6px 0" effect="plain">{{ item.name }}</el-tag></div>
              <el-button type="primary" plain class="full" style="margin-top:12px" @click="openExchange(match)">发起技能互助</el-button>
            </article>
          </div>
          <div v-if="!matches.length" class="empty">先在“我的技能档案”中添加技能需求，才能获得推荐</div>
        </el-tab-pane>

        <el-tab-pane label="我的技能档案" name="profile">
          <div class="two-col">
            <section>
              <h3>我能提供</h3>
              <div v-for="item in profile.offers" :key="item.record.id" class="list-card" style="background:#f5f7f3">
                <strong>{{ item.skill.name }}</strong><span class="muted"> · {{ item.record.proficiency }}</span>
                <p>{{ item.record.description || '暂无介绍' }}</p>
                <el-button text type="danger" @click="remove('offers', item.record.id)">删除</el-button>
              </div>
              <el-divider />
              <el-form label-position="top">
                <el-form-item label="技能"><el-select v-model="offer.skillId" class="full"><el-option v-for="item in skills" :key="item.id" :label="`${item.category} / ${item.name}`" :value="item.id" /></el-select></el-form-item>
                <el-form-item label="熟练程度"><el-select v-model="offer.proficiency" class="full"><el-option label="入门" value="BEGINNER" /><el-option label="熟练" value="INTERMEDIATE" /><el-option label="精通" value="ADVANCED" /></el-select></el-form-item>
                <el-form-item label="技能介绍"><el-input v-model="offer.description" /></el-form-item>
                <el-button type="primary" @click="save('offers', offer)">保存供给</el-button>
              </el-form>
            </section>
            <section>
              <h3>我想学习</h3>
              <div v-for="item in profile.needs" :key="item.record.id" class="list-card" style="background:#f5f7f3">
                <strong>{{ item.skill.name }}</strong><span class="muted"> · 优先级 {{ item.record.priority }}</span>
                <p>{{ item.record.description || '暂无说明' }}</p>
                <el-button text type="danger" @click="remove('needs', item.record.id)">删除</el-button>
              </div>
              <el-divider />
              <el-form label-position="top">
                <el-form-item label="技能"><el-select v-model="need.skillId" class="full"><el-option v-for="item in skills" :key="item.id" :label="`${item.category} / ${item.name}`" :value="item.id" /></el-select></el-form-item>
                <el-form-item label="优先级"><el-slider v-model="need.priority" :min="1" :max="5" show-stops /></el-form-item>
                <el-form-item label="需求说明"><el-input v-model="need.description" /></el-form-item>
                <el-button type="primary" @click="save('needs', need)">保存需求</el-button>
              </el-form>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="互助订单" name="exchange">
          <article v-for="item in exchanges" :key="item.exchange.id" class="list-card" style="background:#f5f7f3">
            <div style="display:flex;justify-content:space-between"><strong>{{ item.requester.nickname }} → {{ item.provider.nickname }}</strong><span class="status-pill">{{ statusText(item.exchange.status) }}</span></div>
            <p>请求技能：{{ item.requestSkill.name }} <span v-if="item.exchangeSkill">· 交换 {{ item.exchangeSkill.name }}</span></p>
            <p class="muted">{{ item.exchange.message }}</p>
            <div class="action-row">
              <template v-if="item.exchange.status === 'PENDING' && item.exchange.providerId === auth.user?.id">
                <el-button size="small" type="success" @click="action(item.exchange.id, 'accept')">接受</el-button>
                <el-button size="small" @click="action(item.exchange.id, 'reject')">拒绝</el-button>
              </template>
              <el-button v-if="item.exchange.status === 'ACCEPTED'" size="small" type="primary" @click="action(item.exchange.id, 'start')">开始互助</el-button>
              <el-button v-if="item.exchange.status === 'IN_PROGRESS'" size="small" type="primary" @click="action(item.exchange.id, 'complete')">标记完成</el-button>
            </div>
          </article>
          <div v-if="!exchanges.length" class="empty">暂无技能互助申请</div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="dialog" title="发起技能互助" width="500">
      <el-form label-position="top">
        <el-form-item label="希望对方提供"><el-select v-model="exchange.requestSkillId" class="full"><el-option v-for="item in skills" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="我可以交换（可选）"><el-select v-model="exchange.exchangeSkillId" clearable class="full"><el-option v-for="item in myOfferOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="申请说明"><el-input v-model="exchange.message" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="约定时间"><el-date-picker v-model="exchange.scheduledTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="submitExchange">发送申请</el-button></template>
    </el-dialog>
  </div>
</template>
