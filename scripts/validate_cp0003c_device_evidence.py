#!/usr/bin/env python3
import argparse
import json
import pathlib
import re
import sys
import tempfile

HEADER = "FieldOps CP-0003C validation"
ALLOWED_KEYS = {
    "source_sha",
    "device",
    "android_sdk",
    "backup_import",
    "signing_only",
    "live_transaction",
    "confirmation_sync",
    "automatic_upload",
    "result",
}
PASS_KEYS = ("backup_import", "signing_only", "live_transaction", "confirmation_sync")
S23_ULTRA = re.compile(r"(?i)^samsung\s+sm-s918[a-z0-9-]*$")
HEX40 = re.compile(r"^[0-9a-f]{40}$")
FORBIDDEN = (
    "-----BEGIN ",
    ".tbk",
    ".p12",
    ".pfx",
    "web_password=",
    "lotw_password=",
    "key_password=",
    "private_key=",
    "pkcs12=",
)


class EvidenceError(ValueError):
    pass


def load_baseline(path: pathlib.Path) -> dict:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        raise EvidenceError(f"cannot read baseline metadata: {exc}") from exc

    required = ("source_sha", "apk_sha256", "workflow_run", "artifact_id")
    missing = [key for key in required if not data.get(key)]
    if missing:
        raise EvidenceError("baseline metadata missing: " + ", ".join(missing))
    source_sha = str(data["source_sha"]).lower()
    if not HEX40.fullmatch(source_sha):
        raise EvidenceError("baseline source_sha must be a full 40-character Git SHA")
    apk_sha = str(data["apk_sha256"]).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", apk_sha):
        raise EvidenceError("baseline apk_sha256 must be a 64-character SHA-256")
    return data


def parse_evidence(text: str) -> dict:
    raw = text.strip()
    if not raw:
        raise EvidenceError("device evidence is empty")

    lower = raw.lower()
    for marker in FORBIDDEN:
        if marker.lower() in lower:
            raise EvidenceError(f"device evidence contains forbidden sensitive marker: {marker}")

    lines = raw.splitlines()
    if lines[0].strip() != HEADER:
        raise EvidenceError(f"first line must be exactly: {HEADER}")

    fields = {}
    for lineno, line in enumerate(lines[1:], start=2):
        if not line.strip():
            continue
        if "=" not in line:
            raise EvidenceError(f"line {lineno} is not key=value")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if key not in ALLOWED_KEYS:
            raise EvidenceError(f"unexpected evidence key: {key}")
        if key in fields:
            raise EvidenceError(f"duplicate evidence key: {key}")
        fields[key] = value

    missing = ALLOWED_KEYS - fields.keys()
    if missing:
        raise EvidenceError("missing evidence fields: " + ", ".join(sorted(missing)))
    return fields


def validate(fields: dict, baseline: dict) -> None:
    source_sha = fields["source_sha"].lower()
    if source_sha != str(baseline["source_sha"]).lower():
        raise EvidenceError(
            "device evidence source_sha does not match the approved validation APK baseline"
        )

    if not S23_ULTRA.fullmatch(fields["device"]):
        raise EvidenceError(
            "device must identify a Samsung Galaxy S23 Ultra model in the SM-S918 family"
        )

    try:
        sdk = int(fields["android_sdk"])
    except ValueError as exc:
        raise EvidenceError("android_sdk must be an integer") from exc
    if sdk < 26:
        raise EvidenceError("android_sdk is below the validation APK minimum supported level")

    for key in PASS_KEYS:
        if fields[key] != "PASS":
            raise EvidenceError(f"{key} must be PASS")

    if fields["automatic_upload"] != "DISABLED":
        raise EvidenceError("automatic_upload must remain DISABLED")

    result = fields["result"]
    if not result.startswith("CP-0003C LIVE TRANSACTION PASS:"):
        raise EvidenceError("result does not contain the required live transaction PASS")
    if "Confirmation-sync query also PASS" not in result:
        raise EvidenceError("result does not prove confirmation-report query success")

    # The app intentionally emits no QSO identity or credential material in the
    # sanitized clipboard block. Keep that invariant conservative.
    if re.search(r"(?i)(password|private.?key|pkcs.?12|backup bytes|credential)\s*[:=]", result):
        raise EvidenceError("result contains credential-like material")


def validate_files(evidence_path: pathlib.Path, baseline_path: pathlib.Path) -> None:
    baseline = load_baseline(baseline_path)
    fields = parse_evidence(evidence_path.read_text(encoding="utf-8"))
    validate(fields, baseline)


def self_test() -> None:
    baseline = {
        "source_sha": "1" * 40,
        "apk_sha256": "2" * 64,
        "workflow_run": 123,
        "artifact_id": 456,
    }
    good = "\n".join(
        [
            HEADER,
            "source_sha=" + baseline["source_sha"],
            "device=samsung SM-S918U1",
            "android_sdk=36",
            "backup_import=PASS",
            "signing_only=PASS",
            "live_transaction=PASS",
            "confirmation_sync=PASS",
            "automatic_upload=DISABLED",
            "result=CP-0003C LIVE TRANSACTION PASS: LoTW accepted and verified the QSO; TrustedQSL duplicate state committed. Confirmation-sync query also PASS (selected QSO currently confirmed=no).",
        ]
    )

    fields = parse_evidence(good)
    validate(fields, baseline)

    bad_cases = [
        good.replace("signing_only=PASS", "signing_only=FAIL"),
        good.replace("device=samsung SM-S918U1", "device=samsung SM-S911U1"),
        good.replace("automatic_upload=DISABLED", "automatic_upload=ENABLED"),
        good.replace(baseline["source_sha"], "3" * 40),
        good.replace("Confirmation-sync query also PASS", "Confirmation-sync query failed"),
        good + "\nlotw_password=secret",
    ]
    for idx, case in enumerate(bad_cases, start=1):
        try:
            validate(parse_evidence(case), baseline)
        except EvidenceError:
            continue
        raise AssertionError(f"self-test bad case {idx} unexpectedly passed")

    with tempfile.TemporaryDirectory() as temp:
        root = pathlib.Path(temp)
        baseline_path = root / "baseline.json"
        evidence_path = root / "evidence.txt"
        baseline_path.write_text(json.dumps(baseline), encoding="utf-8")
        evidence_path.write_text(good, encoding="utf-8")
        validate_files(evidence_path, baseline_path)

    print("CP-0003C device-evidence validator self-test: PASS")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--evidence",
        default="research/tqsl/CP-0003C_DEVICE_EVIDENCE.txt",
    )
    parser.add_argument(
        "--baseline",
        default="research/tqsl/CP-0003C_APK_BASELINE.json",
    )
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    try:
        if args.self_test:
            self_test()
        else:
            validate_files(pathlib.Path(args.evidence), pathlib.Path(args.baseline))
            print("CP-0003C sanitized device evidence: PASS")
        return 0
    except (EvidenceError, OSError, json.JSONDecodeError) as exc:
        print(f"CP-0003C sanitized device evidence: FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
