<script setup>
import { onMounted,reactive,ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'
import { assetUrl } from '../utils/url'
import { statusText } from '../utils/format'
const auth=useAuthStore(),form=reactive({nickname:'',avatarUrl:'',bio:''}),reports=ref({records:[],total:0}),page=ref(1)
async function loadReports(){reports.value=await http.get('/api/reports/mine',{params:{page:page.value,size:10}})}
onMounted(async()=>{await auth.refresh();Object.assign(form,{nickname:auth.user.nickname,avatarUrl:auth.user.avatarUrl||'',bio:auth.user.bio||''});await loadReports()})
async function uploadAvatar(option){const fd=new FormData();fd.append('file',option.file);try{const result=await http.post('/api/files/images',fd);form.avatarUrl=result.url;option.onSuccess(result);ElMessage.success('头像已上传，请保存修改')}catch(e){option.onError(e)}}
async function save(){await http.put('/api/users/me',form);await auth.refresh();ElMessage.success('个人资料已保存')}
</script>
<template><div><div class="page-head"><h1>个人中心</h1></div>
 <section class="surface panel profile-summary"><el-avatar :size="52" :src="assetUrl(form.avatarUrl)">{{auth.user?.nickname?.slice(0,1)}}</el-avatar><strong>{{auth.user?.nickname}}</strong><div class="stat-grid"><div class="stat"><strong>{{auth.user?.availablePoints}}</strong><span>可用积分</span></div><div class="stat"><strong>{{auth.user?.frozenPoints}}</strong><span>冻结积分</span></div><div class="stat"><strong>{{auth.user?.creditScore}}</strong><span>信用</span></div></div><div class="action-row"><el-button @click="$router.push('/mine')">我的任务 / 申请</el-button><el-button @click="$router.push('/skills')">我的技能</el-button><el-button @click="$router.push('/wallet')">积分流水</el-button></div></section>
 <div class="two-col" style="margin-top:20px"><section class="surface panel"><h3>基本资料</h3><el-form label-position="top"><el-form-item label="昵称"><el-input v-model="form.nickname"/></el-form-item><el-form-item label="头像（JPG/PNG，最多5MB）"><el-upload :http-request="uploadAvatar" :show-file-list="false" accept=".jpg,.jpeg,.png"><el-button>上传头像</el-button></el-upload></el-form-item><el-form-item label="个人简介"><el-input v-model="form.bio" type="textarea" :rows="3" maxlength="255"/></el-form-item><el-button type="primary" @click="save">保存修改</el-button></el-form></section>
 <section class="surface panel"><h3>我的举报与申诉</h3><article v-for="r in reports.records" :key="r.id" class="list-card"><el-tag>{{statusText(r.status)}}</el-tag><p>{{r.description}}</p><p v-if="r.handleResult">处理结果：{{r.handleResult}}</p></article><div v-if="!reports.records.length" class="muted">暂无举报</div><el-pagination v-model:current-page="page" :total="reports.total" :page-size="10" layout="prev,pager,next" @current-change="loadReports"/></section></div>
</div></template>
<style scoped>.profile-summary{display:grid;gap:12px}.profile-summary>.action-row{margin-top:6px}</style>
