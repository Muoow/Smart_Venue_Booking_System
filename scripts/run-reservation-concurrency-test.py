import argparse
import json
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta
from urllib import error, request


def http_request(method, url, body=None, headers=None, timeout=15):
    headers = headers or {}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers = {**headers, "Content-Type": "application/json"}
    req = request.Request(url, method=method, data=data, headers=headers)
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            payload = resp.read().decode("utf-8") if resp.length != 0 else ""
            return resp.status, json.loads(payload) if payload else {}
    except error.HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        try:
            body = json.loads(payload)
        except json.JSONDecodeError:
            body = {"code": exc.code, "message": payload}
        return exc.code, body


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:18082")
    parser.add_argument("--resource-id", type=int, default=2)
    parser.add_argument("--venue-id", type=int, default=1)
    parser.add_argument("--size", type=int, default=1)
    parser.add_argument("--concurrency", type=int, default=12)
    parser.add_argument("--start-unit", type=int, default=132)
    parser.add_argument("--end-unit", type=int, default=135)
    parser.add_argument("--days-offset", type=int, default=9)
    args = parser.parse_args()

    _, login = http_request(
        "POST",
        args.base_url + "/auth/login",
        body={"username": "demo", "password": "demo"},
    )
    token = login.get("data")
    if not token:
        raise SystemExit("failed to login demo user")

    headers = {"Authorization": f"Bearer {token}"}
    slot_date = (datetime.now() + timedelta(days=args.days_offset)).strftime("%Y-%m-%dT00:00:00")

    def worker():
        status, body = http_request(
            "POST",
            args.base_url + "/reservation/apply",
            body={
                "venueId": args.venue_id,
                "resourceId": args.resource_id,
                "slotDate": slot_date,
                "startUnit": args.start_unit,
                "endUnit": args.end_unit,
                "size": args.size,
            },
            headers=headers,
        )
        return {"httpStatus": status, "body": body}

    started = time.perf_counter()
    results = []
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(worker) for _ in range(args.concurrency)]
        for future in as_completed(futures):
            results.append(future.result())
    duration = round((time.perf_counter() - started) * 1000, 2)

    success = [item for item in results if item["body"].get("code") == 200]
    conflicts = [item for item in results if item["body"].get("code") == 409]
    others = [item for item in results if item["body"].get("code") not in {200, 409}]

    status, availability = http_request(
        "GET",
        args.base_url + f"/reservation/availability?resourceId={args.resource_id}&slotDate={slot_date[:10]}",
        headers=headers,
    )
    slots = availability.get("data", {}).get("slots", []) if status == 200 else []
    checked_units = [slot for slot in slots if args.start_unit <= slot.get("slotUnit", -1) <= args.end_unit]

    print(json.dumps({
        "baseUrl": args.base_url,
        "slotDate": slot_date,
        "resourceId": args.resource_id,
        "concurrency": args.concurrency,
        "durationMs": duration,
        "successCount": len(success),
        "conflictCount": len(conflicts),
        "otherCount": len(others),
        "sampleOther": others[0] if others else None,
        "checkedUnits": checked_units,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
