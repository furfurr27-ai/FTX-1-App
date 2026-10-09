#!/usr/bin/env python3
"""CP-0008U promotion on main only, after full decoder and inherited test matrix."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008V-PROPAGATION_OFFLINE_COMPARISON_IMPORT_INSPECTION"
parent = "CP-0008U-PROPAGATION_OFFLINE_COMPARISON_EXPORT_DECODE_VALIDATION"
next_checkpoint = "CP-0008W — next GitHub/CI-only propagation software checkpoint"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v47-propagation-offline-comparison-import-inspection\n")

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

CP-0008V is a GREEN host-only import and read-only historical inspection boundary for canonical CP-0008T/U comparison exports.

CP-0008V proves:

- Complete versioned CP-0008U canonical strict typed decode is required; no bare JSON import or unsupported trust assertions.
- Detached typed source/evidence comparison rows, both original receipts, query order, source attribution and selected-view classifications are retained for read-only inspection.
- Source/evidence lookup, kind and classification filters, historical change counts and unmodifiable outer list snapshots.
- Optional verification against both complete CP-0008P original report artifacts detects independently falsified derivative comparison flags even when SHA-256 is recomputed.
- CP-0008P/Q/R/S/T/U interfaces and full inherited host regression matrix remain GREEN.
- The derivative remains unauthenticated, cannot independently recheck full projection evidence without original reports and is never live RF/provider status.

Host/CI gates:

- CP-0008V offline comparison import and inspection boundary: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008V_OFFLINE_COMPARISON_IMPORT_INSPECTION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonImportService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonImportTests.kt
- scripts/test_propagation_offline_comparison_import.sh
- CP-0008V finalization workflow run: {run_id}

### Evidence boundary

SHA-256 is not authenticated origin or a signature. ADDED_TO_VIEW and REMOVED_FROM_VIEW express selected report view membership only. Kotlin DTOs are not deep immutable under unsafe casts. Comparison is a derivative without full projections; no cross-store atomicity, real phone/radio/RF proof, live provider status or real account validation.

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

1. Inspect the GitHub/CI-only propagation roadmap and select the next independent software checkpoint following CP-0008V.
2. Preserve canonical CP-0008P/Q/R/S/T/U/V, detached imported views and historic source/evidence semantics.
3. Continue without inventing signatures, authenticated provenance, cross-store atomicity or live RF status.
4. CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.
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

Inspect the current propagation software roadmap and select the next fully GitHub/CI-only task after the completed CP-0008V import/inspection boundary. Preserve all prior host verified CP-0008P/Q/R/S/T/U/V interfaces and do not claim live or authenticated RF status.
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

CP-0008W — next GitHub/CI-only propagation software checkpoint

Required scope:

- Define next unblocked host-only item from current propagation roadmap
- Preserve CP-0008P/Q/R/S/T/U/V contracts and inherited regression suite
- No authenticated origin, cross-store atomicity, live provider/RF or device/account claim
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008V — Offline comparison import and inspection" not in history:
    history += f"""

## CP-0008V — Offline comparison import and inspection

Parent: {parent}.

Validated versioned canonical CP-0008T/U comparison import boundary with explicit unkeyed integrity receipt and both original report receipts. Read-only historical selection lookups, filter and change-count APIs. Optional strict original V1 report recomputation detects forged derivative change flags when both original reports are present. Full host focused and inherited CI PASS; finalizer run: {run_id}.

No signature/authenticated origin, cross-store atomicity, live provider/RF proof, phone/radio, real accounts or hardware tests. CP-0003C remains DEFERRED; CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0008V_OFFLINE_COMPARISON_IMPORT_INSPECTION.md and PropagationOfflineReportComparisonImportTests.kt.
"""
    history_path.write_text(history)
