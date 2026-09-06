"""Non-destructive transaction regression; ONLY disposable localhost port 18081."""
import json,urllib.request,datetime,concurrent.futures
API='http://127.0.0.1:18081'
def request(path,token=None,data=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(API+path,headers=headers,data=None if data is None else json.dumps(data).encode())
 with urllib.request.urlopen(req,timeout=15) as response:result=json.load(response)
 assert result['code']==200,result
 return result.get('data')
def login(name):return request('/api/auth/login',data={'account':name,'password':'demo123'})
suffix=str(int(datetime.datetime.now().timestamp()*1000))
other=request('/api/auth/register',data={'username':'settle'+suffix,'nickname':'并发结算验证','studentNo':suffix,'password':'demo123'})
a=login('alice');people=[login('bob'),other];before=[request('/api/users/me',p['token']) for p in people];orders=[]
for i,p in enumerate(people):
 now=datetime.datetime.now()
 task=request('/api/tasks',a['token'],{'title':'MySQL concurrent settlement '+now.isoformat(),'category':'校园活动','description':'隔离数据库并发结算验证','taskTime':(now+datetime.timedelta(days=5)).isoformat(),'applicationDeadline':(now+datetime.timedelta(days=4)).isoformat(),'rewardPoints':10,'minCreditScore':0,'estimatedDurationMinutes':60})
 request('/api/tasks/'+str(task['id'])+'/applications',p['token'],{'message':'并发结算回归'})
 apply=request('/api/tasks/'+str(task['id'])+'/applications',a['token'])['records'][0]['application']['id']
 order=request('/api/applications/'+str(apply)+'/accept',a['token'],{})['order']['id'];orders.append(order)
 request('/api/orders/'+str(order)+'/start',p['token'],{})
 request('/api/orders/'+str(order)+'/completions',p['token'],{'description':'已完成'})
owner_before=request('/api/users/me',a['token'])
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 outcomes=list(pool.map(lambda id:request('/api/orders/'+str(id)+'/approve',a['token'],{}),orders))
assert all(o['order']['status']=='COMPLETED' for o in outcomes)
owner_after=request('/api/users/me',a['token'])
assert owner_after['frozenPoints']==owner_before['frozenPoints']-20,(owner_before,owner_after)
for old,p in zip(before,people):
 new=request('/api/users/me',p['token']);assert new['availablePoints']==old['availablePoints']+10
print(json.dumps({'concurrentOrders':2,'httpSuccess':2,'publisherFrozenDelta':-20,'recipientAvailableDeltas':[10,10],'passed':True}))
