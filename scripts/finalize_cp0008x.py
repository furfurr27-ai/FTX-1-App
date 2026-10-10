#!/usr/bin/env python3
"""CP-0008X promotion after main-branch full CI and snapshot verification."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008X-PROPAGATION_OFFLINE_COMPARISON_PAGINATION"
parent = "CP-0008W-PROPAGATION_OFFLINE_COMPARISON_PRESENTATION"
next_checkpoint = "CP-0008Y — offline comparison paged display integration contract"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v49-propagation-offline-comparison-pagination\n")

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

CP-0008X is a GREEN host-only historical offline comparison filtering and pagination read model.

CP-0008X proves:

- Canonical CP-0008W presentation import/validation precedes filtering and pagination, preserving original receipts and UTC query order.
- Independent source and selected-evidence pages with limits 1..100, zero-based Long offsets, stable deterministic order and nullable continuation.
- Exact case-sensitive ID prefixes, selected change classifications and before/after evidence-kind filters, no locale or live source dependencies.
- Global unfiltered classification tallies stay separate from filtered matched totals; no unbounded original row arrays are exposed from paged views.
- Invalid bounds, corrupt artifacts and incomplete optional original-report pairs are rejected; a standalone derivative remains unauthenticated.
- CP-0008P/Q/R/S/T/U/V/W contracts and inherited host-only regression suites remain GREEN.

Host/CI gates:

- CP-0008X bounded offline comparison presentation paging: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008X_OFFLINE_COMPARISON_PAGINATION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonPaginationService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonPaginationTests.kt
- scripts/test_propagation_offline_comparison_pagination.sh
- CP-0008X finalization workflow run: {run_id}

### Evidence boundary

An unkeyed SHA-256 cannot authenticate origin. ADDED_TO_VIEW or REMOVED_FROM_VIEW never proves provider deletion or live RF propagation changes. No cross-store atomicity, real Android device/FTX-1 radio, live provider, account or RF proof.

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

1. Define a GitHub/CI-only offline comparison paged display adapter contract above CP-0008X, preserving the historical view's provenance warnings.
2. Preserve CP-0008P/Q/R/S/T/U/V/W/X canonical artifact receipts, selected-view semantics and full regression suite.
3. Do not claim live propagation, authenticated provider origin, cross-store atomicity or real Android radio/device proof.
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

Define and implement a GitHub/CI-only offline comparison paged display adapter contract above CP-0008X, retaining canonical provenance and selected-view semantics. Preserve CP-0008P/Q/R/S/T/U/V/W/X interfaces and inherited CI; no live/authenticated RF claims.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "software parent")
track = replace_one(track,
    r"## Active software checkpoint\n\\n.*?\\n## Resume rule",
    """## Active software checkpoint

CP-0008Y — offline comparison paged display integration contract

Required scope:

- deterministic GitHub/CI-only paged historical display adapter over CP-0008X
- preserve CP-0008P/Q/R/S/T/U/V/W/X artifact and selected-view contracts
- no authenticated origin, cross-store atomicity, live provider/RF, phone or account claim
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008X — Offline comparison pagination and filtering" not in history:
    history += f"""

## CP-0008X — Offline comparison pagination and filtering

Parent: {parent}.

Strict independent bounded 1..100-row historical source/evidence pagination with nonnegative Long offsets and overflow-safe end behavior. Exact-case prefix, change and evidence-kind filters preserve canonical stable source/evidence selection ordering, all receipts, original UTC query order, unfiltered global classification tallies and optional complete original report consistency. Focused and complete inherited GitHub/CI regressions passed; finalizer run: {run_id}.

Unkeyed integrity only; no authenticated provider origin, cross-store atomicity, live source/RF, Android phone/radio/accounts/certificate/hardware evidence. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0008X_OFFLINE_COMPARISON_PAGINATION.md and PropagationOfflineReportComparisonPaginationTests.kt.
"""
    history_path.write_text(history)
