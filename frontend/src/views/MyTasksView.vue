<script setup>
import { onMounted,ref,watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
import http from '../api/http'
import { dateText,statusText } from '../utils/format'
const router=useRouter(),active=ref('published'),data=ref({records:[],total:0}),page=ref(1),loading=ref(false)
async function load(){loading.value=true;try{data.value=await http.get('/api/tasks/mine',{params:{kind:active.value,page:page.value,size:10}})}finally{loading.value=false}}
watch(active,()=>{page.value=1;load()})
function task(item){return active.value==='published'?item:item.task}
function status(item){return item.order?.status||item.application?.status||item.status}
function open(item){const id=item.order?.id||item.orderId;router.push(id?'/orders/'+id:'/tasks/'+task(item).id)}
async function withdraw(item){await ElMessageBox.confirm('撤回后保留记录，不能重复申请同一任务。','撤回申请');await http.post('/api/applications/'+item.application.id+'/withdraw');ElMessage.success('申请已撤回');await load()}
onMounted(load)
</script>
<template><div>
 <div class="page-head"><div><h1>我的任务</h1><p>发布、申请与履约进度</p></div><el-button type="primary" @click="router.push('/tasks/new')">发布新任务</el-button></div>
 <section class="surface panel" v-loading="loading">
  <el-tabs v-model="active"><el-tab-pane label="我发布的" name="published"/><el-tab-pane label="我申请的" name="applied"/><el-tab-pane label="我接取的" name="accepted"/></el-tabs>
  <article v-for="item in data.records" :key="item.id||item.order?.id||item.application?.id" class="list-card task-row">
   <div><el-tag size="small">{{task(item).category}}</el-tag><h3>{{task(item).title}}</h3><p class="muted">{{dateText(task(item).taskTime)}} · {{task(item).rewardPoints}} 积分</p></div>
   <div><span class="status-pill">{{statusText(status(item))}}</span><div class="action-row" style="margin-top:12px"><el-button @click="open(item)">查看详情</el-button><el-button v-if="active==='applied'&&item.application.status==='PENDING'" type="danger" plain @click="withdraw(item)">撤回申请</el-button></div></div>
  </article>
  <div v-if="!data.records.length" class="empty">暂无记录</div>
  <el-pagination v-model:current-page="page" :page-size="10" :total="data.total" layout="prev,pager,next" @current-change="load"/>
 </section>
</div></template>
<style scoped>.task-row{background:#f5f7f3;display:flex;justify-content:space-between;gap:16px;align-items:center}@media(max-width:575px){.task-row{align-items:stretch;flex-direction:column}}</style>
