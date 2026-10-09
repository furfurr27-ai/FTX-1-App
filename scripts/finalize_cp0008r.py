#!/usr/bin/env python3
"""CP-0008R finalizer: promote only after full main-branch host/CI suite."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008R-PROPAGATION_OFFLINE_REPORT_IMPORT_BOUNDARY"
parent = "CP-0008Q-PROPAGATION_OFFLINE_REPORT_DECODE_VALIDATION"
next_checkpoint = "CP-0008S — propagation offline report import inspection and comparison"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v43-propagation-offline-report-import-boundary\n")

path = root / "README.md"
readme = path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Inherited host gate list unavailable")
inherited = match.group(1).strip()
baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: {parent}.

CP-0003C and CP-0004A/B/C remain deferred/incomplete hardware/account checkpoints, not part of this CI-only verification.

CP-0008R is a GREEN, offline-only, report import/read boundary. It does not import into live propagation stores.

CP-0008R proves:

- A complete V1 serialized artifact is decoded and fully validated before a detached typed import view is returned.
- Exact CP-0008P canonical bytes, CP-0008Q decoder provenance/status checks, original report timestamps, source attribution and selected evidence are preserved.
- Imported-only source/evidence ID/kind lookups never fetch providers, update caches, recompute freshness or invent absent evidence.
- An explicit integrity receipt retains media type, wire version, SHA-256, byte count, snapshot and count metadata without claiming authenticated origin or cross-store atomicity.
- Recomputed-checksum forgery remains explicitly unauthenticated; corruption, format mismatches, invalid JSON and missing evidence fail closed.
- No Android UI, filesystem/network/store mutation, real accounts/credentials, or phone/radio/RF testing.

Host/CI gates:

- CP-0008R propagation offline report import boundary: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008R_OFFLINE_REPORT_IMPORT_BOUNDARY.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportImportService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportImportTests.kt
- scripts/test_propagation_offline_report_import.sh
- CP-0008R finalization workflow run: {run_id}

### Evidence boundary

Imported diagnostic data is an offline artifact, not fresh live state. A SHA-256 checksum with no trusted key or signature cannot authenticate the source; a caller can recompute it. No authenticated provenance, cross-store transaction, provider health or device/RF assurance is claimed.

### Inherited verified ancestry

{parent} and previous verified checkpoints remain verified ancestry.

"""
readme = replace_one(readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_one(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_checkpoint, "README software track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)

next_section = f"""## Current exact next action

**{next_checkpoint}.**

1. Design a GitHub/CI-only, deterministic read-only comparison of two CP-0008R imported report artifacts, including clear missing/stale provenance and evidence identity.
2. Retain original captured/query times; do not equate an imported view with live propagation state, authenticated origin or cache atomicity.
3. Preserve the CP-0008P/Q/R strict V1/roundtrip/tamper gates and all inherited regressions.
4. CP-0003C remains DEFERRED until the owner explicitly says resume CP-0003C. CP-0004A/B/C remain incomplete.

"""
readme = replace_one(readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next")
path.write_text(readme)

(root / "NEXT_ACTION.md").write_text(f"""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return until the owner explicitly says resume CP-0003C.

Skip any checkpoint requiring phone, radio, real credentials/certificates, real accounts, RF testing or manual hardware validation. Skipped checkpoints remain incomplete.

## Next software checkpoint

**{next_checkpoint}**

Create a platform-neutral, GitHub/CI-only comparison/read-only inspection path across fully validated CP-0008R offline artifacts. Keep V1 canonical contracts and provenance, don't claim authenticated origin or cache atomicity, and preserve all prior tests.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "software parent")
track = replace_one(track,
    r"## Active software checkpoint\n\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008S — propagation offline report import inspection and comparison

Required scope:

- deterministic read-only comparison of two validated imported reports
- no credential/account/Android UI/radio/real RF tests
- preserve CP-0008P/Q/R and all inherited regression gates
- imported views are not live state; authenticity and atomicity unverified
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008R — Propagation offline report import boundary" not in history:
    history += f"""

## CP-0008R — Propagation offline report import boundary

Parent: {parent}.

A pure Kotlin import service consumes a complete CP-0008P V1 canonical report artifact, invokes CP-0008Q's strict decoder, and returns a detached typed read view with snapshot and source-attributed selected evidence lookups. The receipt explicitly marks integrity-checked data as unauthenticated, without claims of cross-store atomicity or current provider health. Absent reports and corrupted or tampered artifacts fail as specified. Deterministic synthetic focused and all inherited host/CI tests PASS. Finalizer run: {run_id}.

No live store writes, file/network calls, Android lifecycle, phone/radio/real credentials, or RF work was performed. CP-0003C remains DEFERRED; CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0008R_OFFLINE_REPORT_IMPORT_BOUNDARY.md and PropagationOfflineReportImportTests.kt.
"""
    history_path.write_text(history)
