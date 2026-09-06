import { test,expect } from '@playwright/test'
const api=process.env.API_TEST_URL||'http://127.0.0.1:18081'
if(new URL(api).hostname!=='127.0.0.1')throw new Error('Tests require an isolated localhost backend')
async function login(page,account='alice'){
 await page.goto('/login');await page.getByPlaceholder('请输入账号').fill(account)
 await page.locator('input[type=password]').fill('demo123');await page.getByRole('button',{name:'登录',exact:true}).click()
 await expect(page.getByRole('heading',{name:'任务广场',exact:true})).toBeVisible()
}
async function noOverflow(page){await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true)}
async function apiSession(request,account){const r=await request.post(api+'/api/auth/login',{data:{account,password:'demo123'}});expect(r.ok()).toBeTruthy();return (await r.json()).data.token}
async function post(request,path,token,data){const r=await request.post(api+path,{headers:{Authorization:'Bearer '+token},data});const body=await r.json();expect(body.code,JSON.stringify(body)).toBe(200);return body.data}

test('UI-M01/M02/M03/M08: 登录、筛选、移动发布和个人中心',async({page})=>{
 await login(page);await noOverflow(page)
 await page.getByPlaceholder('搜索任务、地点或关键词').fill('不存在的测试关键词');await page.getByRole('button',{name:'筛选',exact:true}).filter({visible:true}).click();await page.getByRole('button',{name:'应用筛选',exact:true}).click();await expect(page.getByText('暂时没有符合条件的任务，换个条件试试')).toBeVisible()
 await page.goto('/tasks/new');await expect(page.getByRole('heading',{name:'发布微任务',exact:true})).toBeVisible();await noOverflow(page)
 await page.getByPlaceholder('例如：帮忙拍摄社团招新照片').fill('UI移动发布-'+Date.now())
 await page.locator('textarea').fill('在移动终端提交的验收测试任务')
 const dates=page.locator('.el-date-editor input')
 const future=(days)=>{const d=new Date(Date.now()+days*86400000);return d.toISOString().slice(0,10)+' 12:00:00'}
 await dates.nth(0).fill(future(2));await dates.nth(0).press('Enter');await dates.nth(1).fill(future(3));await dates.nth(1).press('Enter')
 await page.getByRole('button',{name:'确认发布',exact:true}).click();await expect(page).toHaveURL(/\/tasks\/\d+$/)
 await page.setViewportSize({width:360,height:800});await page.goto('/profile');await expect(page.getByRole('heading',{name:'个人中心',exact:true})).toBeVisible();await noOverflow(page)
 for(const text of ['我的任务 / 申请','我的技能','积分流水']){const b=page.getByRole('button',{name:text,exact:true});await expect(b).toBeVisible();const box=await b.boundingBox();expect(box.y+box.height).toBeLessThan(800)}
 await page.getByRole('button',{name:'我的任务 / 申请',exact:true}).click();await expect(page.getByRole('tab',{name:'我申请的'})).toBeVisible()
 await page.goto('/wallet');await noOverflow(page)
})

test('UI-M04/M05/M06: 手机申请、开始、上传、驳回重提与验收',async({browser,request})=>{
 const token=await apiSession(request,'alice')
 const task=await post(request,'/api/tasks',token,{title:'手机履约-'+Date.now(),category:'校园活动',description:'移动端验收完整闭环',taskTime:new Date(Date.now()+5*86400000).toISOString().slice(0,19),applicationDeadline:new Date(Date.now()+4*86400000).toISOString().slice(0,19),rewardPoints:10,minCreditScore:0,estimatedDurationMinutes:60})
 const bc=await browser.newContext({viewport:{width:412,height:915},isMobile:true,hasTouch:true}),bob=await bc.newPage();await login(bob,'bob')
 await bob.goto('/tasks/'+task.id);await bob.getByRole('button',{name:'申请接取',exact:true}).click();await bob.locator('.el-dialog textarea').fill('可以完成');await bob.getByRole('button',{name:'提交申请',exact:true}).click();await expect(bob.getByText('申请已提交',{exact:true})).toBeVisible()
 const ar=await request.get(api+'/api/tasks/'+task.id+'/applications',{headers:{Authorization:'Bearer '+token}});const application=(await ar.json()).data.records[0].application
 const ac=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true}),alice=await ac.newPage();await login(alice);await alice.goto('/tasks/'+task.id)
 alice.once('dialog',d=>d.accept());await alice.getByRole('button',{name:'选择 TA',exact:true}).click();await alice.getByRole('button',{name:'确定',exact:true}).click();await expect(alice).toHaveURL(/\/orders\/\d+$/);const orderUrl=alice.url()
 await bob.goto(orderUrl);await bob.getByRole('button',{name:'开始任务',exact:true}).click();await bob.getByRole('button',{name:'提交完成结果',exact:true}).click()
 await bob.locator('.el-dialog textarea').fill('成果已完成')
 await bob.locator('input[type=file]').setInputFiles({name:'proof.png',mimeType:'image/png',buffer:Buffer.from(await bob.evaluate(()=>{const canvas=document.createElement('canvas');canvas.width=1;canvas.height=1;return canvas.toDataURL('image/png').split(',')[1]}),'base64')})
 await expect(bob.getByText('凭证已上传',{exact:true})).toBeVisible();await bob.getByRole('button',{name:'提交验收',exact:true}).click();await noOverflow(bob)
 await alice.reload();await alice.getByRole('button',{name:'驳回重提',exact:true}).click();await alice.locator('.el-message-box textarea').fill('请补充一项说明');await alice.getByRole('button',{name:'确定',exact:true}).click()
 await bob.reload();await bob.getByRole('button',{name:'提交完成结果',exact:true}).click();await bob.locator('.el-dialog textarea').fill('已补充说明');await bob.getByRole('button',{name:'提交验收',exact:true}).click()
 await alice.reload();await alice.getByRole('button',{name:'验收通过',exact:true}).click();await expect(alice.getByRole('button',{name:'评价对方',exact:true})).toBeVisible();await noOverflow(alice)
 await bc.close();await ac.close()
})


test('UI-M01: 手机注册并自动进入首页',async({page})=>{
 await page.setViewportSize({width:390,height:844});await page.goto('/register');await noOverflow(page)
 const inputs=page.locator('input');const suffix=Date.now().toString()
 await inputs.nth(0).fill('mobile'+suffix);await inputs.nth(1).fill('手机注册测试');await inputs.nth(2).fill('demo123');await inputs.nth(3).fill(suffix)
 await page.getByRole('button',{name:'注册并进入',exact:true}).click()
 await expect(page).toHaveURL(/\/tasks$/);await expect(page.getByRole('heading',{name:'任务广场',exact:true})).toBeVisible()
 await page.goto('/profile');await expect(page.getByText('200',{exact:true}).first()).toBeVisible();await noOverflow(page)
})

test('UI-M07: 匹配分页卡片与发起互助',async({page})=>{
 await login(page);await page.goto('/skills');await expect(page.getByRole('tab',{name:'匹配推荐'})).toBeVisible();await expect(page.getByRole('button',{name:'发起技能互助',exact:true}).first()).toBeVisible();await noOverflow(page)
 await page.getByRole('button',{name:'发起技能互助',exact:true}).first().click();await page.getByRole('button',{name:'发送申请',exact:true}).click();await expect(page.getByText('互助申请已发送',{exact:true})).toBeVisible()
})
for(const width of [360,390,412,1920])test('UI-P01/UI-R01: '+width+'px 无横向溢出',async({page})=>{
 await login(page);await page.setViewportSize({width,height:width===1920?1080:844})
 for(const path of ['/tasks','/tasks/new','/mine','/skills','/wallet','/messages','/profile']){await page.goto(path);await page.locator('h1').first().waitFor();await noOverflow(page)}
 await page.setViewportSize({width:390,height:844});await noOverflow(page)
})
