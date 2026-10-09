#!/usr/bin/env python3
"""CP-0008U promotion on main only, after full decoder and inherited test matrix."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008U-PROPAGATION_OFFLINE_COMPARISON_EXPORT_DECODE_VALIDATION"
parent = "CP-0008T-PROPAGATION_OFFLINE_COMPARISON_EXPORT_CONTRACT"
next_checkpoint = "CP-0008V — offline comparison import and inspection boundary"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v46-propagation-offline-comparison-export-decode-validation\n")

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

CP-0008U is a GREEN host-only strict, typed CP-0008T canonical offline comparison export V1 decoder and validator.

CP-0008U proves:

- Full export metadata enforcement: separate media type, version 1, bounded canonical UTF-8, exact length and unkeyed SHA-256 integrity.
- Strict bounded JSON scanner and typed DTO reconstruction shared with the existing CP-0008Q report decoder without altering original CP-0008P report V1 bytes.
- Original before/after import receipts, query times, source/evidence identity and nested status/timing, selected-view classifications and distinct metadata/full-projection flags survive byte-for-byte round trips.
- Rejection of malformed, truncated, incompatible, noncanonical, misclassified, receipt-inconsistent or tampered artifact metadata, including recomputed-but-inconsistent SHA-256.
- Original CP-0008P/Q/R/S/T contracts and the inherited host-only GitHub Actions matrix remain GREEN.
- Integrity is not a signature: original full V1 reports are required for independent projection-content verification, no provider authentication or live RF state is asserted.

Host/CI gates:

- CP-0008U offline comparison export decode validation: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008U_OFFLINE_COMPARISON_EXPORT_DECODE_VALIDATION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonDecoder.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonDecoderTests.kt
- scripts/test_propagation_offline_comparison_decoder.sh
- CP-0008U finalization workflow run: {run_id}

### Evidence boundary

A removed selected-view item is not provider deletion. SHA-256 cannot authenticate provenance. This derivative does not include both original projection payloads. Cross-store atomicity, phone/radio/RF, live provider status and real accounts/certificates remain unverified.

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

1. Add a bounded GitHub/CI-only offline comparison import and read-only inspection boundary above CP-0008U.
2. Preserve CP-0008P/Q/R/S/T/U canonical receipts, report V1 bytes, selected-view semantics and all inherited CI.
3. Never treat SHA-256 as authenticated provenance, claims of cross-store atomicity or evidence of live propagation.
4. CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.
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

Build a strict GitHub/CI-only import and inspection boundary around validated CP-0008U comparison exports, preserving original receipts and historical selected-view semantics, without claiming authenticated provenance, cross-store atomicity, live RF or hardware validation.
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

CP-0008V — offline comparison import and inspection boundary

Required scope:

- GitHub/CI-only bounded import of validated CP-0008U canonical comparison exports
- Preserve full original CP-0008P/Q/R/S/T/U contracts and inherited regression gates
- No authenticated origin, cross-store atomicity, live provider/RF, real-device or account claim
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008U — Offline comparison export strict decode validation" not in history:
    history += f"""

## CP-0008U — Offline comparison export strict decode validation

Parent: {parent}.

Versioned and bounded strict canonical comparison JSON V1 decoder with exact SHA-256/UTF-8 metadata validation, typed DTO reconstruction via shared CP-0008Q parser, nested receipts and selected evidence/source changes. Noncanonical and inconsistent artifacts rejected, and byte-for-byte round trips proved. Original report CP-0008P bytes are unchanged. Focused tests plus full inherited host CI matrix PASS. Finalizer run: {run_id}.

Integrity is not authenticated origin. No cross-store atomicity, live provider/RF, phone/radio/accounts/certificates or hardware assertions. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Evidence: research/propagation/CP-0008U_OFFLINE_COMPARISON_EXPORT_DECODE_VALIDATION.md and PropagationOfflineReportComparisonDecoderTests.kt.
"""
    history_path.write_text(history)
