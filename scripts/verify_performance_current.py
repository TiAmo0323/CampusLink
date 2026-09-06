"""Current-code performance acceptance against the disposable localhost MySQL instance."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime,timedelta
from pathlib import Path
import hashlib,json,math,os,platform,random,time,urllib.request,urllib.error

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"docs"/"verification-20260905"
OUT.mkdir(parents=True,exist_ok=True)
API="http://127.0.0.1:18081"

def request(method,path,token=None,body=None):
    raw=None if body is None else json.dumps(body,ensure_ascii=False).encode()
    headers={"Content-Type":"application/json"}
    if token: headers["Authorization"]="Bearer "+token
    started=time.perf_counter()
    try:
        response=urllib.request.urlopen(urllib.request.Request(API+path,data=raw,headers=headers,method=method),timeout=15)
        payload=json.load(response)
        ok=response.status==200 and payload.get("code")==200
        return {"ok":ok,"ms":round((time.perf_counter()-started)*1000,2),"status":response.status,"data":payload.get("data"),"message":payload.get("message")}
    except Exception as error:
        return {"ok":False,"ms":round((time.perf_counter()-started)*1000,2),"status":getattr(error,"code",0),"message":str(error)}

def must(result,label):
    if not result["ok"]: raise RuntimeError(f"{label}: {result}")
    return result["data"]

def login(account):
    return must(request("POST","/api/auth/login",body={"account":account,"password":"demo123"}),"login "+account)["token"]

def task_body(prefix):
    now=datetime.now()
    return {"title":f"{prefix}-{time.time_ns()}","category":"校园活动","description":"当前版本隔离性能验收数据",
            "taskTime":(now+timedelta(days=5)).isoformat(timespec="seconds"),
            "applicationDeadline":(now+timedelta(days=4)).isoformat(timespec="seconds"),
            "rewardPoints":1,"minCreditScore":0,"estimatedDurationMinutes":60}

def run_group(name,jobs,workers,limit_ms):
    with ThreadPoolExecutor(max_workers=workers) as pool: results=list(pool.map(lambda job:request(*job),jobs))
    values=sorted(x["ms"] for x in results)
    failures=sum(not x["ok"] for x in results)
    p95=values[max(0,math.ceil(len(values)*.95)-1)]
    row={"name":name,"requests":len(results),"workers":workers,"averageMs":round(sum(values)/len(values),2),
         "p95Ms":p95,"maxMs":max(values),"failures":failures,"errorRatePercent":round(failures*100/len(values),2),
         "limitMs":limit_ms,"passed":failures/len(values)<=.01 and p95<=limit_ms}
    if failures: row["failureSamples"]=[x for x in results if not x["ok"]][:3]
    return row

suite_started=time.perf_counter()
alice=login("alice")
publisher=login("perf_publisher")
first=must(request("GET","/api/public/tasks?page=1&size=10"),"task fixture")["records"][0]["id"]
groups=[]
read_jobs=[("GET",f"/api/public/tasks?page={random.randint(1,1000)}&size=10",None,None) for _ in range(500)]
groups.append(run_group("task-list-50-concurrency",read_jobs,50,1000))
groups.append(run_group("task-detail-50-concurrency",[("GET",f"/api/public/tasks/{first}",None,None)]*200,50,1000))
groups.append(run_group("skill-match-1000-candidates",[("GET","/api/skills/matches?page=1&size=10",alice,None)]*100,10,1000))

profile_task=must(request("POST","/api/tasks",publisher,task_body("application-list")),"profile task")
candidate_tokens={}
with ThreadPoolExecutor(max_workers=10) as pool:
    token_values=list(pool.map(login,[f"perf_candidate_{i}" for i in range(20)]))
for index,token in enumerate(token_values):
    candidate_tokens[index]=token
    must(request("POST",f"/api/tasks/{profile_task['id']}/applications",token,{"message":f"申请者{index}"}),"application seed")
groups.append(run_group("application-list-pagination",[("GET",f"/api/tasks/{profile_task['id']}/applications?page=1&size=10",publisher,None)]*100,10,2000))

workflows=[]
for index in range(10):
    task=must(request("POST","/api/tasks",publisher,task_body("lifecycle")),"workflow task")
    application=must(request("POST",f"/api/tasks/{task['id']}/applications",candidate_tokens[index],{"message":"并发履约"}),"workflow application")
    accepted=must(request("POST",f"/api/applications/{application['id']}/accept",publisher,{}),"workflow accept")
    workflows.append((accepted["order"]["id"],candidate_tokens[index]))
groups.append(run_group("order-start", [("POST",f"/api/orders/{oid}/start",token,{}) for oid,token in workflows],10,2000))
groups.append(run_group("completion-submit",[("POST",f"/api/orders/{oid}/completions",token,{"description":"并发完成提交","proofUrl":None}) for oid,token in workflows],10,2000))
groups.append(run_group("settlement-approve",[("POST",f"/api/orders/{oid}/approve",publisher,{}) for oid,token in workflows],10,2000))

sources=[
 "backend/src/main/java/com/campuslink/service/TaskService.java",
 "backend/src/main/java/com/campuslink/service/SkillService.java",
 "backend/src/main/java/com/campuslink/service/PointCreditService.java",
 "backend/src/main/java/com/campuslink/service/StorageService.java",
 "backend/src/main/java/com/campuslink/vo/Views.java",
 "backend/src/main/resources/db/migration/V6__governance_and_ledger_constraints.sql"
]
evidence={"generatedAt":datetime.now().astimezone().isoformat(timespec="seconds"),"database":"isolated MySQL 8.0",
          "dataset":{"tasks":100000,"matchingCandidates":1000},"groups":groups,
          "method":"Concurrent HTTP precheck; final performance acceptance uses JMeter",
          "durationSeconds":round(time.perf_counter()-suite_started,2),
          "environment":{"os":platform.platform(),"cpuLogicalCores":os.cpu_count(),"python":platform.python_version(),"api":API},
          "sourceSha256":{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sources}}
evidence["passed"]=all(x["passed"] for x in groups)
(OUT/"performance-current.json").write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding="utf-8")
print(json.dumps(evidence,ensure_ascii=False,indent=2))
if not evidence["passed"]: raise SystemExit(1)
