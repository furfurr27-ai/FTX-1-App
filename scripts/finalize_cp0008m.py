#!/usr/bin/env python3
"""Promote CP-0008M only after green focused and inherited main CI."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008M-PROPAGATION_OPERATING_PICTURE_READ_MODEL"
next_checkpoint = "CP-0008N — propagation read-model consistency diagnostics"

def replace_one(content, pattern, replacement, label):
    output, hits = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if hits != 1:
        raise SystemExit(f"Expected one {label}, got {hits}")
    return output

(root / "VERSION").write_text("v38-propagation-operating-picture-read-model\n")
path = root / "README.md"
text = path.read_text()
inherited = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", text, re.S)
if not inherited:
    raise SystemExit("Inherited host/CI inventory unavailable")
gates = inherited.group(1).replace(
    "- CP-0008L propagation source-status presentation: **PASS**.", ""
).strip()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: CP-0008L-PROPAGATION_SOURCE_STATUS_PRESENTATION.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008M is a GREEN platform-neutral, deterministic host/CI operating-picture read-model checkpoint.

CP-0008M proves:

- The operating-picture service captures one snapshot and one source-state list and reuses those exact inputs for both source-status and workspace projections.
- Separate snapshot latest() reads cannot diverge between the two rendered data models.
- Existing workspace filters, ordering, selected-path assessment, provenance, evidence freshness and offline-cache flags remain unchanged.
- Complete source errors, retry backoff, last attempt and last-good evidence remain visible even when the workspace is filtered.
- The composition validates identical explicit query UTC and snapshot ID, capture UTC and future-dated marker across views.
- No cached snapshot gives a null workspace and nonfabricated source statuses.
- Runtime operatingPicture overloads reuse existing injected stores without refreshing, scheduling, fetching or writing data.
- Offline tests prove exact equality with independent CP-0008L / CP-0008F projections and persisted runtime recreation with no network calls.
- Superseded CP-0008L finalizer changed to manual-only.

Host/CI gates:

- CP-0008M propagation operating-picture composition: **PASS**.
- CP-0008L propagation source-status presentation: **PASS**.
{gates}

Evidence:

- research/propagation/CP-0008M_OPERATING_PICTURE_READ_MODEL.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOperatingPictureService.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationSourceStatusService.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOperatingPictureTests.kt
- SOFTWARE_TRACK.md
- CP-0008M finalization workflow run: {run_id}

### Evidence boundary

Single-snapshot read composition is not an atomic transaction across independently changing stores. No actual Android UI, live provider, radio/phone/RF, accounts, credentials, QSO/LoTW or new propagation provider behavior has been verified.

### Inherited verified ancestry

CP-0008L-PROPAGATION_SOURCE_STATUS_PRESENTATION and all earlier verified checkpoints remain verified ancestry.

"""
text = replace_one(text,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
text = replace_one(text, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_checkpoint, "active track")
text = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, text, count=1)
next_section = f"""## Current exact next action

**{next_checkpoint}.**

1. Add read-only diagnostics describing captured-source and cached-snapshot timing and provenance; never assert cross-store transactionality or invent propagation evidence.
2. Preserve the CP-0008M operating-picture behavior and all provider refresh, source-state, assessment and cache semantics.
3. Prove deterministic host/CI only; do not add Android UI, lifecycle, WorkManager, real provider dependency, permissions, credentials, accounts or hardware testing.
4. CP-0003C remains DEFERRED until the owner explicitly says resume CP-0003C.

"""
text = replace_one(text, r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next action")
path.write_text(text)

(root / "NEXT_ACTION.md").write_text(f"""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Skipped checkpoints remain incomplete.

## Next software checkpoint

**{next_checkpoint}**

Build platform-neutral read-only diagnostics for the CP-0008M operating picture: state/cache timestamp skew, freshness provenance and explicit consistency limitations. No mutating stores, invented data or live provider dependency; checkpoint-first deterministic GitHub/CI.
""")
path = root / "SOFTWARE_TRACK.md"
track = path.read_text()
track = replace_one(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "software parent")
track = replace_one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008N-PROPAGATION_READ_MODEL_CONSISTENCY_DIAGNOSTICS

Required scope:

- explicit read-only source/snapshot timing and provenance consistency diagnostics
- never claim a cross-store atomic read or a live provider health guarantee
- preserve CP-0008M operating picture and existing projection/status/store contracts
- host/CI-only offline fixtures, immutable verified checkpoint and complete inherited regressions
- no Android UI, WorkManager, lifecycle, network permissions, real accounts/credentials, phone/radio/RF testing
- CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete

## Resume rule""", "active software track")
path.write_text(track)

path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = path.read_text()
if "## CP-0008M — Propagation operating-picture read model" not in history:
    history += f"""

## CP-0008M — Propagation operating-picture read model

Parent: CP-0008L-PROPAGATION_SOURCE_STATUS_PRESENTATION.

A platform-neutral read-only composite now exposes the existing propagation workspace projection and per-source status from one captured cached snapshot and source-state list at one explicit UTC. It retains existing evidence/filters/assessments, read-only persistence, status failure/backoff and provenance without introducing providers, scheduling, Android lifecycle or hardware interaction.

Focused read-model tests PASS; full inherited host/CI matrix PASS. Finalizer run: {run_id}.

A sequential single-snapshot read does not guarantee atomic cross-store state. CP-0003C and CP-0004A/B/C remain deferred/incomplete.

Evidence: research/propagation/CP-0008M_OPERATING_PICTURE_READ_MODEL.md and PropagationOperatingPictureTests.kt.
"""
    path.write_text(history)
