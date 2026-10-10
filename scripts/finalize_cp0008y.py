#!/usr/bin/env python3
"""CP-0008Y promotion after full host CI, guarded main checkpoint and snapshot verification."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008Y-PROPAGATION_OFFLINE_COMPARISON_DISPLAY_CONTRACT"
parent = "CP-0008X-PROPAGATION_OFFLINE_COMPARISON_PAGINATION"
next_checkpoint = "CP-0008Z — offline comparison accessibility and host interaction state contract"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v50-propagation-offline-comparison-display-contract\n")

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

CP-0008Y is a GREEN host-only historical paged comparison display integration contract.

CP-0008Y proves:

- Strict CP-0008X canonical artifact pagination feeds a deterministic platform-neutral historical display model, not a live Android screen.
- Separate bounded source and evidence sections, selected-view-only change labels, original report/comparison receipts, query UTC order and unfiltered global change tallies.
- Typed previous/next page queries preserve page sizes and exact case-sensitive source/evidence change, kind and prefix filters.
- Four fixed mandatory warnings about historical (not live) data, selected-view-only changes, unkeyed checksum origin limits and unverified cross-store/hardware claims.
- Optional paired original-report consistency labels never assert authenticated provider origin, source-store atomicity or observed RF state.
- CP-0008P/Q/R/S/T/U/V/W/X APIs and the full inherited Kotlin host-only regression suite remain GREEN.

Host/CI gates:

- CP-0008Y offline comparison display integration: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008Y_OFFLINE_COMPARISON_DISPLAY_CONTRACT.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonDisplayService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonDisplayTests.kt
- scripts/test_propagation_offline_comparison_display.sh
- CP-0008Y finalization workflow run: {run_id}

### Evidence boundary

A historical selected-view change is not a provider deletion. Unkeyed SHA-256 is not authentication. Full reports are themselves unauthenticated. UI hosts must escape untrusted IDs and provenance text; the DTO layer does not render HTML, connect to real Android views, control radio, access live providers, authenticate sources or verify atomicity.

### Inherited verified ancestry

{parent} and older verified checkpoints remain ancestry.

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

1. Add a GitHub/CI-only host interaction and accessibility state contract for CP-0008Y historical comparison display views.
2. Preserve CP-0008P/Q/R/S/T/U/V/W/X/Y canonical receipts, historical selected-view warnings and bounded page controls.
3. Do not claim Android installation, real UI/device tests, live provider status, authenticated source attribution or live RF.
4. CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.

"""
readme = replace_one(readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next")
path.write_text(readme)

(root / "NEXT_ACTION.md").write_text(f"""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return until the owner explicitly says resume CP-0003C.

Skip checkpoints requiring phone, radio, real accounts, credentials/certificates, RF or manual hardware validation. Skipped checkpoints remain incomplete.

## Next software checkpoint

**{next_checkpoint}**

Define a deterministic, GitHub/CI-only host interaction/accessibility state contract for the historical CP-0008Y paged display model, retaining mandatory provenance warnings, independent page cursors and historical selected-view classifications. Preserve all CP-0008P/Q/R/S/T/U/V/W/X/Y interfaces; no phone/radio/live RF claims.
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

CP-0008Z — offline comparison accessibility and host interaction state contract

Required scope:

- deterministic GitHub/CI-only interaction/accessibility state over CP-0008Y historical paged display
- preserve CP-0008P/Q/R/S/T/U/V/W/X/Y artifact, receipts, warnings and selected-view contracts
- no real Android UI/device, authenticated origin, atomicity, live source/RF or account claim
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008Y — Offline comparison paged display integration" not in history:
    history += f"""

## CP-0008Y — Offline comparison paged display integration

Parent: {parent}.

Typed pure Kotlin host display contract wrapping validated CP-0008X canonical comparison pagination: two independent bounded sections with stable historical source/evidence before/after rows, selected-view-only change labels, typed previous/next requests preserving filters, original receipts/global tallies, and mandatory safety notices that never imply live RF, provider deletion, authenticated origin or atomicity. Optional original report consistency is explicit and remains unauthenticated. Focused and inherited host CI PASS; finalizer run: {run_id}.

No live Android UI, radio/phone/USB, RF, certificate or account testing. CP-0003C remains DEFERRED; CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0008Y_OFFLINE_COMPARISON_DISPLAY_CONTRACT.md and PropagationOfflineReportComparisonDisplayTests.kt.
"""
    history_path.write_text(history)
