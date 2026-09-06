import axios from 'axios'
import { ElMessage } from 'element-plus'
const options={baseURL:import.meta.env.VITE_API_BASE||'',timeout:12000}
const http=axios.create(options), sessionClient=axios.create(options)
let refreshing=null
export function saveSession(data){
  localStorage.setItem('campuslink_token',data.token)
  localStorage.setItem('campuslink_refresh',data.refreshToken)
  localStorage.setItem('campuslink_user',JSON.stringify(data.user))
  window.dispatchEvent(new Event('campuslink-session'))
}
export function clearSession(){
  for(const key of ['campuslink_token','campuslink_refresh','campuslink_user'])localStorage.removeItem(key)
  window.dispatchEvent(new Event('campuslink-session'))
}
http.interceptors.request.use(config=>{
  const token=localStorage.getItem('campuslink_token')
  if(token)config.headers.Authorization='Bearer '+token
  return config
})
http.interceptors.response.use(response=>{
  const body=response.data
  if(body&&typeof body.code==='number'&&body.code!==200)throw new Error(body.message||'请求失败')
  return body?.data
},async error=>{
  const config=error.config
  if(error.response?.status===401&&config&&!config._retry&&!config.url.startsWith('/api/auth/')){
    const refreshToken=localStorage.getItem('campuslink_refresh')
    if(refreshToken){
      config._retry=true
      try{
        if(!refreshing)refreshing=sessionClient.post('/api/auth/refresh',{refreshToken}).then(r=>{
          if(r.data.code!==200)throw new Error(r.data.message)
          saveSession(r.data.data)
        }).finally(()=>{refreshing=null})
        await refreshing
        return http(config)
      }catch(refreshError){
        if([400,401,403].includes(refreshError.response?.status))clearSession()
        return Promise.reject(refreshError)
      }
    }
    clearSession()
  }
  const message=error.response?.data?.message||error.message||'网络连接失败'
  ElMessage.error(message)
  return Promise.reject(new Error(message))
})
export default http
