import { defineStore } from 'pinia'
import { computed,ref } from 'vue'
import http from '../api/http'

export const useAuthStore=defineStore('auth',()=>{
  const token=ref(localStorage.getItem('campuslink_token')||'')
  const user=ref(JSON.parse(localStorage.getItem('campuslink_user')||'null'))
  const loggedIn=computed(()=>Boolean(token.value));const isAdmin=computed(()=>user.value?.role==='ADMIN')
  function persist(data){token.value=data.token;user.value=data.user;localStorage.setItem('campuslink_token',data.token);localStorage.setItem('campuslink_user',JSON.stringify(data.user))}
  async function login(payload){persist(await http.post('/api/auth/login',payload))}
  async function register(payload){persist(await http.post('/api/auth/register',payload))}
  async function refresh(){if(!token.value)return;user.value=await http.get('/api/users/me');localStorage.setItem('campuslink_user',JSON.stringify(user.value))}
  function logout(){token.value='';user.value=null;localStorage.removeItem('campuslink_token');localStorage.removeItem('campuslink_user')}
  return{token,user,loggedIn,isAdmin,login,register,refresh,logout}
})
