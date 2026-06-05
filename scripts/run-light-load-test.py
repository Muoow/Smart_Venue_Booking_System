import argparse
import json
import math
import statistics
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path
from urllib import request, error

ROOT = Path(r"c:\Users\GALAXY\Desktop\新建文件夹\main\Smart_Venue_Booking_System")
ARTIFACTS = ROOT / "test-artifacts" / "load-test"


def quantile(values, ratio):
    if not values:
        return 0.0
    if len(values) == 1:
        return round(values[0], 2)
    pos = (len(values) - 1) * ratio
    lower = math.floor(pos)
    upper = math.ceil(pos)
    if lower == upper:
        return round(values[int(pos)], 2)
    lower_value = values[lower]
    upper_value = values[upper]
    interpolated = lower_value + (upper_value - lower_value) * (pos - lower)
    return round(interpolated, 2)


def http_request(method, url, body=None, headers=None, timeout=15):
    headers = headers or {}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers = {**headers, "Content-Type": "application/json"}
    req = request.Request(url, method=method, data=data, headers=headers)
    start = time.perf_counter()
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            payload = resp.read()
            elapsed = (time.perf_counter() - start) * 1000
            text = payload.decode("utf-8") if payload else ""
            parsed = json.loads(text) if text else None
            return {
                "ok": 200 <= resp.status < 300,
                "status": resp.status,
                "elapsedMs": round(elapsed, 2),
                "body": parsed,
                "error": None,
            }
    except error.HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        elapsed = (time.perf_counter() - start) * 1000
        return {
            "ok": False,
            "status": exc.code,
            "elapsedMs": round(elapsed, 2),
            "body": payload,
            "error": f"HTTP {exc.code}",
        }
    except Exception as exc:
        elapsed = (time.perf_counter() - start) * 1000
        return {
            "ok": False,
            "status": 0,
            "elapsedMs": round(elapsed, 2),
            "body": None,
            "error": str(exc),
        }


def run_endpoint(case, concurrency, rounds_per_worker):
    def worker(_worker_id):
        local = []
        for _ in range(rounds_per_worker):
            local.append(http_request(case["method"], case["url"], case.get("body"), case.get("headers")))
        return local

    started = time.perf_counter()
    records = []
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(worker, idx) for idx in range(concurrency)]
        for future in as_completed(futures):
            records.extend(future.result())
    total_elapsed = time.perf_counter() - started
    latencies = sorted(item["elapsedMs"] for item in records)
    success = sum(1 for item in records if item["ok"])
    failures = len(records) - success
    success_rate = round((success / len(records)) * 100, 2) if records else 0.0
    throughput = round(len(records) / total_elapsed, 2) if total_elapsed else 0.0
    return {
        "name": case["name"],
        "path": case["path"],
        "method": case["method"],
        "requests": len(records),
        "success": success,
        "failed": failures,
        "successRate": success_rate,
        "avgMs": round(statistics.mean(latencies), 2) if latencies else 0.0,
        "medianMs": round(statistics.median(latencies), 2) if latencies else 0.0,
        "p95Ms": quantile(latencies, 0.95),
        "p99Ms": quantile(latencies, 0.99),
        "maxMs": round(max(latencies), 2) if latencies else 0.0,
        "minMs": round(min(latencies), 2) if latencies else 0.0,
        "throughputRps": throughput,
        "sampleError": next((item["error"] for item in records if item["error"]), None),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--username", default="caojinshuo")
    parser.add_argument("--password", default="12345")
    parser.add_argument("--concurrency", type=int, default=12)
    parser.add_argument("--rounds", type=int, default=5)
    args = parser.parse_args()

    ARTIFACTS.mkdir(parents=True, exist_ok=True)

    login_result = http_request(
        "POST",
        args.base_url + "/auth/login",
        body={"username": args.username, "password": args.password},
    )
    if not login_result["ok"] or not login_result.get("body") or not login_result["body"].get("data"):
        raise SystemExit("Failed to acquire token before load test.")

    token = login_result["body"]["data"]
    auth_headers = {"Authorization": f"Bearer {token}"}

    cases = [
        {
            "name": "Login API",
            "method": "POST",
            "path": "/auth/login",
            "url": args.base_url + "/auth/login",
            "body": {"username": args.username, "password": args.password},
        },
        {
            "name": "Venue List API",
            "method": "GET",
            "path": "/venue/list",
            "url": args.base_url + "/venue/list",
        },
        {
            "name": "Recommendation API",
            "method": "POST",
            "path": "/recommendation/venues",
            "url": args.base_url + "/recommendation/venues",
            "body": {
                "sportKeyword": "badminton",
                "preferredUnitMinutes": 10,
                "expectedPeopleCount": 4,
                "maxBudget": 60,
                "preferLowPrice": True,
                "expectedStartUnit": 114,
                "expectedEndUnit": 125,
                "topN": 3,
            },
        },
        {
            "name": "Profile API",
            "method": "GET",
            "path": "/user/profile",
            "url": args.base_url + "/user/profile",
            "headers": auth_headers,
        },
        {
            "name": "Reservation List API",
            "method": "GET",
            "path": "/reservation/my?pageNumber=1&pageSize=10",
            "url": args.base_url + "/reservation/my?pageNumber=1&pageSize=10",
            "headers": auth_headers,
        },
    ]

    endpoint_results = [run_endpoint(case, args.concurrency, args.rounds) for case in cases]
    summary = {
        "generatedAt": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "baseUrl": args.base_url,
        "concurrency": args.concurrency,
        "roundsPerWorker": args.rounds,
        "requestsPerEndpoint": args.concurrency * args.rounds,
        "endpoints": endpoint_results,
    }

    (ARTIFACTS / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# CourtFlow Light Load Test Summary",
        "",
        f"- Generated at: {summary['generatedAt']}",
        f"- Base URL: {summary['baseUrl']}",
        f"- Concurrency: {summary['concurrency']}",
        f"- Rounds per worker: {summary['roundsPerWorker']}",
        f"- Requests per endpoint: {summary['requestsPerEndpoint']}",
        "",
        "| API | Success Rate | Avg (ms) | P95 (ms) | P99 (ms) | Max (ms) | Throughput (req/s) |",
        "| :--- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for item in endpoint_results:
        lines.append(
            f"| {item['name']} | {item['successRate']}% | {item['avgMs']} | {item['p95Ms']} | {item['p99Ms']} | {item['maxMs']} | {item['throughputRps']} |"
        )
    (ARTIFACTS / "summary.md").write_text("\n".join(lines), encoding="utf-8")
    print(str(ARTIFACTS / "summary.json"))


if __name__ == "__main__":
    main()
