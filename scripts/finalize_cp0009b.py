#!/usr/bin/env python3
"""Promote CP-0009B only after complete merged-main Actions verification."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009B-PROPAGATION_OFFLINE_REPORT_ARCHIVE"
parent = "CP-0009A-PROPAGATION_WORKSPACE_HISTORY_INTEGRATION"
next_label = "CP-0009C — archive history selection and Android build-readiness integration"

def replace_one(source, pattern, replacement, label):
    result, hits = re.subn(pattern, lambda _: replacement, source, count=1, flags=re.S)
    if hits != 1:
        raise SystemExit("Missing or duplicated " + label + ": " + str(hits))
    return result

(root / "VERSION").write_text("v53-propagation-offline-report-archive\n")
readme_file = root / "README.md"
readme = readme_file.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Prior host gate ancestry missing")
inherited = match.group(1).strip()
baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: {parent}.

CP-0003C and CP-0004A/B/C remain deferred/incomplete hardware/account checkpoints.

CP-0009B is a GREEN host-only bounded archive/store contract above CP-0009A.

CP-0009B proves:

- A deterministic caller-retained canonical offline report history value; FIFO eviction by insertion order, count and UTF-8 byte budget.
- Idempotent SHA-256 content key duplicates; canonical imported receipts reverified on every archive operation.
- Explicit removal, stable query UTC-descending paginated selection, and original-paired CP-0009A historical comparison.
- Rejects tampering, forged receipt/trust, invalid retention/page limits and missing selected captures.
- CP-0009A and the full inherited CP-0008P/Q/R/S/T/U/V/W/X/Y/Z host regression matrix remain GREEN.
- No Android filesystem/DB persistence, trusted origin, cross-store atomicity or live RF claims.

Host/CI gates:

- CP-0009B offline propagation report archive/store contract: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0009B_OFFLINE_ARCHIVE_CONTRACT.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportArchiveService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportArchiveTests.kt
- scripts/test_propagation_offline_archive.sh
- CP-0009B main finalization workflow run: {run_id}

### Evidence boundary

Only host-only, caller-owned in-memory archive behavior is proven. Content SHA-256 is not authenticated provenance; no Android storage, source-store atomicity, phone/radio/USB/RF, real account or certificate verification.

### Inherited verified ancestry

{parent} and older verified checkpoints remain ancestry.

"""
readme = replace_one(readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_one(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_label, "README software track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
section = f"""## Current exact next action

**{next_label}.**

1. Wire canonical bounded archive selection and history controls into the application workspace; add GitHub/CI-only Android build-readiness smoke checks.
2. Preserve CP-0009B canonical receipts, bounded retention, CP-0009A original-paired comparison, warnings and inherited regression gates.
3. Do not claim Android on-device persistence, authenticated origin, RF operation or hardware/account verification.
4. CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.

"""
readme = replace_one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
    section + "## README maintenance contract", "README next")
readme_file.write_text(readme)

(root / "NEXT_ACTION.md").write_text(f"""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED**. Do not resume until explicitly instructed 'resume CP-0003C'.
Skip any checkpoint requiring actual phone, FTX-1, credentials, accounts, certificates, RF or manual hardware validation. CP-0004A/B/C remain incomplete.

## Next software checkpoint

**{next_label}**

Integrate the bounded offline archive selection and history controls with the Android app workspace where possible through GitHub/CI. Validate Android build readiness without claiming successful physical install or device/radio operation. Preserve CP-0009A/B and inherited CI contracts.
""")
track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(track, r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "software track parent")
track = replace_one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
    f"""## Active software checkpoint

{next_label}

Required scope:

- integrate CP-0009B offline archive selection/history UX with accessible workspace and host/Android CI checks
- preserve canonical report import/receipts and CP-0009A historical comparison fidelity
- no actual device/DB persistence, authenticated origin or real RF/device/account verification
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software track next")
track_path.write_text(track)

history = root / "checkpoints/CHECKPOINT_HISTORY.md"
h = history.read_text()
if "## CP-0009B — Bounded offline report archive" not in h:
    h += f"""

## CP-0009B — Bounded offline report archive

Parent: {parent}. Canonical, caller-retained archive values validate every retained report and receipt on access; deterministic count/byte FIFO eviction, idempotent content keys, UTC-based stable history paging, explicit removal and original-paired CP-0009A comparison. Host test and inherited complete CI matrix GREEN; finalizer {run_id}. This is not Android database persistence, provider signature authentication, live RF or physical hardware proof. CP-0003C DEFERRED and CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0009B_OFFLINE_ARCHIVE_CONTRACT.md, PropagationOfflineReportArchiveTests.kt.
"""
    history.write_text(h)
