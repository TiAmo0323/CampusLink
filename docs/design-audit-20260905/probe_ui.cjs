const {chromium}=require(process.cwd()+'/frontend/node_modules/@playwright/test');
const fs=require('fs');
const base='http://127.0.0.1:18089',ui='http://127.0.0.1:15189';
async function api(path,token,method='GET',data){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data===undefined?undefined:JSON.stringify(data)});return {status:r.status,body:await r.json()}}
(async()=>{
 const result=[];
 const session=(await api('/api/auth/login',null,'POST',{account:'admin',password:'admin123'})).body.data;
 const browser=await chromium.launch({channel:'chrome',headless:true});
 const context=await browser.newContext({viewport:{width:1920,height:1080}});
 await context.addInitScript(s=>{localStorage.setItem('campuslink_token',s.token);localStorage.setItem('campuslink_refresh',s.refreshToken);localStorage.setItem('campuslink_user',JSON.stringify(s.user))},session);
 const page=await context.newPage();await page.goto(ui+'/admin');
 await page.getByRole('tab',{name:'技能分类',exact:true}).click();
 const panel=page.getByRole('tabpanel',{name:'技能分类',exact:true});
 const row=panel.locator('.el-table__body tr').first();await row.waitFor();
 const skills=(await api('/api/admin/skills',session.token)).body.data;
 const skill=skills.records[0];
 const before=await api('/api/public/tasks/'+skill.id);
 await page.screenshot({path:'backend/target/design-audit/admin-skill-before.png',fullPage:true});
 await row.getByRole('button',{name:'下架',exact:true}).click();
 await page.locator('.el-message-box input').fill('isolated audit wrong target');
 const response=page.waitForResponse(r=>r.url().includes('/delist'));
 await page.getByRole('button',{name:'确定',exact:true}).click();
 const changed=await response;
 const after=await api('/api/public/tasks/'+skill.id);
 const still=(await api('/api/admin/skills',session.token)).body.data.records.find(s=>s.id===skill.id);
 result.push({name:'skill_delist_calls_task_api',skillId:skill.id,skillName:skill.name,request:changed.url(),http:changed.status(),taskBefore:before.status,taskAfter:after.status,skillStatusAfter:still.status});
 const alice=(await api('/api/auth/login',null,'POST',{account:'alice',password:'demo123'})).body.data;
 const published=(await api('/api/tasks/mine?kind=published',alice.token)).body.data.records;
 const task=published.find(t=>t.title==='audit-boundaries');
 const detail=await api('/api/orders/'+task.orderId,session.token);
 result.push({name:'admin_cannot_view_order_evidence',http:detail.status,body:detail.body});
 await browser.close();
 fs.writeFileSync('backend/target/design-audit/ui-results.json',JSON.stringify(result,null,2));
 console.log(JSON.stringify(result,null,2));
})().catch(e=>{console.error(e);process.exit(1)});

