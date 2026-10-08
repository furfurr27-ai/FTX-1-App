#!/usr/bin/env python3
"""Promote CP-0008O only after green main-branch focused and inherited CI."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008O-PROPAGATION_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD"
next_checkpoint = "CP-0008P — propagation offline report serialization contract"

def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label}, got {count}")
    return output

(root / "VERSION").write_text("v40-propagation-offline-diagnostic-report\n")
path = root / "README.md"
readme = path.read_text()
found = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not found:
    raise SystemExit("Inherited host/CI gates not found")
inherited = found.group(1).replace(
    "- CP-0008N propagation read-model consistency diagnostics: **PASS**.", ""
).strip()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: CP-0008N-PROPAGATION_READ_MODEL_CONSISTENCY_DIAGNOSTICS.

CP-0003C and CP-0004A/B/C remain deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008O is a GREEN deterministic host/CI-only typed in-memory propagation offline diagnostic report checkpoint.

CP-0008O proves:

- PropagationOfflineDiagnosticReportService.build accepts the already captured CP-0008N operating picture and diagnostics, with no extra refresh-state or snapshot read, provider request, clock or write.
- A versioned platform-neutral report DTO includes explicit query UTC, nullable snapshot metadata/age and future/unknown statuses rather than invented values.
- Stable source-key rows preserve complete original per-source status (failed attempt, cooldown, readiness and last-good evidence) and full original timeline diagnostics without collapsing them to a provider-health score.
- Source totals distinguish cached attributed evidence from evidence visible after workspace filters, retaining provenance for unseen cache records.
- Original filtered heard, ionospheric, solar/geomagnetic and modeled projection sections and selected-path assessment remain lossless and unmodified.
- A deterministic evidence index derives exclusively from actually visible existing projection metadata, including kind, source ID, observed/retrieved UTC and original freshness classification.
- The payload explicitly prohibits cross-store atomicity claims and rejects mismatched source identities/roles, totals, invalid schema versions and corrupt report invariants.
- Runtime offlineDiagnosticReport overloads reuse existing injected stores, and file-backed recreation yields identical read-only results.
- No Android UI, JSON/CSV/PDF exporter, network/scheduler/provider, credentials, phone/radio/RF or QSO/LoTW behavior was added.
- Superseded CP-0008N automatic main finalizer changed to manual-only.

Host/CI gates:

- CP-0008O propagation offline report payload: **PASS**.
- CP-0008N propagation read-model consistency diagnostics: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008O_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineDiagnosticReportService.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineDiagnosticReportTests.kt
- SOFTWARE_TRACK.md
- CP-0008O finalization workflow run: {run_id}

### Evidence boundary

The report is a deterministic in-memory DTO, not a completed UI, actual serialization contract, live provider monitor or RF prediction. Separate snapshot/state store reads do not prove atomicity. No hardware/account/manual Android validation was performed.

### Inherited verified ancestry

CP-0008N-PROPAGATION_READ_MODEL_CONSISTENCY_DIAGNOSTICS and earlier verified checkpoints remain verified ancestry.

"""
readme = replace_one(readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_one(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_checkpoint, "active track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = f"""## Current exact next action

**{next_checkpoint}.**

1. Design a deterministic offline report serialization contract from the versioned CP-0008O DTO with explicit schema semantics and safe output validation.
2. Preserve original source/evidence provenance and unknown/future timestamps; prove stable ordering and no fabricated values with synthetic host/CI fixtures.
3. Keep provider/cadence/cache/assessment and refresh logic unchanged; no Android lifecycle/UI/WorkManager, credentials, real accounts, new provider or phone/radio/RF proof.
4. CP-0003C remains DEFERRED until owner explicitly says resume CP-0003C.

"""
readme = replace_one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next action")
path.write_text(readme)

(root / "NEXT_ACTION.md").write_text(f"""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return until the owner explicitly says resume CP-0003C.

Skip any checkpoint requiring phone, radio, real credentials/certificates, real accounts, RF testing, or manual hardware validation. Skipped checkpoints remain incomplete.

## Next software checkpoint

**{next_checkpoint}**

Design and host-test an explicit versioned deterministic offline serialization contract for the CP-0008O diagnostic report. Preserve unknown/future timestamps, source attribution and stable evidence ordering without generating new evidence or fetching providers. Checkpoint-first GitHub/CI only.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "software parent")
track = replace_one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008P-PROPAGATION_OFFLINE_REPORT_SERIALIZATION_CONTRACT

Required scope:

- offline deterministic versioned serialization contract for CP-0008O typed report
- preserve source/evidence provenance and unknown/future timestamps without invented values
- validation of output consistency and stable canonical ordering; deterministic synthetic host CI
- preserve CP-0008O/N/M interfaces, cache, refresh, source provider/cadence and assessment behavior
- immutable main checkpoint after full inherited CI
- no phone, radio, RF testing, real accounts/credentials, Android lifecycle/UI/WorkManager or new provider
- CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete

## Resume rule""", "software active checkpoint")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008O — Propagation offline diagnostic report payload" not in history:
    history += f"""

## CP-0008O — Propagation offline diagnostic report payload

Parent: CP-0008N-PROPAGATION_READ_MODEL_CONSISTENCY_DIAGNOSTICS.

A versioned platform-neutral in-memory DTO now preserves the existing operating-picture workspace sections, source statuses and CP-0008N timestamp diagnostics with one explicit UTC. Separate source-attributed cached and visible filtered evidence counts prevent misinterpreting workspace filters as missing cache evidence. The visible evidence index reports original source and timing metadata only. Snapshot absence and future/unknown timestamps remain explicit, and cross-store atomicity is never claimed.

Focused offline report CI PASS, full inherited host regressions PASS. Immutable checkpoint finalizer run: {run_id}. No Android UI, serialization, provider fetch, radio hardware or credentials.

CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.

Evidence: research/propagation/CP-0008O_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD.md and PropagationOfflineDiagnosticReportTests.kt.
"""
    history_path.write_text(history)
