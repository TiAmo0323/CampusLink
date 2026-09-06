<script setup>
import { ref,reactive } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
const props=defineProps({targetType:String,targetId:Number})
const visible=ref(false),busy=ref(false),form=reactive({reasonType:'OTHER',description:''})
async function submit(){if(!form.description.trim()){ElMessage.warning('请填写具体说明');return}busy.value=true;try{await http.post('/api/reports',{targetType:props.targetType,targetId:props.targetId,...form});ElMessage.success('举报已提交');visible.value=false;form.description=''}finally{busy.value=false}}
</script>
<template><el-button text type="warning" @click="visible=true">举报</el-button><el-dialog v-model="visible" title="提交举报" width="460"><el-form label-position="top"><el-form-item label="原因"><el-select v-model="form.reasonType" class="full"><el-option value="SPAM" label="垃圾信息"/><el-option value="VIOLATION" label="违规内容"/><el-option value="OTHER" label="其他"/></el-select></el-form-item><el-form-item label="具体说明"><el-input v-model="form.description" type="textarea" maxlength="500" :rows="4"/></el-form-item></el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="busy" @click="submit">提交举报</el-button></template></el-dialog></template>

