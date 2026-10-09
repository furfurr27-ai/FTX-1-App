#!/usr/bin/env python3
"""CP-0008Q finalizer: durable state only after complete main-branch CI."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008Q-PROPAGATION_OFFLINE_REPORT_DECODE_VALIDATION"
parent = "CP-0008P-PROPAGATION_OFFLINE_REPORT_SERIALIZATION_CONTRACT"
next_checkpoint = "CP-0008R — propagation offline report import boundary"

def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output

(root / "VERSION").write_text("v42-propagation-offline-report-decode-validation\n")
path = root / "README.md"
readme = path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Inherited host gate list unavailable")
inherited = match.group(1).strip()
baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** {checkpoint}

Parent durable checkpoint: {parent}.

CP-0003C and CP-0004A/B/C remain deferred/incomplete hardware/account checkpoints, not part of this CI-only verification.

CP-0008Q is a GREEN platform-neutral offline report V1 strict decoder/validator.

CP-0008Q proves:

- Strict bounded V1 JSON parsing, exact format/media-type/wire/schema checks, complete typed DTO reconstruction and byte-for-byte canonical re-encoding.
- CP-0008P canonical JSON layout, nulls, source attribution, nested projections, assessments, freshness metadata, timestamp provenance, ordered lists, maps and sets survive round trips.
- Corrupt, oversized, truncated, malformed and incompatible reports fail closed, including altered evidence references, source-count mismatches, inconsistent timestamps, invalid Unicode and numeric overflow.
- Coverage of geographic projections and global coverage singleton that were valid nested DTOs but previously rejected by the canonical serializer.
- Embedded SHA-256 and UTF-8 length detect accidental corruption. They are not an authenticity proof when the digest can be recomputed.
- No source store, provider calls, Android lifecycle, hardware access or new application dependencies.

Host/CI gates:

- CP-0008Q propagation offline report decode and validation: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008Q_OFFLINE_REPORT_DECODE_VALIDATION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportDecoder.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportSerialization.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportDecoderTests.kt
- scripts/test_propagation_offline_report_decoder.sh
- CP-0008Q finalization workflow run: {run_id}

### Evidence boundary

The decoder validates structural and internally consistent provenance assertions; untrusted reports can be forged with a newly computed checksum. This is not a digital signature, authenticated transport, proof of original provider observation or transactional cache consistency. No real phone/radio/RF validation.

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

1. Identify a GitHub/CI-only offline report import boundary using the canonical decoder, without new filesystem, Android UI, network or credential dependencies.
2. Preserve the byte-identical CP-0008P serializer and CP-0008Q decoder gates, and all existing provider/cache/assessment behavior.
3. Do not claim authenticity for bare SHA-256 or cross-store atomicity.
4. CP-0003C remains DEFERRED until the owner explicitly says resume CP-0003C. CP-0004A/B/C remain incomplete.

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

Build a GitHub/CI-only report import boundary using validated decoded V1 DTOs. Preserve all prior tests, canonical format, source attribution and offline-only contracts; avoid inventing authenticated provenance.
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

CP-0008R — propagation offline report import boundary

Required scope:

- validated import boundary consuming CP-0008Q decoded canonical report DTO
- no credential/account/Android UI/radio/real RF tests
- maintain all inherited CP-0008P/Q host and CI regression gates
- cross-store atomicity and authenticity remain unverified
- CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete

## Resume rule""", "software next track")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008Q — Propagation offline report decode and validation" not in history:
    history += f"""

## CP-0008Q — Propagation offline report decode and validation

Parent: {parent}.

A strict standalone bounded JSON V1 decoder reconstructs the full original report's typed model, rejects incompatible envelope/unknown fields/invalid strings or numbers and validates source-status, diagnostic, snapshot and visible-evidence provenance. Canonical V1 round trips remain byte-identical. Deterministic synthetic focused plus inherited host/CI tests PASS. Finalizer run: {run_id}.

A recomputed SHA-256 cannot establish origin/authenticity; hardware and independently stored views are not proven atomic.

CP-0003C remains DEFERRED until explicitly resumed. CP-0004A/B/C remain incomplete.

Evidence: research/propagation/CP-0008Q_OFFLINE_REPORT_DECODE_VALIDATION.md and PropagationOfflineReportDecoderTests.kt.
"""
    history_path.write_text(history)
