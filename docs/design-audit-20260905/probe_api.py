import json, sys, urllib.request, urllib.error
from datetime import datetime, timedelta
from pathlib import Path
sys.stdout.reconfigure(encoding='utf-8')
BASE='http://127.0.0.1:18089'
results=[]
def req(method,path,data=None,token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    request=urllib.request.Request(BASE+path,data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(request,timeout=15) as r: return r.status,json.load(r)
    except urllib.error.HTTPError as e: return e.code,json.load(e)
def ok(method,path,data=None,token=None):
    status,body=req(method,path,data,token)
    assert status==200,(path,status,body)
    return body.get('data')
def record(name,status,observed):
    results.append(dict(name=name,http=status,observed=observed))
def login(account,password='demo123'): return ok('POST','/api/auth/login',dict(account=account,password=password))['token']
a=login('alice'); b=login('bob'); admin=login('admin','admin123')
s=ok('GET','/api/skills/profile',token=a)
record('skill_profile_contract',200,list(s))
record('dashboard_contract',200,ok('GET','/api/admin/dashboard',token=admin))
for path in ['/api/skills/match','/api/points/transactions']:
    code,body=req('GET',path,token=a);record('documented_route '+path,code,body)
code,body=req('GET','/api/public/tasks?page=abc');record('invalid_page',code,body)
code,body=req('PUT','/api/users/me',{'nickname':'Audit Alice','avatarUrl':'javascript:invalid-audit-url','bio':'audit'},a)
record('invalid_avatar_url',code,body.get('data',{}).get('avatarUrl'))
ok('PUT','/api/users/me',{'nickname':'Alice','avatarUrl':'','bio':''},a)
stamp=str(int(datetime.now().timestamp()))
code,body=req('POST','/api/auth/register',dict(username='audit'+stamp,password='audit123',nickname='audit',studentNo='!!!'+stamp))
record('unvalidated_student_number',code,{'studentNo':body.get('data',{}).get('user',{}).get('studentNo')})
c=body['data']['token']; cid=body['data']['user']['id']
taskbody=dict(title='audit-boundaries',category='其他',description='isolated audit',location='audit',taskTime=(datetime.now()+timedelta(days=3)).isoformat(timespec='seconds'),applicationDeadline=(datetime.now()+timedelta(days=2)).isoformat(timespec='seconds'),rewardPoints=10,minCreditScore=0)
code,body=req('POST','/api/tasks',dict(taskbody,category='x'*51),a)
record('oversize_category',code,body)
task=ok('POST','/api/tasks',taskbody,a); tid=task['id']
ok('POST',f'/api/tasks/{tid}/applications',{'message':'audit applicant'},b)
apps=ok('GET',f'/api/tasks/{tid}/applications',token=a)
record('applicant_view_fields',200,{'entry':list(apps['records'][0]),'applicant':list(apps['records'][0]['applicant'])})
aid=apps['records'][0]['application']['id']
code,body=req('POST',f'/api/tasks/{tid}/applications/{aid}/accept',{},a);record('documented_accept_application',code,body)
order=ok('POST',f'/api/applications/{aid}/accept',{},a)['order'];oid=order['id']
ok('POST',f'/api/orders/{oid}/start',{},b)
ok('POST',f'/api/orders/{oid}/completions',{'description':'audit done'},b)
code,body=req('POST',f'/api/orders/{oid}/accept',{},a);record('documented_accept_order',code,body)
ok('POST',f'/api/orders/{oid}/approve',{},a)
code,body=req('POST','/api/reports',dict(targetType='USER',targetId=cid,reasonType='VIOLATION',description='audit violation'),a)
rid=body['data']['id'];ok('POST',f'/api/admin/reports/{rid}/claim',{},admin)
before=ok('GET','/api/users/me',token=c)['creditScore']
ok('POST',f'/api/admin/reports/{rid}/handle',{'result':'核实违规'},admin)
after=ok('GET','/api/users/me',token=c)['creditScore']
record('confirmed_violation_credit',200,{'before':before,'after':after})
skill=ok('POST','/api/admin/skills',{'name':'Audit skill '+stamp,'category':'Audit'},admin)
sid=skill['id'];bid=ok('GET','/api/users/me',token=b)['id']
ok('POST','/api/skills/offers',{'skillId':sid,'proficiency':'INTERMEDIATE','availableMode':'BOTH'},b)
ex=ok('POST','/api/exchanges',{'providerId':bid,'requestSkillId':sid},c)
record('exchange_without_requester_need',200,{'id':ex['id'],'requesterHasNeed':False})
ok('PUT',f'/api/admin/skills/{sid}/status/DISABLED',token=admin)
code,body=req('POST',f'/api/exchanges/{ex["id"]}/accept',{},b)
record('accept_disabled_skill_exchange',code,body.get('data',{}).get('status'))
Path('backend/target/design-audit/api-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(results,ensure_ascii=False,indent=2))
