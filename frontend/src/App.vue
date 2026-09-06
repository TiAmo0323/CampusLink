<script setup>
import { computed,onMounted } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { ArrowLeft,House,Opportunity,List,Wallet,User,ChatDotRound,SwitchButton,DataAnalysis } from '@element-plus/icons-vue'
const auth=useAuthStore(),route=useRoute(),router=useRouter();const plain=computed(()=>['/login','/register'].includes(route.path));
const mobileNav=computed(()=>auth.isAdmin?nav.value:[{to:'/tasks',label:'任务',icon:House},{to:'/skills',label:'技能',icon:Opportunity},{to:'/tasks/new',label:'发布',icon:List},{to:'/messages',label:'消息',icon:ChatDotRound},{to:'/profile',label:'我的',icon:User}])
const backTarget=computed(()=>route.meta.backTo)
const nav=computed(()=>auth.isAdmin?[{to:'/admin',label:'管理台',icon:DataAnalysis},{to:'/tasks',label:'任务广场',icon:House},{to:'/profile',label:'账户',icon:User}]:[{to:'/tasks',label:'任务',icon:House},{to:'/mine',label:'我的',icon:List},{to:'/skills',label:'技能',icon:Opportunity},{to:'/wallet',label:'积分',icon:Wallet},{to:'/messages',label:'消息',icon:ChatDotRound}])
function goBack(){window.history.state?.back?router.back():router.push(backTarget.value)}
async function logout(){try{await auth.logout()}finally{router.push('/login')}}onMounted(()=>auth.refresh().catch(()=>{}))
</script>
<template>
  <router-view v-if="plain"/>
  <div v-else class="app-shell">
    <header class="topbar"><router-link class="brand" to="/tasks"><span class="brand-mark">C</span><span>CampusLink<small>让校园互助更可信</small></span></router-link><nav class="desktop-nav"><router-link v-for="item in nav" :key="item.to" :to="item.to"><el-icon><component :is="item.icon"/></el-icon>{{item.label}}</router-link></nav><div class="top-actions"><template v-if="auth.loggedIn"><router-link class="user-chip" to="/profile">{{auth.user?.nickname}}</router-link><el-button text circle @click="logout"><el-icon><SwitchButton/></el-icon></el-button></template><el-button v-else type="primary" @click="router.push('/login')">登录</el-button></div></header>
    <main class="page"><el-button v-if="backTarget" class="page-back" text @click="goBack"><el-icon><ArrowLeft/></el-icon><span>{{route.meta.backLabel||'返回'}}</span></el-button><router-view/></main>
    <nav v-if="auth.loggedIn" class="mobile-nav"><router-link v-for="item in mobileNav" :key="item.to" :to="item.to"><el-icon><component :is="item.icon"/></el-icon><span>{{item.label}}</span></router-link></nav>
  </div>
</template>

<style scoped>
.page-back{margin:0 0 18px;padding:0 4px;color:var(--green);font-weight:700}
.page-back .el-icon{font-size:18px}
@media(max-width:575px){.page-back{min-height:40px;margin-bottom:12px}}
</style>
