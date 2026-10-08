#!/usr/bin/env python3
"""Promote CP-0008L after its main-branch focused and inherited CI gates."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008L-PROPAGATION_SOURCE_STATUS_PRESENTATION"
next_checkpoint = "CP-0008M — propagation operating-picture read-model composition"

def replace_one(value, pattern, replacement, label):
    updated, count = re.subn(pattern, lambda _: replacement, value, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} section, found {count}")
    return updated

(root / "VERSION").write_text("v37-propagation-source-status-presentation\n")

readme_path = root / "README.md"
readme = readme_path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Missing inherited host/CI gate inventory")
inherited = match.group(1).replace(
    "- CP-0008K refresh-state persistence: **PASS**.", ""
).strip()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: CP-0008K-PROPAGATION_REFRESH_STATE_PERSISTENCE.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008L is a GREEN host/CI checkpoint. It provides a read-only, platform-neutral source-status presentation projection of persisted refresh state and cached propagation evidence.

CP-0008L proves:

- PropagationSourceStatusService.project requires explicit UTC and never reads the system clock or fetches providers.
- PropagationRuntime.sourceStatus reuses the existing injected refresh-state and snapshot stores; existing refreshAndProject semantics are unchanged.
- Stable-source-key ordering, no duplicate status records and no invented records from a missing snapshot.
- READY, CADENCE_WAIT, RETRY_BACKOFF and FAILURE_COOLDOWN distinguish eligibility from source success.
- Last attempt outcome remains FAILED even when an earlier failed source becomes eligible; retryability, failure details and consecutive failure count remain inspectable.
- Exact remaining wait and inclusive eligibility boundary; no negative countdown when overdue.
- Last-success UTC/age/future-date status is separate from last evidence observed/retrieved UTC and from provider availability.
- Source-key/role matching isolates observed planetary Kp, forecast variants, F10.7, GloTEC and PSK Reporter.
- Mixed stale/fresh/future-dated evidence is counted by the existing source-specific freshness classifier without collapsing to an invented single condition label.
- Runtime/store recreation with CP-0008K persisted refresh state and file-backed snapshots yields identical projections.
- Repeated projections leave persisted state/snapshot files bit-identical; synthetic offline tests never contact providers.
- The superseded CP-0008K main finalizer was made manual-only.

Host/CI gates:

- CP-0008L propagation source-status presentation: **PASS**.
- CP-0008K refresh-state persistence: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008L_SOURCE_STATUS_PRESENTATION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationSourceStatusService.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationSourceStatusTests.kt
- SOFTWARE_TRACK.md
- CP-0008L finalization workflow run: {run_id}

### Evidence boundary

CP-0008L is a deterministic host-only source status read model, not a rendered Android screen, background refresh scheduler, live provider health monitor, or prediction of HF propagation. No credentials, radio hardware, RF, QSO or LoTW behavior was tested or added.

### Inherited verified ancestry

CP-0008K-PROPAGATION_REFRESH_STATE_PERSISTENCE, CP-0008J-PROPAGATION_RUNTIME_COMPOSITION and all earlier verified checkpoints remain verified ancestry.

"""
readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)
readme = replace_one(
    readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_checkpoint,
    "active software track",
)
readme = re.sub(
    r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint,
    readme, count=1,
)
next_text = f"""## Current exact next action

**{next_checkpoint}.**

1. Compose an explicit-UTC, read-only operating-picture result containing the existing propagation workspace projection plus the CP-0008L source-status projection.
2. Keep these representations separate and lossless; no provider, parser, cadence, caching, projection, evidence, TX, logbook or LoTW semantic changes.
3. Prove consistency across one immutable source-state/snapshot read cycle with deterministic host/CI fixtures.
4. Do not add Android UI/lifecycle/WorkManager/network-permission behavior, credentials, accounts, new propagation providers or hardware tests.
5. CP-0003C remains DEFERRED until the owner explicitly says resume CP-0003C.

"""
readme = replace_one(
    readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_text + "## README maintenance contract",
    "current exact next action",
)
readme_path.write_text(readme)

(root / "NEXT_ACTION.md").write_text(f"""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing or other manual hardware validation. Skipped checkpoints remain incomplete.

## Next software checkpoint

**{next_checkpoint}**

Create a platform-neutral read-only operating-picture composition that exposes the existing propagation workspace projection alongside CP-0008L per-source readiness/freshness/status for one explicit UTC. Preserve evidence and scheduler semantics. Prove host/CI only, checkpoint-first.
""")
track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(
    track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work",
    "software track parent",
)
track = replace_one(
    track,
    r"## Active software checkpoint\n\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008M-PROPAGATION_OPERATING_PICTURE_READ_MODEL

Required scope:

- platform-neutral read-only combined operating-picture model
- explicit UTC, no provider fetch or internal clock
- preserve original propagation workspace projection and CP-0008L source-status fidelity
- prevent conflicting snapshot/state read consistency where practicable without mutating underlying stores
- no changes to provider cadence/parsers, cache writes or propagation evidence semantics
- deterministic offline host/CI gates and immutable checkpoint
- no Android/WorkManager/network permission, credentials/accounts, new providers, phone/radio/RF validation
- CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete

## Resume rule""",
    "software track active checkpoint",
)
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008L — Propagation source-status presentation" not in history:
    history += f"""

## CP-0008L — Propagation source-status presentation

Parent: CP-0008K-PROPAGATION_REFRESH_STATE_PERSISTENCE.

A read-only, explicit-UTC service now projects per-source scheduler eligibility, time until next refresh, last attempt and retry/backoff details separately from last-good cached evidence ages and per-observation freshness counts. Role matching keeps NOAA Kp observed/forecast, F10.7, GloTEC and PSK Reporter separate. PropagationRuntime.sourceStatus uses the existing injected stores. No network, hardware or Android background behavior was introduced.

Focused status suite PASS; inherited CP-0008K persistence/runtime/propagation/awards/logger/LoTW/core regression jobs PASS. Finalization run: {run_id}.

Deferred hardware/account checkpoints CP-0003C and CP-0004A/B/C remain incomplete.

Evidence: research/propagation/CP-0008L_SOURCE_STATUS_PRESENTATION.md, PropagationSourceStatusService.kt, PropagationRuntime.kt and PropagationSourceStatusTests.kt.
"""
    history_path.write_text(history)
