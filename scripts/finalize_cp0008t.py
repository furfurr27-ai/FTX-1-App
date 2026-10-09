#!/usr/bin/env python3
"""CP-0008T finalizer: promote only after full main-branch host/CI suite."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0008T-PROPAGATION_OFFLINE_COMPARISON_EXPORT_CONTRACT"
parent = "CP-0008S-PROPAGATION_OFFLINE_REPORT_INSPECTION_COMPARISON"
next_checkpoint = "CP-0008U — offline comparison export decode validation"


def replace_one(content, pattern, replacement, label):
    output, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return output


(root / "VERSION").write_text("v45-propagation-offline-comparison-export-contract\n")

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

CP-0008T is a GREEN, platform-neutral canonical V1 serialization of CP-0008S report comparisons.

CP-0008T proves:

- Validated before/after CP-0008P V1 reports can produce a deterministic, separately versioned canonical offline comparison export.
- The report diagnostic V1 byte contract and golden fixtures remain unchanged; the existing canonical value writer is shared without new third-party dependencies.
- Both original integrity receipts, source/evidence selected-view change rows, UTC query order, and metadata versus full-projection change flags are retained.
- Exports have an independent media type, explicit wire version, exact UTF-8 length, SHA-256 and a 16 MiB upper bound.
- Structural checks reject unsupported trust/atomicity assertions, mismatched original receipt counts, unsorted or inconsistent source/evidence changes, and invalid UTF-8.
- The comparison export is derivative; full projection content and independently checkable provenance require the two original V1 reports. No signature, authenticated provider origin, or live RF status is implied.

Host/CI gates:

- CP-0008T offline comparison canonical export: **PASS**.
{inherited}

Evidence:

- research/propagation/CP-0008T_OFFLINE_COMPARISON_EXPORT_CONTRACT.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationOfflineReportComparisonSerialization.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationOfflineReportComparisonSerializationTests.kt
- scripts/test_propagation_offline_comparison_serialization.sh
- CP-0008T finalization workflow run: {run_id}

### Evidence boundary

A removed item is absent from a selected report view, not proven deleted at the provider. Original report receipts have unkeyed SHA-256, not cryptographic signatures. This compact export does not embed the full original projection values; independently rechecking a reported projection change requires both full original artifacts. No cross-store atomicity, provider authentication, Android device, RF or account validation.

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

1. Implement a GitHub/CI-only strict bounded decoder/validator for canonical CP-0008T comparison export V1.
2. Preserve canonical export bytes, original report receipts, selected-view caveats, and all source/evidence change metadata.
3. Preserve CP-0008P/Q/R/S/T and inherited host/CI gates, without inventing authenticated origin or live RF confidence.
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

Implement a strict platform-neutral decoder for versioned canonical CP-0008T comparison export artifacts, preserving original receipts and source/evidence change semantics; require full inherited tests and no authenticated provenance claims.
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

CP-0008U — offline comparison export decode validation

Required scope:

- strict bounded versioned decode/validate of canonical CP-0008T comparison export
- no credential/account/Android UI/radio/real RF tests
- preserve CP-0008P/Q/R/S/T and all inherited regression gates
- imported views are not live state; authenticity and atomicity unverified
- CP-0003C DEFERRED; CP-0004A/B/C incomplete

## Resume rule""", "software next")
track_path.write_text(track)

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008T — Offline comparison canonical export contract" not in history:
    history += f"""

## CP-0008T — Offline comparison canonical export contract

Parent: {parent}.

Canonical, separately versioned and bounded V1 JSON export of deterministic CP-0008S offline comparisons, preserving selected source/evidence changes, both import receipts and original UTC order. Strict structural validation and exact UTF-8/SHA-256 integrity metadata; original report serialization remains unchanged. Full projection provenance requires original reports. Host-focused and inherited CI regressions PASS. Finalizer run: {run_id}.

No signature/authenticated origin, cross-store atomicity, live state, device/radio/credentials, or RF test. CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.

Evidence: research/propagation/CP-0008T_OFFLINE_COMPARISON_EXPORT_CONTRACT.md and PropagationOfflineReportComparisonSerializationTests.kt.
"""
    history_path.write_text(history)
