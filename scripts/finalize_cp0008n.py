#!/usr/bin/env python3
"""CP-0008N: promote source checkpoint only after green full main CI."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008N-PROPAGATION_READ_MODEL_CONSISTENCY_DIAGNOSTICS"
next_checkpoint = "CP-0008O — propagation offline diagnostic report payload"

def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label}: {count}")
    return output

(root / "VERSION").write_text("v39-propagation-read-model-consistency-diagnostics\n")
path = root / "README.md"
readme = path.read_text()
gates = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not gates:
    raise SystemExit("Inherited host/CI gate inventory missing")
inherited = gates.group(1).replace(
    "- CP-0008M propagation operating-picture composition: **PASS**.", ""
).strip()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: CP-0008M-PROPAGATION_OPERATING_PICTURE_READ_MODEL.

CP-0003C and CP-0004A/B/C remain deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008N is a GREEN platform-neutral read-only propagation source/snapshot consistency diagnostics checkpoint.

CP-0008N proves:

- PropagationReadModelConsistencyService.diagnose accepts only the already-captured CP-0008M operating picture, without additional store reads, network calls, refresh, writes or internal wall clock.
- Per-source last-success versus snapshot capture and newest cached retrieval versus last-success timestamps retain signed differences and BEFORE/EQUAL/AFTER/UNKNOWN relations.
- Explicit future-dated source attempts, retrieval times and cache captures, with snapshot age null for future-dated captures.
- Source-aware fresh/aging/stale/future-dated evidence counts remain separate; no provider availability, path quality or propagation forecast is invented.
- Diagnostics are invariant under workspace evidence filters; source status still carries unfiltered cached evidence and last-attempt errors.
- Aggregated source-success-after-snapshot, retrieval-after-success and future-timestamp source counts are derived from the same rows.
- The composed runtime `operatingPictureWithDiagnostics` retains the original operating picture, including after file-backed runtime recreation.
- The model explicitly sets crossStoreAtomicityVerified=false: sequential store reads cannot prove transactionally consistent generations.
- Deterministic offline synthetic tests prove missing metadata, boundary comparisons, invalid composites, no extra store reads and bit-identical persisted files.
- Superseded CP-0008M finalizer changed to manual-only.

Host/CI gates:

- CP-0008N propagation read-model consistency diagnostics: **PASS**.
- CP-0008M propagation operating-picture composition: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008N_READ_MODEL_CONSISTENCY_DIAGNOSTICS.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationReadModelConsistencyService.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationReadModelConsistencyTests.kt
- SOFTWARE_TRACK.md
- CP-0008N finalization workflow run: {run_id}

### Evidence boundary

Read-model comparisons are provenance diagnostics only. They do not establish atomic cross-store reads, provider errors from timing mismatches, radio conditions or a working Android screen. No device, credentials, LoTW, new provider or RF testing was performed.

### Inherited verified ancestry

CP-0008M-PROPAGATION_OPERATING_PICTURE_READ_MODEL and earlier verified checkpoints remain verified ancestry.

"""
readme = replace_one(readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_one(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_checkpoint, "active software track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = f"""## Current exact next action

**{next_checkpoint}.**

1. Define a platform-neutral deterministic offline diagnostic report payload from CP-0008N's composed operating picture and read-only provenance diagnostics.
2. Preserve unchanged refresh, projection, source-status and consistency semantics; carry source attribution and unknown/future timestamps without fabricating evidence.
3. Prove through host/CI-only fixtures and immutable checkpoint. Do not add Android UI, network permissions, new providers, real accounts/credentials or phone/radio/RF work.
4. CP-0003C remains DEFERRED until owner explicitly says resume CP-0003C.

"""
readme = replace_one(readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next action")
path.write_text(readme)

(root / "NEXT_ACTION.md").write_text(f"""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return until the owner explicitly says resume CP-0003C.

Skip any checkpoint requiring phone, radio, real credentials/certificates, real accounts, RF testing or manual hardware validation. Skipped checkpoints remain incomplete.

## Next software checkpoint

**{next_checkpoint}**

Build a platform-neutral deterministic offline report DTO from the existing CP-0008N operating picture and source provenance diagnostics, with explicit UTC and unknown fields. No network/provider dependency or new evidence; immutable GitHub/CI checkpoint first.
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

CP-0008O-PROPAGATION_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD

Required scope:

- read-only deterministic offline data-transfer object for combined operating picture/source timing diagnostics
- provenance preserved; no invented source, clock, RF measurement or cached evidence
- unchanged CP-0008M/N APIs and all source/cache/assessment policies
- focused + inherited deterministic CI, durable immutable checkpoint
- no Android UI, lifecycle, WorkManager, credentials, real accounts, new provider or phone/radio/RF tests
- CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete

## Resume rule""", "software active")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008N — Propagation read-model consistency diagnostics" not in history:
    history += f"""

## CP-0008N — Propagation read-model consistency diagnostics

Parent: CP-0008M-PROPAGATION_OPERATING_PICTURE_READ_MODEL.

A pure read-only diagnostic projection now compares cached snapshot capture, last source success and newest source evidence retrieval at one explicit UTC, with signed time deltas, before/equal/after/unknown ordering, source-specific observation freshness counts and future-date markers. It does not read stores or call providers separately. Runtime entrypoint composes without changing the existing operating picture.

Host CI focused suite PASS; inherited propagation, awards, logger/LoTW and core suites PASS. Finalizer run: {run_id}.

Cross-store atomicity remains explicitly unverified. CP-0003C and CP-0004A/B/C remain deferred/incomplete.

Evidence: research/propagation/CP-0008N_READ_MODEL_CONSISTENCY_DIAGNOSTICS.md and PropagationReadModelConsistencyTests.kt.
"""
    history_path.write_text(history)
