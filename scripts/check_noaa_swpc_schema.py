#!/usr/bin/env python3
import datetime as dt
import json
import urllib.request

ENDPOINTS = {
    "kp_history": "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json",
    "kp_forecast": "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json",
    "f107_summary": "https://services.swpc.noaa.gov/products/summary/10cm-flux.json",
}


def fetch(url):
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "FTX-1-FieldOps-CP0008B-schema-check/1"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise SystemExit(f"{url}: HTTP {response.status}")
        return json.loads(response.read().decode("utf-8"))


def require_array(name, data):
    if not isinstance(data, list) or not data:
        raise SystemExit(f"{name}: expected non-empty JSON array")
    if not all(isinstance(row, dict) for row in data):
        raise SystemExit(f"{name}: expected object rows")
    return data


def require_time(value, label):
    if not isinstance(value, str):
        raise SystemExit(f"{label}: time_tag must be string")
    candidate = value[:-1] + "+00:00" if value.endswith("Z") else value + "+00:00"
    try:
        dt.datetime.fromisoformat(candidate)
    except ValueError as exc:
        raise SystemExit(f"{label}: invalid time_tag {value!r}: {exc}")


def require_number(value, label):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise SystemExit(f"{label}: expected numeric JSON value")


kp = require_array("kp_history", fetch(ENDPOINTS["kp_history"]))
for row in kp:
    if set(row) != {"time_tag", "Kp", "a_running", "station_count"}:
        raise SystemExit(f"kp_history: schema drift fields={sorted(row)}")
    require_time(row["time_tag"], "kp_history")
    require_number(row["Kp"], "kp_history.Kp")
    require_number(row["a_running"], "kp_history.a_running")
    if isinstance(row["station_count"], bool) or not isinstance(row["station_count"], int):
        raise SystemExit("kp_history.station_count: expected integer")

forecast = require_array("kp_forecast", fetch(ENDPOINTS["kp_forecast"]))
seen_status = set()
for row in forecast:
    if set(row) != {"time_tag", "kp", "observed", "noaa_scale"}:
        raise SystemExit(f"kp_forecast: schema drift fields={sorted(row)}")
    require_time(row["time_tag"], "kp_forecast")
    require_number(row["kp"], "kp_forecast.kp")
    if row["observed"] not in {"observed", "estimated", "predicted"}:
        raise SystemExit(f"kp_forecast: unknown status={row['observed']!r}")
    seen_status.add(row["observed"])
    scale = row["noaa_scale"]
    if scale is not None and scale not in {"G1", "G2", "G3", "G4", "G5"}:
        raise SystemExit(f"kp_forecast: unexpected noaa_scale={scale!r}")

f107 = require_array("f107_summary", fetch(ENDPOINTS["f107_summary"]))
for row in f107:
    if set(row) != {"flux", "time_tag"}:
        raise SystemExit(f"f107_summary: schema drift fields={sorted(row)}")
    require_number(row["flux"], "f107_summary.flux")
    require_time(row["time_tag"], "f107_summary")

print(
    "CP-0008B live NOAA schema check: PASS "
    f"kp_rows={len(kp)} forecast_rows={len(forecast)} "
    f"forecast_statuses={','.join(sorted(seen_status))} f107_rows={len(f107)}"
)
