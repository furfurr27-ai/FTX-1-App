#!/usr/bin/env python3
"""CP-0008Z promotion after full host CI and guarded main snapshot checkpoint."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008Z-PROPAGATION_OFFLINE_COMPARISON_ACCESSIBILITY_STATE"
parent = "CP-0008Y-PROPAGATION_OFFLINE_COMPARISON_DISPLAY_CONTRACT"
next_checkpoint = "CP-0009A — next GitHub/CI-only propagation workspace integration checkpoint"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v51-propagation-offline-comparison-accessibility-state\n")

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

CP-0008Z is a GREEN host-only historical offline comparison accessibility and interaction-state contract.

CP-0008Z proves:

- Strict canonical CP-0008Y validated historical display is the only source for an immutable platform-neutral host interaction screen and state.
- Independent source/evidence cursor and filter actions, deterministic next/previous, safe disabled navigation and reset-to-first-page when filters change.
- Typed summary/source/evidence/provenance focus intents, bounded accessible row descriptors, first/last ordinal labels and empty-results descriptions.
- Every screen retains all four mandatory historical/provenance warnings; no live provider deletion, RF quality, unkeyed digest authentication or source atomicity claims.
- Stale comparison artifact interaction is rejected and optional paired original-report consistency preserves earlier validation.
- Full CP-0008P/Q/R/S/T/U/V/W/X/Y contracts and inherited Kotlin/host GitHub CI regression matrix remain GREEN.

Host/CI gates:

- CP-0008Z offline historical interaction/accessibility contract: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008Z_OFFLINE_COMPARISON_ACCESSIBILITY_STATE.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonInteractionService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonInteractionTests.kt
- scripts/test_propagation_offline_comparison_interaction.sh
- CP-0008Z finalization workflow run: {run_id}

### Evidence boundary

This host DTO layer does not actually move focus, render Android UI, verify TalkBack, authenticate origin or claim live propagation/RF. Hosts must escape untrusted source and evidence IDs when rendering. CP-0003C is deferred and CP-0004A/B/C incomplete.

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

1. Inspect the current live propagation workspace roadmap and select the next unblocked GitHub/CI-only integration task above CP-0008Z.
2. Preserve CP-0008P/Q/R/S/T/U/V/W/X/Y/Z canonical report, selected-view display and accessibility state contracts and full regression matrix.
3. Do not claim Android UI device installation, real TalkBack proof, authenticated source origin, cross-store atomicity or live RF.
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

Inspect the current propagation workspace roadmap and choose the next fully GitHub/CI-only integration task after CP-0008Z. Preserve CP-0008P/Q/R/S/T/U/V/W/X/Y/Z canonical report, selected historical views and accessibility state, and full inherited regression suite. No live/authenticated RF or real Android UI claims.
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

CP-0009A — next GitHub/CI-only propagation workspace integration checkpoint

Required scope:

- select next software-only propagation/workspace integration task from the live roadmap
- preserve CP-0008P/Q/R/S/T/U/V/W/X/Y/Z report, historical view and accessibility contracts
- no real Android UI/device, authenticated origin, atomicity, live source/RF or account claim
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008Z — Historical comparison accessibility and interaction state" not in history:
    history += f"""

## CP-0008Z — Historical comparison accessibility and interaction state

Parent: {parent}.

Strict host-only accessibility and independent cursor/filter/focus state machine over validated CP-0008Y historical display; controlled page-bound and no-match spoken descriptors, required provenance notice reading order, immutable outer collection read models, stale artifact rejection and optional paired original-report consistency preservation. Focused and inherited Kotlin host CI PASS; finalizer run: {run_id}.

No live Android/TalkBack, radio/phone/USB, RF, account/certificate, source authentication or cross-store atomicity proof. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0008Z_OFFLINE_COMPARISON_ACCESSIBILITY_STATE.md and PropagationOfflineReportComparisonInteractionTests.kt.
"""
    history_path.write_text(history)
