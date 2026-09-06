<script setup>
import { computed,onMounted,reactive,ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const router=useRouter(),auth=useAuthStore(),loading=ref(false),formRef=ref()
const form=reactive({title:'',category:'校园活动',description:'',location:'',taskTime:'',applicationDeadline:'',rewardPoints:20,minCreditScore:60,estimatedDurationMinutes:60})
const categories=['校园活动','课程辅导','生活服务','物品代取','创意设计','其他']
const availablePoints=computed(()=>auth.user?.availablePoints??0)
const future=(rule,value,done)=>{if(!value)return done(new Error('请选择时间'));if(new Date(value)<=new Date())return done(new Error('时间必须晚于当前时间'));done()}
const taskAfterDeadline=(rule,value,done)=>{if(!value)return done(new Error('请选择任务时间'));if(form.applicationDeadline&&new Date(value)<=new Date(form.applicationDeadline))return done(new Error('任务时间必须晚于申请截止时间'));done()}
const rewardAvailable=(rule,value,done)=>{if(value>availablePoints.value)return done(new Error(`可用积分不足，当前仅有 ${availablePoints.value} 分`));done()}
const rules={
 title:[{required:true,message:'请填写任务标题',trigger:'blur'},{min:2,max:100,message:'标题长度为 2 至 100 字',trigger:'blur'}],
 category:[{required:true,message:'请选择任务分类',trigger:'change'}],
 description:[{required:true,message:'请填写详细描述',trigger:'blur'}],
 applicationDeadline:[{validator:future,trigger:'change'}],
 taskTime:[{validator:taskAfterDeadline,trigger:'change'}],
 rewardPoints:[{validator:rewardAvailable,trigger:'change'}]
}
async function submit(){try{await formRef.value.validate()}catch{return}loading.value=true;try{const data=await http.post('/api/tasks',form);await auth.refresh();ElMessage.success('任务已发布，奖励积分已冻结');router.replace(`/tasks/${data.id}`)}catch(e){ElMessage.error(e.message)}finally{loading.value=false}}
onMounted(()=>auth.refresh())
</script>
<template><div style="max-width:780px;margin:auto"><div class="page-head"><div><h1>发布微任务</h1><p>把任务说清楚，让合适的同学更快找到你</p></div></div><section class="surface panel"><el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item prop="title" label="任务标题"><el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：帮忙拍摄社团招新照片"/></el-form-item><el-form-item prop="category" label="任务分类"><el-select v-model="form.category" class="full"><el-option v-for="c in categories" :key="c" :value="c"/></el-select></el-form-item><el-form-item prop="description" label="详细描述"><el-input v-model="form.description" type="textarea" :rows="5" maxlength="1000" show-word-limit/></el-form-item><el-form-item label="地点"><el-input v-model="form.location" placeholder="线上任务可填写“线上”"/></el-form-item><div class="date-grid" style="display:grid;grid-template-columns:1fr 1fr;gap:16px"><el-form-item prop="applicationDeadline" label="申请截止"><el-date-picker v-model="form.applicationDeadline" class="full" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/></el-form-item><el-form-item prop="taskTime" label="任务时间"><el-date-picker v-model="form.taskTime" class="full" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/></el-form-item></div><div class="date-grid" style="display:grid;grid-template-columns:1fr 1fr;gap:16px"><el-form-item label="预计时长（分钟）"><el-input-number v-model="form.estimatedDurationMinutes" :min="1" :max="10080" class="full"/></el-form-item><el-form-item prop="rewardPoints" label="奖励积分"><el-input-number v-model="form.rewardPoints" :min="1" :max="1000" class="full"/></el-form-item><el-form-item label="最低信用分"><el-input-number v-model="form.minCreditScore" :min="0" :max="100" class="full"/></el-form-item></div><el-alert :closable="false" type="info" :title="`当前可用积分 ${availablePoints}；发布后奖励积分将被冻结，验收后结算给接取者。`"/><div style="display:flex;justify-content:flex-end;gap:10px;margin-top:22px"><el-button @click="router.back()">取消</el-button><el-button type="primary" :loading="loading" @click="submit">确认发布</el-button></div></el-form></section></div></template>
<style scoped>@media(max-width:575px){.date-grid{grid-template-columns:1fr!important}}</style>
