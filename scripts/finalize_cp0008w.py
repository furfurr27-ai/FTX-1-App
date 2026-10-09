#!/usr/bin/env python3
"""CP-0008W promotion after main-branch full CI and snapshot verification."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008W-PROPAGATION_OFFLINE_COMPARISON_PRESENTATION"
parent = "CP-0008V-PROPAGATION_OFFLINE_COMPARISON_IMPORT_INSPECTION"
next_checkpoint = "CP-0008X — bounded historical comparison presentation filters and pagination"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v48-propagation-offline-comparison-presentation\n")

path = root / "README.md"
readme = path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Inherited host gate list unavailable")
inherited = match.group(1).strip()
baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: {parent}.

CP-0003C and CP-0004A/B/C remain deferred/incomplete hardware/account checkpoints, not part of this GitHub/CI-only verification.

CP-0008W is a GREEN host-only historical offline comparison presentation read model.

CP-0008W proves:

- Validated canonical comparison imports project to deterministic presentation DTOs without Android, store mutation or live provider calls.
- Both original report receipts, comparison receipt, UTC query order, source status/timing, evidence attribution and selected-view change classes remain available.
- Added/removed/changed/unchanged counts, stable sorted source and selected-evidence rows, identity/kind/classification filters, distinct metadata/projection-change flags.
- Explicit original-report reconciliation: not supplied, fully consistent, or mismatch when both original canonical V1 reports are available.
- Strict CP-0008P/Q/R/S/T/U/V interfaces and inherited Kotlin/GitHub Actions regression matrix remain GREEN.
- Unkeyed SHA-256 proves integrity only; original reports are not authenticated provider provenance, live propagation, or cross-store atomicity.

Host/CI gates:

- CP-0008W offline comparison presentation read model: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008W_OFFLINE_COMPARISON_PRESENTATION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonPresentationService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonPresentationTests.kt
- scripts/test_propagation_offline_comparison_presentation.sh
- CP-0008W finalization workflow run: {run_id}

### Evidence boundary

ADDED_TO_VIEW and REMOVED_FROM_VIEW are selected-view changes only. SHA-256 is not authenticated origin. A complete historical comparison has no live RF/provider verification, transactional source-store atomicity, or phone/radio hardware proof. Nested Kotlin DTOs are not transitively immutable against unsafe casts.

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

1. Add deterministic bounded pagination/filters above the CP-0008W historical comparison presentation read model.
2. Preserve CP-0008P/Q/R/S/T/U/V/W receipts, comparisons, selected-view classifications, and host regression checks.
3. No live provider status, RF truth, authenticated origin, cross-store atomicity, or device/account tests.
4. CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.

"""
readme = replace_one(readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next")
path.write_text(readme)

(root / "NEXT_ACTION.md").write_text(f"""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return until the owner explicitly says resume CP-0003C.

Skip checkpoints requiring the phone, radio, real accounts, credentials/certificates, RF or manual hardware validation. Skipped checkpoints remain incomplete.

## Next software checkpoint

**{next_checkpoint}**

Implement a deterministic, bounded historical comparison presentation pagination/filtering layer. Preserve the original receipts, selected-view semantics, CP-0008P/Q/R/S/T/U/V/W contracts and inherited CI matrix. No authenticated origin or live RF claims.
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

CP-0008X — bounded historical offline comparison presentation filters and pagination

Required scope:

- deterministic GitHub/CI-only filter/pagination over validated CP-0008W projection
- preserve all CP-0008P/Q/R/S/T/U/V/W receipt and classification interfaces
- no authenticated provenance, cross-store atomicity, live source/RF, phone or account claims
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008W — Offline comparison presentation read model" not in history:
    history += f"""

## CP-0008W — Offline comparison presentation read model

Parent: {parent}.

Platform-neutral deterministic historical presentation over validated CP-0008V imported comparison records, preserving original receipts, time order, source/evidence identities, selected change categories, metadata versus full-projection flags, source/evidence counts and filters. Optional full original report reconciliation remains explicit and unauthenticated. Focused and full inherited GitHub/CI regressions PASS. Finalizer run: {run_id}.

No authenticated provider origin, cross-store atomicity, live status, Android device/RF/account validation. CP-0003C remains DEFERRED; CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0008W_OFFLINE_COMPARISON_PRESENTATION.md and PropagationOfflineReportComparisonPresentationTests.kt.
"""
    history_path.write_text(history)
