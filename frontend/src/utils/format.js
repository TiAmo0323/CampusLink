export const statusMap={RECRUITING:'招募中',WAIT_EXECUTE:'待执行',IN_PROGRESS:'进行中',WAIT_ACCEPTANCE:'待验收',COMPLETED:'已完成',CANCELLED:'已取消',ABNORMAL:'异常处理中',PENDING:'待处理',ACCEPTED:'已接受',REJECTED:'已拒绝',RESOLVED:'已解决',PROCESSING:'处理中',NORMAL:'正常',BANNED:'已禁用'}
export const statusText=value=>statusMap[value]||value||'-'
export const dateText=value=>value?new Date(value).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):'-'
export const scoreColor=score=>score>=85?'#2f7d68':score>=70?'#c78634':'#bf4c4c'
