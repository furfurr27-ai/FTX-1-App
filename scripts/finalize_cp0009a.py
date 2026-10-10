#!/usr/bin/env python3
"""CP-0009A promotion after full inherited host CI and guarded main checkpoint."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009A-PROPAGATION_WORKSPACE_HISTORY_INTEGRATION"
parent = "CP-0008Z-PROPAGATION_OFFLINE_COMPARISON_ACCESSIBILITY_STATE"
next_checkpoint = "CP-0009B — bounded offline propagation report history archive/store contract"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v52-propagation-workspace-history-integration\n")

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

CP-0009A is a GREEN host-only propagation workspace-to-historical-comparison integration bridge.

CP-0009A proves:

- One already-captured CP-0008M operating picture composes CP-0008N consistency diagnostics, CP-0008O report and strict CP-0008P/R canonical serialized report/receipt without a second store read.
- Two explicitly provided canonical offline captures produce a CP-0008T–V comparison and CP-0008Z accessible historical interaction screen, with both original report artifacts independently reconciled.
- Typed next/previous/filter/focus interactions revalidate the original canonical reports and derivative; stale, forged, swapped or tampered captures are rejected.
- Preserves UTC query ordering, source/evidence receipts, selected-view-only meaning, separate bounded cursors and four mandatory historical/provenance warnings.
- Full CP-0008P/Q/R/S/T/U/V/W/X/Y/Z contracts and inherited Kotlin/host GitHub CI regression suite remain GREEN.
- Offline capture is not persisted and does not assert authenticated origin, store atomicity, live RF truth or Android device behavior.

Host/CI gates:

- CP-0009A propagation workspace-to-offline-history integration: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0009A_WORKSPACE_HISTORY_INTEGRATION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationWorkspaceHistoryService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationWorkspaceHistoryTests.kt
- scripts/test_propagation_workspace_history.sh
- CP-0009A finalization workflow run: {run_id}

### Evidence boundary

Only historical/offline self-consistency is proven. Unkeyed SHA-256 is not authenticated provider provenance, comparison changes are not provider deletions, and no actual archive persistence, live RF, Android UI, USB, radio, certificate or account verification is included.

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

1. Create a GitHub/CI-only bounded offline propagation report archive/store contract above CP-0009A, preserving canonical imports and explicit history selection.
2. Preserve CP-0008P/Q/R/S/T/U/V/W/X/Y/Z original comparison/presentation/interaction contracts and the full inherited test matrix.
3. No actual Android DB/filesystem persistence, real phone/radio/RF, authenticated provider source or cross-store atomicity claim.
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

Implement a bounded, deterministic, GitHub/CI-only offline report history archive/store contract above CP-0009A. Do not claim real Android filesystem/DB persistence, provider authentication, live RF state or source-store atomicity. Preserve CP-0008P/Q/R/S/T/U/V/W/X/Y/Z and CP-0009A APIs and full inherited regression CI.
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

CP-0009B — bounded offline propagation report history archive/store contract

Required scope:

- deterministic software-only bounded archive selection, retention and validated canonical report receipts
- preserve CP-0008P/Q/R/S/T/U/V/W/X/Y/Z and CP-0009A workspace integration contracts
- no Android device/DB persistence, authenticated origin, cross-store atomicity, RF or real account claim
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009A — Propagation workspace to historical comparison bridge" not in history:
    history += f"""

## CP-0009A — Propagation workspace to historical comparison bridge

Parent: {parent}.

An already-captured operating picture is transformed into strict deterministic CP-0008P report and CP-0008R receipt without extra store reads. Two explicit canonical captures form a validated CP-0008T comparison plus CP-0008Z accessible historical screen with paired original report consistency. Host interactions revalidate canonical originals, derivative and retained provenance. Synthetic fixtures cover no-snapshot, populated, later/earlier/same UTC, independent filters, tampered artifacts/receipts and stale comparison state. Focused and complete inherited host CI PASS; finalizer run {run_id}.

No archive persistence, authenticated provider origin, atomic stores, live RF, radio/phone/Android UI or account/certificate proof. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0009A_WORKSPACE_HISTORY_INTEGRATION.md and PropagationWorkspaceHistoryTests.kt.
"""
    history_path.write_text(history)
