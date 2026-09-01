import axios from 'axios'
import { ElMessage } from 'element-plus'

const http=axios.create({baseURL:import.meta.env.VITE_API_BASE||'',timeout:12000})
http.interceptors.request.use(config=>{const token=localStorage.getItem('campuslink_token');if(token)config.headers.Authorization=`Bearer ${token}`;return config})
http.interceptors.response.use(response=>{const body=response.data;if(body&&typeof body.code==='number'&&body.code!==0){if(body.code===401){localStorage.removeItem('campuslink_token');localStorage.removeItem('campuslink_user')}return Promise.reject(new Error(body.message||'请求失败'))}return body?.data},error=>{const message=error.response?.data?.message||error.message||'网络连接失败';if(error.response?.status===401){localStorage.removeItem('campuslink_token');localStorage.removeItem('campuslink_user')}ElMessage.error(message);return Promise.reject(error)})
export default http
