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
(root / "VERSION").write_text("v14-universal-qso-logger\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** `CP-0005A-UNIVERSAL_QSO_LOGGER`

Parent durable checkpoint: `CP-0003B-LOTW_TRANSACTION_SAFE`.

`CP-0003C` and `CP-0004A/B/C` remain explicitly deferred hardware/account checkpoints and are **not** implied complete by this software checkpoint.

CP-0005A is a **GREEN host/CI universal-logbook checkpoint**. The existing `QsoRecord` used by the verified LoTW transaction remains the authoritative local QSO model and has been extended rather than replaced.

CP-0005A proves:

- Exact ADIF MODE and SUBMODE are preserved independently.
- Exact physical/radio mode is preserved separately from ADIF identity.
- Exact frequency is retained in Hz with a compatible MHz export representation.
- Band, UTC start/end, callsign, sent/received reports, remote/station grid and location snapshots are retained.
- Station profile id and operating session id are first-class QSO fields.
- `OperatingSession` captures session UTC, station identity/location, radio, antenna notes, default power and activity tags.
- `FastQsoLogger` creates manual SSB and CW QSOs from current session/radio context with minimal contact-specific input.
- Manual logging does not fabricate RST values.
- SSB can retain a physical mode such as USB/LSB while ADIF remains MODE=SSB.
- Digital completed-contact adapters feed the same QSO model and must supply exact mode/submode rather than relying on lossy normalization.
- Incomplete digital contacts are rejected from auto-log.
- ADIF export now preserves MODE, SUBMODE, exact FREQ, UTC end fields, grids and reports.
- Legacy CP-0003B QSO constructors remain compatible.
- New exact/legacy grid and frequency fields fail closed if contradictory.
- `LogbookRepository` is the local-storage contract; the host checkpoint uses an in-memory implementation while Room remains later app-shell work.
- CP-0005A leaves LoTW state at NOT_UPLOADED. Automatic manual-QSO LoTW enqueue remains CP-0005B.

Host/CI gates:

- CP-0005A universal QSO/logger: **78/78 PASS**.
- CP-0003B LoTW transaction regression: **53/53 PASS**.
- Inherited core: **42,062 PASS**.
- Pipeline: **56 PASS**.
- Inherited LoTW: **19 PASS**.

Evidence:

- `research/logbook/CP-0005A_UNIVERSAL_QSO_LOGGER.md`
- `SOFTWARE_TRACK.md`

- CP-0005A finalization workflow run: `{run_id}`

### Evidence boundary

CP-0005A is host/CI software evidence. It does not claim Room persistence, Compose UI, live FTX-1 state capture, hardware CAT/audio behavior, automatic LoTW enqueue, or real LoTW/device validation.

### Inherited verified ancestry

`CP-0003B-LOTW_TRANSACTION_SAFE`, `CP-0003A-TRUSTEDQSL_SIGNER`, `CP-0002E-NATIVE_MODE_REGRESSION`, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = readme.replace(
    "- Current Git source baseline: `CP-0003B-LOTW_TRANSACTION_SAFE`",
    "- Current Git source baseline: `CP-0005A-UNIVERSAL_QSO_LOGGER`",
    1,
)

next_section = """## Current exact next action

**CP-0005B — Manual-QSO LoTW queue.**

1. Add an explicit logger policy controlling whether newly saved local QSOs enter the LoTW queue automatically.
2. When enabled, successful SSB/CW manual saves and eligible digital saves must enter the same verified `LotwUploadQueue` state machine.
3. Saving the authoritative local QSO must not depend on network availability or LoTW success.
4. Queue insertion must be idempotent by immutable local QSO id.
5. A disabled policy must leave the QSO local-only / NOT_UPLOADED.
6. Preserve station-profile/session binding so later signing selects the correct TrustedQSL station location.
7. Do not enable real automatic network upload; CP-0003C remains required before that device/account behavior can be enabled.

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

**CP-0005B — Manual-QSO LoTW queue**

Add a logger policy that, when enabled, automatically places successfully saved local SSB/CW and eligible digital QSOs into the existing `LotwUploadQueue` by immutable QSO id.

Local logging remains authoritative and must succeed independently of network/LoTW status. Disabled policy leaves the QSO NOT_UPLOADED. Do not enable real automatic network upload; CP-0003C remains hardware/account gated.
"""
)

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n`[^`]+`",
    "## Latest verified durable parent\n\n`CP-0005A-UNIVERSAL_QSO_LOGGER`",
    track,
    count=1,
)
track = re.sub(
    r"## Active software checkpoint\n\n`[^`]+`\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

`CP-0005B-MANUAL_QSO_LOTW_QUEUE`

Required scope:

- optional logger-save -> LoTW queue policy
- SSB/CW and eligible digital QSOs use the same queue
- local log save remains independent of LoTW/network success
- idempotent queue insertion by immutable QSO id
- disabled policy leaves local QSO NOT_UPLOADED
- no real automatic network upload until deferred CP-0003C passes

## Resume rule""",
    track,
    count=1,
    flags=re.S,
)
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0005A — Universal QSO model + fast logger" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0005A — Universal QSO model + fast logger

Parent durable checkpoint: `CP-0003B-LOTW_TRANSACTION_SAFE`.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete.

The existing LoTW-compatible `QsoRecord` is now the universal local QSO model. It preserves exact ADIF mode/submode, physical radio mode, exact frequency, band, UTC start/end, reports, station/remote location, station profile and operating session. `FastQsoLogger` adds manual SSB/CW logging and completed-contact digital auto-log adapters into the same authoritative repository contract.

ADIF export preserves the new exact identity/frequency fields. Automatic logger-save -> LoTW enqueue is intentionally left for CP-0005B.

Host/CI gate: logger 78; LoTW transaction 53; core 42,062; pipeline 56; LoTW 19 assertions, all PASS.

Finalization workflow run: `{run_id}`.

Evidence: `research/logbook/CP-0005A_UNIVERSAL_QSO_LOGGER.md`.
"""
    history_path.write_text(history)
