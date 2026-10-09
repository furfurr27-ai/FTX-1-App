#!/usr/bin/env python3
"""CP-0008S finalizer: promote only after full main-branch host/CI suite."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008S-PROPAGATION_OFFLINE_REPORT_INSPECTION_COMPARISON"
parent = "CP-0008R-PROPAGATION_OFFLINE_REPORT_IMPORT_BOUNDARY"
next_checkpoint = "CP-0008T — offline propagation report comparison export contract"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v44-propagation-offline-report-inspection-comparison\n")

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

CP-0008S is a GREEN offline-only, deterministic inspection/comparison of two fully validated imported reports.

CP-0008S proves:

- Both complete canonical V1 artifacts are validated before comparison, with mutated previously imported DTOs rejected against their receipts.
- Sorted source/evidence identity changes distinguish added/removed/changed/unchanged in *selected views*, without inferring provider deletion or worsening RF.
- Complete selected projection payloads and provenance indices are compared, preserving changes that share unchanged evidence IDs.
- Original query times, snapshot/summary and selected assessment change flags are preserved without reinterpreting freshness or consulting clocks/providers.
- Integrity receipts remain explicitly unauthenticated and cannot assert atomicity or RF/device proof.
- Focused Kotlin host/CI and inherited regression tests prove a pure offline read path.

Host/CI gates:

- CP-0008S offline imported report inspection and comparison: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008S_OFFLINE_REPORT_INSPECTION_COMPARISON.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonTests.kt
- scripts/test_propagation_offline_report_comparison.sh
- CP-0008S finalization workflow run: {run_id}

### Evidence boundary

A selected-view removal may be caused by different filters; it does not prove provider deletion or changed RF conditions. Checksums are not authenticated signatures. No source-origin assurance, live store atomicity, real device, or RF proof is claimed.

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

1. Design a GitHub/CI-only canonical serialization/export contract for CP-0008S comparison results.
2. Preserve original V1 report receipts, missing-view caveats and change metadata; never imply live RF, authentic origin, or atomicity.
3. Retain CP-0008P/Q/R/S strict gates and inherited regressions.
4. CP-0003C remains DEFERRED until owner resume; CP-0004A/B/C incomplete.
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

Build a platform-neutral, versioned canonical export contract for CP-0008S offline comparison results. Preserve both report receipts, selected-view caveats, original timestamps, source provenance and all inherited regression tests.
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

CP-0008T — offline propagation report comparison export contract

Required scope:

- deterministic canonical export of validated CP-0008S comparison output
- no credential/account/Android UI/radio/real RF tests
- preserve CP-0008P/Q/R/S and all inherited regression gates
- imported views are not live state; authenticity and atomicity unverified
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008S — Offline imported report inspection and comparison" not in history:
    history += f"""

## CP-0008S — Offline imported report inspection and comparison

Parent: {parent}.

A pure Kotlin service compares two separately validated CP-0008R imported canonical reports, retaining original receipt and query-time provenance, source-status and selected-evidence identity. Full projection values are compared independently of evidence indices. Missing selected evidence means absent from a report view, not deleted from the live provider. Synthetic focused and inherited host/CI tests PASS. Finalizer run: {run_id}.

No authenticated origin, cross-store atomicity, live store I/O, Android/radio/real credentials, or RF tests. CP-0003C remains DEFERRED, CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0008S_OFFLINE_REPORT_INSPECTION_COMPARISON.md and PropagationOfflineReportComparisonTests.kt.
"""
    history_path.write_text(history)
