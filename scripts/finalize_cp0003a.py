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
(root / "VERSION").write_text("v12-trustedqsl-signer-bridge\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** `CP-0003A-TRUSTEDQSL_SIGNER`

Parent: `CP-0002E-NATIVE_MODE_REGRESSION`.

CP-0003A is a **GREEN host/CI TrustedQSL signer-bridge checkpoint with YELLOW Android/native-runtime and RED real-account status**. It pins the official TrustedQSL release, compiles the production FieldOps JNI bridge directly against that official API, and adds a transactional signing boundary without prematurely coupling signing to LoTW HTTP upload.

CP-0003A adds and proves:

- Official TrustedQSL build pin: **2.8.6**, `tqsl-2.8.6.tar.gz`.
- SourceForge-published archive SHA-256:
  `182e5f2ac35a3db8b409b45d96505e6bd265ae4668ed064754209c4b8e7bdf37`.
- The production JNI source compiles against the exact `tqsllib.h` and `tqslconvert.h` from that verified release.
- Current official SourceForge master reviewed for API continuity at `78a143276e9bde53b2c9535839cc19ab45b686f0`; the stable build pin remains release 2.8.6.
- In-memory PKCS#12 import goes through official tqsllib Base64/import APIs.
- TrustedQSL station location is explicit and must match the expected FieldOps station callsign and DXCC before signing.
- Certificate selection and private-key signing initialization remain inside official tqsllib calls.
- ADIF conversion uses the official converter/GABBI API with duplicate tracking enabled and QTH behavior fixed to REPORT rather than UPDATE.
- Signed GABBI is packaged as compressed `.tq8` output.
- Core now exposes `TransactionalLotwSigner` / `LotwSigningSession`.
- A new signing session is OPEN and does **not** automatically commit TrustedQSL's duplicate database.
- Explicit `commit()` and `rollback()` are retained so CP-0003B can make the network transaction atomic.
- Closing an OPEN session rolls back.
- An unavailable signer, missing location, callsign mismatch, DXCC mismatch, missing certificate, or signing-init error fails closed.
- Transient PKCS#12/password copies are zero-filled and raw tqsllib error strings are not surfaced through the FieldOps bridge.
- The existing `LotwSyncManager.upload()` is deliberately unchanged in CP-0003A so signing and network upload remain separate.
- Focused signer bridge tests pass **43/43**.
- Inherited core regression remains **42,062 core, 56 pipeline, 19 LoTW assertions PASS**.

Exact source/API and test evidence:

- `research/tqsl/CP-0003A_SOURCE_PIN.md`
- `research/tqsl/CP-0003A_SIGNER_BRIDGE.md`

- CP-0003A finalization workflow run: `{run_id}`

### Evidence boundary

The runtime CI fixture implements the exact API subset deterministically so JNI lifecycle and transaction behavior can run on x86_64. It is **not** a substitute for real TrustedQSL cryptography.

Still unverified:

- complete official TrustedQSL + dependency build/link for Android arm64-v8a
- runtime loading on the Galaxy S23 Ultra
- Chris's real Callsign Certificate / PKCS#12 import
- real private-key cryptographic signing
- LoTW server acceptance
- transaction-safe sign -> upload -> verify -> commit behavior
- automatic upload

Those claims remain later checkpoints. CP-0003B owns transaction-safe upload integration; CP-0003C owns real device/test-account validation.

### Inherited verified ancestry

`CP-0002E-NATIVE_MODE_REGRESSION` and all prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Engineering rules",
    baseline + "## Engineering rules",
    "verified durable baseline",
)

readme = readme.replace(
    "- Current Git source baseline: `CP-0002E-NATIVE_MODE_REGRESSION`",
    "- Current Git source baseline: `CP-0003A-TRUSTEDQSL_SIGNER`",
    1,
)

next_section = """## Current exact next action

**CP-0003B — Transaction-safe LoTW upload.**

1. Replace the legacy one-shot signer/upload path with the CP-0003A transactional signer session.
2. Execute the sequence: sign -> upload TQ8 -> verify LoTW acceptance -> commit TrustedQSL duplicate state.
3. If signing, network upload, server rejection, or acceptance verification fails, roll back the signer transaction and do not mark local duplicate/upload state as committed.
4. Keep HTTP upload success distinct from LoTW QSO acceptance; only report ACCEPTED after the QSO appears in the acceptance report.
5. Route manual SSB/CW and digital QSOs through the same local queue and transaction rules.
6. Preserve explicit station-profile/location binding and all secret-handling rules.
7. Automatic upload remains disabled until CP-0003C real device/test-account validation passes.

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

**CP-0003B — Transaction-safe LoTW upload**

Migrate the upload path to the CP-0003A transactional signer session:

`sign -> upload TQ8 -> verify LoTW acceptance -> commit TrustedQSL duplicate state`

Any signing/network/rejection/verification failure must roll back the signer transaction and must not mark the QSO accepted locally. HTTP upload success alone is not LoTW acceptance.

Manual SSB/CW and digital QSOs use the same queue and transaction rules. Automatic upload remains disabled until CP-0003C real device/test-account validation.
"""
)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0003A — TrustedQSL signer bridge" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0003A — TrustedQSL signer bridge

Parent: `CP-0002E-NATIVE_MODE_REGRESSION`

Official TrustedQSL 2.8.6 is pinned by SourceForge SHA-256 and the production JNI bridge compiles directly against its exact headers. FieldOps now has a fail-closed transactional signing boundary for PKCS#12 import, explicit station-location/callsign/DXCC validation, official ADIF/GABBI conversion calls, compressed TQ8 output, and explicit TrustedQSL duplicate-state commit/rollback.

CP-0003A intentionally does not connect that transaction to the network upload path yet. Runtime CI uses an exact-API deterministic fixture; real TrustedQSL Android ARM64 linking, real certificate cryptography, and LoTW acceptance remain unverified.

Host/CI gate: signer 43; inherited core 42,062; pipeline 56; LoTW 19 assertions, all PASS.

Finalization workflow run: `{run_id}`.

Evidence: `research/tqsl/CP-0003A_SOURCE_PIN.md` and `research/tqsl/CP-0003A_SIGNER_BRIDGE.md`.
"""
    history_path.write_text(history)
