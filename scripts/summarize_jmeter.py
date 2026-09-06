"""Summarize the current isolated JMeter run into reproducible acceptance evidence."""
from __future__ import annotations

import csv
import hashlib
import json
import math
import os
import platform
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from collections import defaultdict
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PERF = ROOT / "backend" / "target" / "performance"
JTL = PERF / "final-results.jtl"
JMX = PERF / "acceptance.jmx"
SEED = PERF / "seed-counts.txt"
OUT = ROOT / "docs" / "verification-20260906"
LIMITS_MS = {
    "task-list": 1000, "task-detail": 1000, "application-list": 1000, "skill-match": 1000,
    "task-publish": 2000, "completion-submit": 2000, "settlement-approve": 2000,
}


def nearest_rank(values: list[int], percentile: float) -> int:
    values.sort()
    return values[max(0, math.ceil(len(values) * percentile) - 1)]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_tree_sha256() -> str:
    digest = hashlib.sha256()
    base = ROOT / "backend" / "src" / "main"
    for path in sorted(p for p in base.rglob("*") if p.is_file()):
        digest.update(path.relative_to(ROOT).as_posix().encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def tool_version(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return " ".join((result.stdout + result.stderr).splitlines()[:1]).strip()


def mysql_version() -> str:
    result = subprocess.run(
        ["docker", "exec", "campuslink-revision-mysql-20260905", "mysql", "-urevision",
         "-prevision-local-only-20260905", "-N", "-e", "SELECT VERSION()"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if result.returncode:
        return "isolated MySQL 8.0 (version query unavailable)"
    return "MySQL " + result.stdout.strip()


def plan_summary() -> list[dict[str, object]]:
    root = ET.parse(JMX).getroot()
    groups = []
    for node in root.iter("ThreadGroup"):
        props = {item.attrib.get("name"): item.text for item in node if item.tag in {"stringProp", "boolProp"}}
        groups.append({
            "name": node.attrib["testname"],
            "threads": int(props["ThreadGroup.num_threads"]),
            "rampSeconds": int(props["ThreadGroup.ramp_time"]),
            "durationSeconds": int(props["ThreadGroup.duration"]),
        })
    return groups


rows: dict[str, list[dict[str, str]]] = defaultdict(list)
with JTL.open(newline="", encoding="utf-8") as stream:
    for row in csv.DictReader(stream):
        rows[row["label"]].append(row)

groups = []
for label, limit in LIMITS_MS.items():
    samples = rows[label]
    elapsed = [int(row["elapsed"]) for row in samples]
    failures = sum(row["success"].lower() != "true" or row["responseCode"] != "200" for row in samples)
    error_rate = failures * 100 / len(samples)
    groups.append({
        "name": label,
        "samples": len(samples),
        "averageMs": round(sum(elapsed) / len(elapsed), 2),
        "p95Ms": nearest_rank(elapsed, 0.95),
        "maxMs": max(elapsed),
        "failures": failures,
        "errorRatePercent": round(error_rate, 4),
        "limitMs": limit,
        "passed": error_rate <= 1 and nearest_rank(elapsed, 0.95) <= limit,
    })

all_rows = [row for samples in rows.values() for row in samples]
started_ms = min(int(row["timeStamp"]) for row in all_rows)
ended_ms = max(int(row["timeStamp"]) + int(row["elapsed"]) for row in all_rows)
seed_numbers = [int(token) for token in SEED.read_text(encoding="utf-8").split() if token.isdigit()]
key_sources = [
    ROOT / "backend/src/main/java/com/campuslink/service/TaskService.java",
    ROOT / "backend/src/main/java/com/campuslink/service/SkillService.java",
    ROOT / "backend/src/main/java/com/campuslink/service/PointCreditService.java",
    ROOT / "backend/src/main/java/com/campuslink/service/StorageService.java",
    ROOT / "backend/src/main/java/com/campuslink/vo/Views.java",
    ROOT / "backend/src/main/resources/db/migration/V6__governance_and_ledger_constraints.sql",
]
evidence = {
    "generatedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
    "runStartedAt": datetime.fromtimestamp(started_ms / 1000).astimezone().isoformat(timespec="seconds"),
    "durationSeconds": round((ended_ms - started_ms) / 1000, 2),
    "method": "Apache JMeter non-GUI HTTP acceptance against an isolated localhost MySQL database",
    "environment": {
        "os": platform.platform(),
        "cpuLogicalCores": os.cpu_count(),
        "java": tool_version(["java", "-version"]),
        "jmeter": "Apache JMeter 5.6.3",
        "database": mysql_version(),
        "api": "http://127.0.0.1:18081",
    },
    "dataset": {"tasks": seed_numbers[0], "matchingCandidates": seed_numbers[1]},
    "testPlan": plan_summary(),
    "thresholds": {"maximumErrorRatePercent": 1, "responseTimeMetric": "nearest-rank P95"},
    "groups": groups,
    "totalSamples": len(all_rows),
    "backendSourceTreeSha256": source_tree_sha256(),
    "sourceSha256": {path.relative_to(ROOT).as_posix(): sha256(path) for path in key_sources},
    "jmeterDistributionSha512": (ROOT / "backend/target/apache-jmeter-5.6.3.zip.sha512").read_text(encoding="utf-8").split()[0].lower(),
}
evidence["passed"] = all(group["passed"] for group in groups)
OUT.mkdir(parents=True, exist_ok=True)
summary_path = OUT / "jmeter-current.json"
summary_path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
archive_path = OUT / "jmeter-current.zip"
with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    archive.write(JTL, "final-results.jtl")
    archive.write(SEED, "seed-counts.txt")
    archive.write(summary_path, "jmeter-current.json")
print(json.dumps(evidence, ensure_ascii=False, indent=2))
if not evidence["passed"]:
    raise SystemExit(1)
