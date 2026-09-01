import { createRouter,createWebHashHistory,createWebHistory } from 'vue-router'
import { Capacitor } from '@capacitor/core'

const routes=[
  {path:'/',redirect:'/tasks'},
  {path:'/login',component:()=>import('../views/LoginView.vue'),meta:{guest:true}},
  {path:'/register',component:()=>import('../views/RegisterView.vue'),meta:{guest:true}},
  {path:'/tasks',component:()=>import('../views/TaskListView.vue')},
  {path:'/tasks/new',component:()=>import('../views/TaskPublishView.vue'),meta:{auth:true,backTo:'/tasks',backLabel:'返回任务广场'}},
  {path:'/tasks/:id',component:()=>import('../views/TaskDetailView.vue'),meta:{backTo:'/tasks',backLabel:'返回任务广场'}},
  {path:'/mine',component:()=>import('../views/MyTasksView.vue'),meta:{auth:true}},
  {path:'/orders/:id',component:()=>import('../views/OrderDetailView.vue'),meta:{auth:true,backTo:'/mine',backLabel:'返回我的任务'}},
  {path:'/skills',component:()=>import('../views/SkillsView.vue'),meta:{auth:true}},
  {path:'/wallet',component:()=>import('../views/WalletView.vue'),meta:{auth:true}},
  {path:'/messages',component:()=>import('../views/MessagesView.vue'),meta:{auth:true}},
  {path:'/profile',component:()=>import('../views/ProfileView.vue'),meta:{auth:true}},
  {path:'/admin',component:()=>import('../views/AdminView.vue'),meta:{auth:true,admin:true}}
]
const history=Capacitor.isNativePlatform()?createWebHashHistory():createWebHistory()
const router=createRouter({history,routes,scrollBehavior:()=>({top:0})})
router.beforeEach(to=>{const token=localStorage.getItem('campuslink_token'),user=JSON.parse(localStorage.getItem('campuslink_user')||'null');if(to.meta.auth&&!token)return{path:'/login',query:{redirect:to.fullPath}};if(to.meta.admin&&user?.role!=='ADMIN')return'/tasks';if(to.meta.guest&&token)return'/tasks'})
export default router
