"""Seed ONLY the disposable revision database and generate a repeatable JMeter plan."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from pathlib import Path
import csv
import json
import subprocess
import urllib.request
import uuid
import xml.etree.ElementTree as X
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "backend" / "target" / "performance"
OUT.mkdir(parents=True, exist_ok=True)
CONTAINER = "campuslink-revision-mysql-20260905"
DATABASE = "campuslink_revision"
API = "http://127.0.0.1:18081"

DIGITS = "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)"
NUMBERS = f"SELECT a.n+10*b.n+100*c.n+1000*d.n+10000*e.n n FROM {DIGITS} a CROSS JOIN {DIGITS} b CROSS JOIN {DIGITS} c CROSS JOIN {DIGITS} d CROSS JOIN {DIGITS} e"
SQL = f"""
INSERT INTO `user`(id,username,password_hash,nickname,role,credit_score,available_points,frozen_points,status,token_version,created_at,updated_at)
SELECT 10000+n,CONCAT('perf_candidate_',n),(SELECT password_hash FROM `user` WHERE username='bob'),'压测候选','STUDENT',90,200,0,'NORMAL',0,NOW(),NOW() FROM ({NUMBERS}) seq WHERE n<1000 AND NOT EXISTS(SELECT 1 FROM `user` WHERE id=10000+n);
INSERT INTO user_skill(user_id,skill_id,proficiency,available_mode,created_at)
SELECT id,3,'INTERMEDIATE','BOTH',NOW() FROM `user` WHERE id BETWEEN 10000 AND 10999 AND NOT EXISTS(SELECT 1 FROM user_skill WHERE user_id=`user`.id AND skill_id=3);
INSERT INTO `user`(id,username,password_hash,nickname,role,credit_score,available_points,frozen_points,status,token_version,created_at,updated_at)
SELECT 20000,'perf_publisher',password_hash,'压测发布者','STUDENT',100,500000,100000,'NORMAL',0,NOW(),NOW() FROM `user` WHERE username='alice' AND NOT EXISTS(SELECT 1 FROM `user` WHERE id=20000);
INSERT INTO task(publisher_id,title,category,description,location,task_time,application_deadline,reward_points,min_credit_score,status,version,created_at,updated_at)
SELECT 20000,CONCAT('perf_fixture_',n),'校园活动','隔离只读性能测试数据','活动中心',DATE_ADD(NOW(),INTERVAL 5 DAY),DATE_ADD(NOW(),INTERVAL 4 DAY),1,0,'RECRUITING',0,DATE_SUB(NOW(),INTERVAL n SECOND),NOW() FROM ({NUMBERS}) seq WHERE NOT EXISTS(SELECT 1 FROM task WHERE title=CONCAT('perf_fixture_',n));
SELECT COUNT(*) tasks FROM task;
SELECT COUNT(*) candidates FROM `user` WHERE id BETWEEN 10000 AND 10999;
"""
result = subprocess.run(
    ["docker", "exec", "-i", CONTAINER, "mysql", "-urevision", "-prevision-local-only-20260905", DATABASE],
    input=SQL.encode(), capture_output=True,
)
if result.returncode:
    raise RuntimeError(result.stderr.decode(errors="replace"))
print(result.stdout.decode())
(OUT / "seed-counts.txt").write_bytes(result.stdout)


def call(method, path, token=None, body=None):
    raw = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(API + path, data=raw, headers=headers, method=method)
    payload = json.load(urllib.request.urlopen(request, timeout=15))
    if payload["code"] != 200:
        raise RuntimeError(f"{method} {path}: {payload}")
    return payload["data"]


def login(account):
    return call("POST", "/api/auth/login", body={"account": account, "password": "demo123"})["token"]


def task_body(prefix):
    now = datetime.now()
    return {
        "title": f"{prefix}-{uuid.uuid4()}", "category": "校园活动", "description": "隔离写入压测",
        "taskTime": (now + timedelta(days=5)).isoformat(timespec="seconds"),
        "applicationDeadline": (now + timedelta(days=4)).isoformat(timespec="seconds"),
        "rewardPoints": 1, "minCreditScore": 0, "estimatedDurationMinutes": 60,
    }


alice = login("alice")
publisher = login("perf_publisher")
with ThreadPoolExecutor(max_workers=10) as pool:
    candidate_tokens = list(pool.map(login, [f"perf_candidate_{index}" for index in range(50)]))

application_task = call("POST", "/api/tasks", publisher, task_body("application-list"))
with ThreadPoolExecutor(max_workers=10) as pool:
    list(pool.map(
        lambda item: call("POST", f"/api/tasks/{application_task['id']}/applications", item[1], {"message": f"申请者{item[0]}"}),
        enumerate(candidate_tokens),
    ))


def workflow(index, ready_for_acceptance=False):
    candidate = candidate_tokens[1 if ready_for_acceptance else 0]
    task = call("POST", "/api/tasks", publisher, task_body("jmeter-lifecycle"))
    application = call("POST", f"/api/tasks/{task['id']}/applications", candidate, {"message": "JMeter写操作夹具"})
    accepted = call("POST", f"/api/applications/{application['id']}/accept", publisher, {})
    order_id = accepted["order"]["id"]
    call("POST", f"/api/orders/{order_id}/start", candidate, {})
    if ready_for_acceptance:
        call("POST", f"/api/orders/{order_id}/completions", candidate, {"description": "JMeter验收夹具"})
    return order_id


with ThreadPoolExecutor(max_workers=10) as pool:
    completion_orders = list(pool.map(lambda index: workflow(index, False), range(100)))
    settlement_orders = list(pool.map(lambda index: workflow(index, True), range(100, 200)))


def write_orders(path, values):
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["orderId"])
        writer.writerows((value,) for value in values)


completion_csv = OUT / "completion-orders.csv"
settlement_csv = OUT / "settlement-orders.csv"
write_orders(completion_csv, completion_orders)
write_orders(settlement_csv, settlement_orders)

root = X.Element("jmeterTestPlan", version="1.2", properties="5.0", jmeter="5.6.3")
tree = X.SubElement(root, "hashTree")
plan = X.SubElement(tree, "TestPlan", guiclass="TestPlanGui", testclass="TestPlan", testname="CampusLink isolated acceptance", enabled="true")


def prop(parent, name, value, kind="stringProp"):
    X.SubElement(parent, kind, name=name).text = str(value).lower() if isinstance(value, bool) else str(value)


prop(plan, "TestPlan.serialize_threadgroups", True, "boolProp")
groups = X.SubElement(tree, "hashTree")


def group(name, threads, duration, samplers, token=None, loops=-1, csv_file=None):
    tg = X.SubElement(groups, "ThreadGroup", guiclass="ThreadGroupGui", testclass="ThreadGroup", testname=name, enabled="true")
    prop(tg, "ThreadGroup.num_threads", threads)
    prop(tg, "ThreadGroup.ramp_time", 10 if duration else 2)
    prop(tg, "ThreadGroup.scheduler", duration > 0, "boolProp")
    prop(tg, "ThreadGroup.duration", duration)
    prop(tg, "ThreadGroup.on_sample_error", "continue")
    loop = X.SubElement(tg, "elementProp", name="ThreadGroup.main_controller", elementType="LoopController")
    prop(loop, "LoopController.continue_forever", loops == -1, "boolProp")
    prop(loop, "LoopController.loops", loops)
    children = X.SubElement(groups, "hashTree")
    if csv_file:
        dataset = X.SubElement(children, "CSVDataSet", guiclass="TestBeanGUI", testclass="CSVDataSet", testname="Unique order ids", enabled="true")
        for key, value in [
            ("filename", str(csv_file.resolve())), ("fileEncoding", "UTF-8"), ("variableNames", "orderId"),
            ("delimiter", ","), ("quotedData", "false"), ("recycle", "false"), ("stopThread", "true"),
            ("ignoreFirstLine", "true"), ("shareMode", "shareMode.all"),
        ]:
            prop(dataset, key, value)
        X.SubElement(children, "hashTree")
    header = X.SubElement(children, "HeaderManager", guiclass="HeaderPanel", testclass="HeaderManager", testname="JSON and auth", enabled="true")
    collection = X.SubElement(header, "collectionProp", name="HeaderManager.headers")
    for key, value in [("Content-Type", "application/json")] + ([("Authorization", "Bearer " + token)] if token else []):
        item = X.SubElement(collection, "elementProp", name=key, elementType="Header")
        prop(item, "Header.name", key)
        prop(item, "Header.value", value)
    X.SubElement(children, "hashTree")
    for label, path, body in samplers:
        sampler = X.SubElement(children, "HTTPSamplerProxy", guiclass="HttpTestSampleGui", testclass="HTTPSamplerProxy", testname=label, enabled="true")
        for key, value in [
            ("domain", "127.0.0.1"), ("port", 18081), ("protocol", "http"), ("path", path),
            ("method", "POST" if body is not None else "GET"), ("connect_timeout", 5000), ("response_timeout", 10000),
        ]:
            prop(sampler, "HTTPSampler." + key, value)
        prop(sampler, "HTTPSampler.use_keepalive", True, "boolProp")
        args = X.SubElement(sampler, "elementProp", name="HTTPsampler.Arguments", elementType="Arguments")
        args_list = X.SubElement(args, "collectionProp", name="Arguments.arguments")
        if body is not None:
            prop(sampler, "HTTPSampler.postBodyRaw", True, "boolProp")
            argument = X.SubElement(args_list, "elementProp", name="", elementType="HTTPArgument")
            prop(argument, "HTTPArgument.always_encode", False, "boolProp")
            prop(argument, "Argument.value", json.dumps(body, ensure_ascii=False))
            prop(argument, "Argument.metadata", "=")
        child = X.SubElement(children, "hashTree")
        assertion = X.SubElement(child, "ResponseAssertion", guiclass="AssertionGui", testclass="ResponseAssertion", testname="HTTP 200", enabled="true")
        patterns = X.SubElement(assertion, "collectionProp", name="Asserion.test_strings")
        prop(patterns, "200", "200")
        prop(assertion, "Assertion.test_field", "Assertion.response_code")
        prop(assertion, "Assertion.test_type", 8, "intProp")
        X.SubElement(child, "hashTree")


group("50 concurrent readers", 50, 60, [
    ("task-list", "/api/public/tasks?page=${__Random(1,100)}&size=10", None),
    ("task-detail", "/api/public/tasks/1", None),
])
group("application list query", 10, 30, [("application-list", f"/api/tasks/{application_task['id']}/applications?page=1&size=10", None)], publisher)
group("1000 candidate matching", 10, 30, [("skill-match", "/api/skills/matches?page=1&size=10", None)], alice)
future = (datetime.now() + timedelta(days=5)).isoformat(timespec="seconds")
deadline = (datetime.now() + timedelta(days=4)).isoformat(timespec="seconds")
group("transactional task publication", 10, 30, [("task-publish", "/api/tasks", {
    "title": "JMeter-${__UUID}", "category": "校园活动", "description": "隔离写入压测",
    "taskTime": future, "applicationDeadline": deadline, "rewardPoints": 1,
    "minCreditScore": 0, "estimatedDurationMinutes": 60,
})], publisher)
group("completion submission", 10, 0, [("completion-submit", "/api/orders/${orderId}/completions", {"description": "JMeter并发完成提交"})], candidate_tokens[0], loops=10, csv_file=completion_csv)
group("settlement approval", 10, 0, [("settlement-approve", "/api/orders/${orderId}/approve", {})], publisher, loops=10, csv_file=settlement_csv)
X.ElementTree(root).write(OUT / "acceptance.jmx", encoding="utf-8", xml_declaration=True)

archive = ROOT / "backend/target/apache-jmeter-5.6.3.zip"
if not (ROOT / "backend/target/apache-jmeter-5.6.3/bin/ApacheJMeter.jar").exists() and archive.exists():
    with zipfile.ZipFile(archive) as bundle:
        bundle.extractall(ROOT / "backend/target")
print(OUT / "acceptance.jmx")
