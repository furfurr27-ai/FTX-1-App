#!/usr/bin/env python3
"""CP-0008P finalizer: durable checkpoint only after green full main CI."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008P-PROPAGATION_OFFLINE_REPORT_SERIALIZATION_CONTRACT"
next_checkpoint = "CP-0008Q — propagation offline report decode and validation"

def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}, got {count}")
    return output

(root / "VERSION").write_text("v41-propagation-offline-report-serialization\n")
path = root / "README.md"
readme = path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Inherited CI gate list unavailable")
inherited = match.group(1).replace(
    "- CP-0008O propagation offline report payload: **PASS**.", ""
).strip()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: CP-0008O-PROPAGATION_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD.

CP-0003C and CP-0004A/B/C remain deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008P is a GREEN platform-neutral, deterministic canonical JSON offline serialization checkpoint.

CP-0008P proves:

- The entire CP-0008O report including original nested selected workspace projections, source status, timing diagnostics and evidence index can be serialized in-memory to a versioned canonical UTF-8 JSON format without another store read or provider request.
- Root format and wire version are explicitly pinned to schema V1 and an application-specific JSON media type; nullable timestamps stay null and signed integers/millisecond UTCs stay exact.
- Domain fields and map keys order lexically, unordered sets canonicalize and ordered lists remain ordered. Enum names, full nested metrics and metadata are preserved.
- JSON Unicode and metacharacter escaping, finite-number validation and size/depth/collection bounds prevent malformed or unexpectedly large output.
- Canonical serialized output includes exact UTF-8 byte count and SHA-256 integrity checksum, and re-serialization verifies content/metadata equality without claiming authenticity.
- Source summary counts, indexed evidence provenance and cache-future timestamp markers are validated against the original DTO; cross-store atomicity remains explicitly unverified.
- Runtime serializedOfflineDiagnosticReport overloads compose CP-0008O first, with no new lifecycle, fetch, background scheduler or source/cache writes.
- Synthetic tests demonstrate missing cache, future/unknown dates, filters, stale and full rich source/evidence projections, tamper/Unicode/numeric rejection and identical file-backed runtime restoration.
- This checkpoint does not provide arbitrary JSON parsing or report reconstruction; decode/validation is the next planned software checkpoint.
- Superseded CP-0008O automatic finalizer changed to manual-only.

Host/CI gates:

- CP-0008P propagation offline report serialization: **PASS**.
- CP-0008O propagation offline report payload: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008P_OFFLINE_REPORT_SERIALIZATION_CONTRACT.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportSerialization.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportSerializationTests.kt
- SOFTWARE_TRACK.md
- CP-0008P finalization workflow run: {run_id}

### Evidence boundary

This is one-way canonical JSON serialization and deterministic verification against an original CP-0008O report, not JSON decoding, a digital signature, completed UI, filesystem exporter, live provider monitor or radio conditions prediction. Different source/state stores are not an atomic transaction. No real credentials, accounts, Android device or RF proof.

### Inherited verified ancestry

CP-0008O-PROPAGATION_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD and earlier verified checkpoints remain verified ancestry.

"""
readme = replace_one(readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_one(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_checkpoint, "README active track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = f"""## Current exact next action

**{next_checkpoint}.**

1. Add a bounded, deterministic, strict JSON decode/validation path for the canonical CP-0008P offline report wire format. Reject unknown incompatible wire versions, unsafe field types and corrupted source/evidence provenance.
2. Preserve the original source/cache/status/assessment semantics and every nullable/future timestamp; prove encoding/decoding consistency using offline host fixtures.
3. Keep provider/cadence/refresh and Android lifecycle/UI/WorkManager unchanged, with no new accounts, credentials, provider calls or phone/radio/RF work.
4. CP-0003C remains DEFERRED until owner explicitly says resume CP-0003C.

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

Implement a strict, bounded offline decode and validation path for CP-0008P's versioned canonical JSON report contract with deterministic round-trip and corruption tests, without network, provider, credential or hardware dependencies.
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

CP-0008Q-PROPAGATION_OFFLINE_REPORT_DECODE_VALIDATION

Required scope:

- strict offline deterministic JSON decoding and validation of CP-0008P canonical wire version 1
- bounded parser and strong source/evidence provenance and version validation
- round-trip equality and tampering/corruption rejection using deterministic CI-only fixtures
- preserve CP-0008M/N/O/P semantics and existing source/cache/assessment/refresh contracts
- immutable main checkpoint after full inherited CI regression
- no Android UI, lifecycle, WorkManager, credentials, real accounts, new providers or phone/radio/RF tests
- CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete

## Resume rule""", "active software checkpoint")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008P — Propagation offline report serialization contract" not in history:
    history += f"""

## CP-0008P — Propagation offline report serialization contract

Parent: CP-0008O-PROPAGATION_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD.

A pure offline canonical JSON UTF-8 serializer now exports the complete platform-neutral CP-0008O report DTO, including all nested source status and evidence projection details. Stable field/map/set ordering, exact nullable UTC integer timestamps, finite number checks, Unicode escaping and size bounds support deterministic transfer. Explicit wire version, media type, SHA-256 and byte count provide integrity validation without claiming provenance authenticity. No source or cache stores are accessed by the serializer, and no cross-store atomicity is claimed.

Focused host CI, checkpoint dry-run and all inherited host tests PASS. Finalizer run: {run_id}. Full decode/deserialization, UI and device integration remain future work.

CP-0003C DEFERRED until explicitly resumed; CP-0004A/B/C remain incomplete.

Evidence: research/propagation/CP-0008P_OFFLINE_REPORT_SERIALIZATION_CONTRACT.md and PropagationOfflineReportSerializationTests.kt.
"""
    history_path.write_text(history)
