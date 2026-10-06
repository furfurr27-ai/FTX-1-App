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
(root / "VERSION").write_text("v15-manual-qso-lotw-queue\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0005B-MANUAL_QSO_LOTW_QUEUE

Parent durable checkpoint: CP-0005A-UNIVERSAL_QSO_LOGGER.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0005B is a GREEN host/CI local LoTW-queue checkpoint. It connects successful local SSB/CW and eligible completed-digital saves to the existing transaction-safe LoTW queue without giving the logger any signing or network ownership.

CP-0005B proves:

- LotwLoggerPolicy explicitly controls logger-save to local LoTW queue behavior.
- Disabled policy leaves the authoritative saved QSO NOT_UPLOADED.
- Enabled policy auto-queues manual SSB, manual CW and completed digital-auto QSOs.
- Local QSO persistence happens before any queue action.
- A local queue failure cannot erase or fail the authoritative QSO save.
- Successful local enqueue updates the authoritative QSO sync state to QUEUED.
- Queue failures are surfaced separately through LotwQueueFailureHandler.
- LotwUploadQueue.enqueue() is idempotent by immutable local QSO id.
- Re-enqueueing the same QSO does not duplicate the entry or reset attempt/state.
- Reusing an existing immutable QSO id for a materially different contact or station profile fails closed.
- Queue entries preserve station-profile id, operating-session id, station callsign, exact mode/submode and digital provider identity.
- SSB, CW and digital QSOs use the same local LotwUploadQueue.
- The logger path contains no LoTW HTTP/signing transaction call and cannot start a real upload.
- Real automatic network upload remains disabled until deferred CP-0003C passes.

Host/CI gates:

- CP-0005B manual/digital LoTW queue: **47/47 PASS**.
- CP-0005A universal QSO/logger: **78/78 PASS**.
- CP-0003B LoTW transaction: **53/53 PASS**.
- Inherited core: **42,062 PASS**.
- Pipeline: **56 PASS**.
- Inherited LoTW: **19 PASS**.

Evidence:

- research/logbook/CP-0005B_MANUAL_QSO_LOTW_QUEUE.md
- SOFTWARE_TRACK.md

- CP-0005B finalization workflow run: {run_id}

### Evidence boundary

CP-0005B is host/CI software evidence. It does not claim Room persistence, Compose queue UI, live automatic LoTW network upload, real LoTW certificate/account/device validation, or FTX-1 Android hardware proof.

### Inherited verified ancestry

CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = readme.replace(
    "- Current Git source baseline: CP-0005A-UNIVERSAL_QSO_LOGGER",
    "- Current Git source baseline: CP-0005B-MANUAL_QSO_LOTW_QUEUE",
    1,
)
readme = readme.replace(
    "- Current Git source baseline: `CP-0005A-UNIVERSAL_QSO_LOGGER`",
    "- Current Git source baseline: `CP-0005B-MANUAL_QSO_LOTW_QUEUE`",
    1,
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0006A — Award evaluation engine",
    readme,
    count=1,
)

next_section = """## Current exact next action

**CP-0006A — Award evaluation engine.**

1. Add a provider-independent award-domain model with explicit WORKED, CONFIRMED, THRESHOLD_MET and OFFICIALLY_CLAIMABLE states.
2. Preserve exact QSO mode/submode while adding a separate controlled award-mode grouping layer.
3. Support band-scoped and all-band evaluation without rewriting the authoritative QSO record.
4. Keep confirmation source/evidence explicit; a worked QSO is not automatically confirmed.
5. Keep local threshold completion distinct from official sponsor claimability/credit.
6. Build the engine against synthetic/generic award definitions only in CP-0006A.
7. Do not encode guessed official award requirements; verified official rules and claim URLs belong to CP-0006B.

"""

readme = replace_one(
    readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract",
    "current exact next action",
)
readme_path.write_text(readme)

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

**CP-0006A — Award evaluation engine**

Create the provider-independent award-domain/evaluator layer with distinct WORKED, CONFIRMED, THRESHOLD_MET and OFFICIALLY_CLAIMABLE states.

Preserve exact QSO MODE/SUBMODE and add a separate controlled award-mode grouping layer. Support band and all-band evaluation, keep confirmation evidence explicit, and do not treat local threshold completion as official sponsor claimability.

Use synthetic/generic award definitions only. Official award rules and claim URLs must be verified separately in CP-0006B.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n`[^`]+`",
    "## Latest verified durable parent\n\n`CP-0005B-MANUAL_QSO_LOTW_QUEUE`",
    track,
    count=1,
)
track = re.sub(
    r"## Active software checkpoint\n\n`[^`]+`\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

`CP-0006A-AWARD_EVALUATION_ENGINE`

Required scope:

- explicit WORKED / CONFIRMED / THRESHOLD_MET / OFFICIALLY_CLAIMABLE award states
- controlled award-mode grouping without losing exact QSO mode/submode
- band-scoped and all-band evaluation
- explicit confirmation/evidence provenance
- generic/synthetic definitions only in this checkpoint
- no guessed official award requirements; official catalog work remains CP-0006B

## Resume rule""",
    track,
    count=1,
    flags=re.S,
)
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0005B — Manual/digital QSO LoTW queue" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0005B — Manual/digital QSO LoTW queue

Parent durable checkpoint: CP-0005A-UNIVERSAL_QSO_LOGGER.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete.

FastQsoLogger now has an explicit LoTW auto-queue policy. When enabled, successful local SSB/CW and eligible completed-digital saves enter the same local LotwUploadQueue; the authoritative local save always occurs first. Successful enqueue mirrors QUEUED back to the stored QSO, while queue failure leaves the already-saved QSO NOT_UPLOADED and reports the queue failure separately.

LotwUploadQueue is idempotent by immutable local QSO id, preserves retry state on repeated enqueue, and rejects conflicting reuse of an id/profile. The logger path remains network-free; real automatic LoTW upload is still gated by CP-0003C.

Host/CI gate: queue 47; universal logger 78; LoTW transaction 53; core 42,062; pipeline 56; LoTW 19 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/logbook/CP-0005B_MANUAL_QSO_LOTW_QUEUE.md.
"""
    history_path.write_text(history)

verify_workflow = """name: Verify CP-0005B checkpoint

on:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Verify immutable CP-0005B snapshot
        run: python3 scripts/verify_checkpoint.py --root . --id CP-0005B-MANUAL_QSO_LOTW_QUEUE
"""
(root / ".github" / "workflows" / "finalize-cp0005b.yml").write_text(verify_workflow)
