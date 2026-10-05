#!/usr/bin/env python3
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
(root / "VERSION").write_text("v13-lotw-transaction-safe\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** `CP-0003B-LOTW_TRANSACTION_SAFE`

Parent: `CP-0003A-TRUSTEDQSL_SIGNER`.

CP-0003B is a **GREEN host/CI LoTW transaction checkpoint with RED real-account/device status**. The old one-shot signer/upload path is retired. LoTW upload now preserves the CP-0003A TrustedQSL transaction through server acceptance verification.

The verified transaction is:

`sign -> upload TQ8 -> verify every QSO in LoTW accepted report -> commit TrustedQSL duplicate state`

CP-0003B proves:

- One explicit FieldOps station profile / station callsign per batch.
- Explicit TrustedQSL station-location name, expected callsign and expected DXCC are carried into signing.
- Duplicate local QSO ids and ambiguous acceptance match keys fail before signing.
- Signing failure never uploads.
- Transport exceptions and non-2xx upload responses roll back and remain QUEUED/retryable.
- LoTW endpoint rejection rolls back and becomes REJECTED rather than silently retrying.
- HTTP upload acceptance alone is only SUBMITTED.
- Acceptance report failure or partial batch acceptance rolls back the signer transaction and never marks the batch ACCEPTED.
- Only when **all** QSOs appear in the LoTW accepted-QSO report does FieldOps commit the TrustedQSL duplicate database and mark the batch ACCEPTED.
- SSB, CW and digital QSOs use the same mode-neutral `LotwUploadQueue`.
- Rejected entries remain visible but are excluded from automatic pending/retry selection.
- Verified accepted entries leave the queue.
- TrustedQSL Kotlin session state is hardened so successful native commit/rollback becomes terminal before cleanup.
- The signer implementation itself remains isolated from HTTP transport.

Host/CI gates:

- CP-0003B transaction suite: **53 assertions PASS**.
- CP-0003A signer regression: **43 assertions PASS**.
- Inherited core: **42,062 PASS**.
- Pipeline: **56 PASS**.
- Inherited LoTW: **19 PASS**.
- Official TrustedQSL 2.8.6 archive hash/API compile remains verified by the signer regression.

Evidence:

- `research/tqsl/CP-0003B_TRANSACTION_SAFE_UPLOAD.md`
- `research/LOTW_INTEGRATION.md`

- CP-0003B finalization workflow run: `{run_id}`

### Evidence boundary

The transaction is host/CI verified with deterministic signer/transport/report fixtures. CP-0003B does **not** claim a real LoTW upload, real server acceptance, Android ARM64 TrustedQSL packaging, real certificate/private-key use, Room-backed persistence, or automatic upload.

The shared queue is now mode-neutral, but automatic SSB/CW logger-save enqueue remains the separate CP-0005B item.

### Inherited verified ancestry

`CP-0003A-TRUSTEDQSL_SIGNER`, `CP-0002E-NATIVE_MODE_REGRESSION`, and all prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Engineering rules",
    baseline + "## Engineering rules",
    "verified durable baseline",
)

readme = readme.replace(
    "- Current Git source baseline: `CP-0003A-TRUSTEDQSL_SIGNER`",
    "- Current Git source baseline: `CP-0003B-LOTW_TRANSACTION_SAFE`",
    1,
)

next_section = """## Current exact next action

**CP-0003C — Real LoTW validation.**

1. Build/package the official TrustedQSL dependency stack for Android arm64-v8a.
2. Load it on the Galaxy S23 Ultra and import a real test Callsign Certificate/PKCS#12 through the production secret-storage boundary.
3. Use a controlled test QSO/account flow to produce a real signed TQ8.
4. Exercise the CP-0003B transaction against LoTW: sign -> upload -> verify accepted-QSO report -> commit.
5. Run confirmation sync and verify the local confirmation metadata/cursors.
6. Confirm no certificate/password/private-key material appears in logs, crash output, support bundles or repository files.
7. Keep automatic upload disabled unless every CP-0003C device/test-account gate passes.

"""

readme = replace_one(
    readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract",
    "current exact next action",
)
readme_path.write_text(readme)

(root / "NEXT_ACTION.md").write_text(
    """# NEXT ACTION — FTX-1 FieldOps

**CP-0003C — Real LoTW validation**

Build/package official TrustedQSL for Android arm64-v8a, load it on the Galaxy S23 Ultra, import a controlled real Callsign Certificate/PKCS#12, create a real signed TQ8, and run the CP-0003B transaction against a controlled LoTW test QSO:

`sign -> upload -> verify accepted-QSO report -> commit`

Then verify confirmation sync/cursors and secret hygiene. Automatic upload stays disabled unless every CP-0003C device/test-account gate passes.
"""
)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0003B — Transaction-safe LoTW upload" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0003B — Transaction-safe LoTW upload

Parent: `CP-0003A-TRUSTEDQSL_SIGNER`

FieldOps now preserves the TrustedQSL duplicate-database transaction across the entire upload/verification flow. HTTP upload acceptance is SUBMITTED only; every QSO in the batch must appear in the LoTW accepted-QSO report before TrustedQSL state commits and local QSOs become ACCEPTED.

Signer/network/non-2xx/report-verification failures roll back. Endpoint rejection rolls back and remains visible without automatic retry. SSB, CW and digital QSOs share the same mode-neutral queue/state machine; automatic manual-logger enqueue remains CP-0005B.

Host/CI gate: transaction 53; signer 43; core 42,062; pipeline 56; LoTW 19 assertions, all PASS.

Finalization workflow run: `{run_id}`.

Evidence: `research/tqsl/CP-0003B_TRANSACTION_SAFE_UPLOAD.md`.
"""
    history_path.write_text(history)
