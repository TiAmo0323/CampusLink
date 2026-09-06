import { defineStore } from 'pinia'
import { computed,ref } from 'vue'
import http,{saveSession,clearSession} from '../api/http'
export const useAuthStore=defineStore('auth',()=>{
  const token=ref(''),user=ref(null)
  function sync(){token.value=localStorage.getItem('campuslink_token')||'';try{user.value=JSON.parse(localStorage.getItem('campuslink_user')||'null')}catch{user.value=null}}
  sync();window.addEventListener('campuslink-session',sync)
  const loggedIn=computed(()=>Boolean(token.value)),isAdmin=computed(()=>user.value?.role==='ADMIN')
  async function login(payload){saveSession(await http.post('/api/auth/login',payload))}
  async function register(payload){saveSession(await http.post('/api/auth/register',payload))}
  async function refresh(){if(!token.value)return;user.value=await http.get('/api/users/me');localStorage.setItem('campuslink_user',JSON.stringify(user.value))}
  async function logout(){const refreshToken=localStorage.getItem('campuslink_refresh');try{if(refreshToken)await http.post('/api/auth/logout',{refreshToken})}finally{clearSession()}}
  return{token,user,loggedIn,isAdmin,login,register,refresh,logout}
})
