#!/usr/bin/env python3
import json
import os
import pathlib
import re


def replace_one(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} section, found {count}")
    return updated


root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]

baseline_path = root / "research" / "tqsl" / "CP-0003C_APK_BASELINE.json"
baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
source_sha = baseline["source_sha"]
apk_sha = baseline["apk_sha256"]
apk_run = baseline["workflow_run"]
apk_artifact = baseline["artifact_id"]

(root / "VERSION").write_text("v14-real-lotw-validated\n", encoding="utf-8")

readme_path = root / "README.md"
readme = readme_path.read_text(encoding="utf-8")

baseline_section = f"""## Verified durable baseline

**Latest verified checkpoint:** `CP-0003C-REAL_LOTW_VALIDATED`

Parent: `CP-0003B-LOTW_TRANSACTION_SAFE`.

CP-0003C is the first **GREEN real-device / real-LoTW checkpoint** in the LoTW chain. The official TrustedQSL runtime was packaged for Android arm64-v8a, loaded on Chris's Galaxy S23 Ultra, restored from a real private TQSL backup, used to produce a real signed TQ8, and exercised through the CP-0003B transaction against one genuine LoTW QSO.

Verified CP-0003C behavior:

- Official TrustedQSL **2.8.6** archive remains pinned at SHA-256 `182e5f2ac35a3db8b409b45d96505e6bd265ae4668ed064754209c4b8e7bdf37`.
- TrustedQSL plus OpenSSL, Expat, SQLite and zlib builds for **Android arm64-v8a / AArch64**.
- The packaged native library dynamically depends only on Android system libraries.
- TrustedQSL writable state and `config.xml` resource roots are explicit app-private directories.
- A real TQSL `.tbk` backup restored Callsign Certificates/private keys and Station Locations on the Galaxy S23 Ultra.
- **Real signing-only / NO UPLOAD PASS** produced a TQ8 on-device and rolled the TrustedQSL duplicate transaction back.
- **Real one-QSO LoTW transaction PASS** followed:
  `sign -> upload TQ8 -> accepted-QSO report verification -> TrustedQSL commit`.
- HTTP upload success alone was not treated as acceptance; commit followed accepted-QSO report verification.
- The authenticated LoTW confirmation-report query also completed successfully. The selected QSO is not required to already be confirmed by the other station.
- Sanitized device evidence passed the fail-closed evidence validator.
- Repository secret-hygiene checks remained GREEN; no TQSL backup, certificate/private-key container, LoTW password or key password is committed.
- Automatic LoTW upload remains **DISABLED**.

Approved validation APK baseline:

- source SHA: `{source_sha}`
- APK SHA-256: `{apk_sha}`
- workflow run: `{apk_run}`
- artifact id: `{apk_artifact}`

Final merged-main regression gates:

- CP-0003C TrustedQSL signer regression: **56 PASS**.
- CP-0003B transaction regression: **53 PASS**.
- inherited core: **42,062 PASS**.
- pipeline: **56 PASS**.
- inherited LoTW: **19 PASS**.
- Android arm64-v8a TrustedQSL package gate: **PASS**.
- CP-0003C secret-hygiene gate: **PASS**.
- sanitized Galaxy S23 Ultra device evidence: **PASS**.

Evidence:

- `research/tqsl/CP-0003C_DEVICE_VALIDATION.md`
- `research/tqsl/CP-0003C_DEVICE_EVIDENCE.txt`
- `research/tqsl/CP-0003C_APK_BASELINE.json`

- CP-0003C finalization workflow run: `{run_id}`

### Evidence boundary

CP-0003C proves the controlled real-device LoTW path for the validation harness and the underlying production signer/transaction components. It does **not** enable unattended upload, does not yet provide the final production FieldOps UI/database worker, and does not prove FTX-1 USB CAT/audio hardware behavior.

Automatic LoTW upload remains disabled until a later explicit product checkpoint enables it.

### Inherited verified ancestry

`CP-0003B-LOTW_TRANSACTION_SAFE`, `CP-0003A-TRUSTEDQSL_SIGNER`, `CP-0002E-NATIVE_MODE_REGRESSION`, and all prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Engineering rules",
    baseline_section + "## Engineering rules",
    "verified durable baseline",
)

readme = re.sub(
    r"- Current Git source baseline: `CP-[^\n]+`",
    "- Current Git source baseline: `CP-0003C-REAL_LOTW_VALIDATED`",
    readme,
    count=1,
)

next_section = """## Current exact next action

**CP-0004A — FTX-1 CAT USB port.**

1. Keep the hardware scope FTX-1 / FTX-1F only; do not generalize the radio driver.
2. Discover and resolve the radio's Enhanced CAT serial interface rather than selecting an arbitrary USB serial endpoint.
3. Start with RTS and DTR deasserted and guarantee that opening the CAT connection cannot key PTT.
4. Exercise CAT command/write/readback through the existing `Ftx1RadioDriver` / session boundary.
5. Treat USB detach, serial read/write failure, timeout or driver close as an emergency-RX condition.
6. Capture a first-connect diagnostic record with USB VID/PID/interface/endpoint details without logging user secrets.
7. Do not advance to USB audio work until the CAT port and disconnect-to-RX safety behavior are verified.

"""

readme = replace_one(
    readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract",
    "current exact next action",
)
readme_path.write_text(readme, encoding="utf-8")

(root / "NEXT_ACTION.md").write_text(
    """# NEXT ACTION — FTX-1 FieldOps

**CP-0004A — FTX-1 CAT USB port**

Implement and verify the FTX-1-only Android USB CAT path.

Required gates:

- resolve the actual **Enhanced CAT** serial interface, not an arbitrary USB endpoint
- RTS/DTR deasserted before/opening the connection
- no PTT side effect on connect
- CAT write/readback through the existing FTX-1 radio boundary
- USB detach/read/write failure/close forces emergency RX
- first-connect diagnostic dump records USB topology needed for later recovery
- keep the driver FTX-1-specific

Do not begin CP-0004B USB audio until CP-0004A is durably checkpointed.
""",
    encoding="utf-8",
)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text(encoding="utf-8")
if "## CP-0003C — Real LoTW validation" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0003C — Real LoTW validation

Parent: `CP-0003B-LOTW_TRANSACTION_SAFE`

Official TrustedQSL 2.8.6 was packaged for Android arm64-v8a and validated on Chris's Galaxy S23 Ultra with a real TQSL backup and one genuine LoTW QSO. Real signing-only succeeded with rollback, then the CP-0003B sign/upload/accepted-report/commit transaction succeeded. The authenticated confirmation-report query also succeeded.

Validation APK source: `{source_sha}`  
Validation APK SHA-256: `{apk_sha}`

Final gates: signer 56; transaction 53; core 42,062; pipeline 56; LoTW 19; Android package PASS; secret hygiene PASS; sanitized device evidence PASS.

Automatic LoTW upload remains disabled.

Finalization workflow run: `{run_id}`.

Evidence: `research/tqsl/CP-0003C_DEVICE_EVIDENCE.txt`.
"""
    history_path.write_text(history, encoding="utf-8")
