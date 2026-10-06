#!/usr/bin/env python3
import os
import pathlib
import re


def replace_one(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} section, found {count}")
    return updated


root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
(root / "VERSION").write_text("v23-award-map-projection\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0007A-AWARD_MAP_PROJECTION

Parent durable checkpoint: CP-0006G-EXTENDED_AWARD_CATALOG.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0007A is a GREEN host/CI map-domain checkpoint. It adds a platform-independent award-area projection for verified U.S. state and Maidenhead-grid award targets without embedding map SDK objects, coordinates, or fabricated geography.

CP-0007A proves:

- Map targets have exactly one primary state: NEEDED, WORKED_UNCONFIRMED, or CONFIRMED.
- LOCAL_THRESHOLD_MET is a separate overlay state and is explicitly not sponsor claimability or award credit.
- ARRL WAS projects all 50 finite state identities, including explicit needed states.
- ARRL FFMA projects the exact finite 488-grid target universe, including explicit needed grids.
- VUCC projects only explicitly known worked/confirmed grids and never fabricates a world-grid needed list.
- Open-universe awards carry a numeric remaining-to-threshold count when specific needed identities are unknowable.
- Map records retain immutable contributing QSO ids, confirmed QSO ids, bands, controlled mode groups, exact MODE/SUBMODE labels, target provenance, and accepted confirmation sources.
- Map aggregation is deterministic regardless of input iteration order.
- Existing AwardsCenterQuery mode/band/date filters are preserved.
- Geometry identity/source/version/license metadata is separated from geometry payload via an external asset binding.
- Missing geometry remains unbound metadata; FieldOps does not synthesize coordinates or boundaries.
- A geometry catalog returning the wrong target identity fails closed.
- AwardsCenterApplicationService builds map layers from the same authoritative LogbookRepository and current AwardEvidenceRepository used by Awards Center.
- Production map projection code never inspects callsigns to infer state/grid geography.
- DXCC, Triple Play matrices, IOTA, POTA, SOTA, CQ/ITU zones, and other unsupported geography are not flattened or guessed into this checkpoint.

Host/CI gates:

- CP-0007A award-area map projection: **96/96 PASS**.
- CP-0006G extended official award catalog/grid evaluator: **96/96 PASS**.
- CP-0006F Awards Center application service: **64/64 PASS**.
- CP-0006E award evidence persistence/import: **92/92 PASS**.
- CP-0006D Awards Center projection: **114/114 PASS**.
- CP-0006C award target/composite evaluator: **77/77 PASS**.
- CP-0006B base official award catalog: **115/115 PASS**.
- CP-0006A award evaluator: **67/67 PASS**.
- CP-0005B manual/digital LoTW queue: **47/47 PASS**.
- CP-0005A universal QSO/logger: **78/78 PASS**.
- CP-0003B LoTW transaction: **53/53 PASS**.
- Inherited core: **42,062 PASS**.

Evidence:

- research/maps/CP-0007A_AWARD_MAP_PROJECTION.md
- research/awards/CP-0006G_EXTENDED_AWARD_CATALOG.md
- SOFTWARE_TRACK.md
- CP-0007A finalization workflow run: {run_id}

### Evidence boundary

CP-0007A proves the award-area read model and geometry-reference boundary only. It does not claim real polygons/coordinates, U.S. state geometry data, Maidenhead geometry payload generation, DXCC/IOTA/POTA/SOTA geometry, map SDK rendering, offline tiles, sponsor claimability, real account synchronization, or FTX-1 hardware proof.

### Inherited verified ancestry

CP-0006G-EXTENDED_AWARD_CATALOG, CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0007B — Award geometry providers and offline geometry-pack contract",
    readme,
    count=1,
)

readme = readme.replace(
    "Current Git source baseline: " + chr(96) + "CP-0006G-EXTENDED_AWARD_CATALOG" + chr(96),
    "Current Git source baseline: " + chr(96) + "CP-0007A-AWARD_MAP_PROJECTION" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0007B — Award geometry providers and offline geometry-pack contract.**

1. Define a platform-independent, versioned geometry payload/provider contract that resolves CP-0007A stable geometry identities without embedding award logic.
2. Verify the Maidenhead four-character grid specification from an authoritative or primary technical source and implement deterministic grid-cell bounds from the locator itself.
3. Define a source/version/license-attributed U.S. state geometry-pack adapter and exercise it with compact CI fixtures before considering a full offline dataset.
4. Require every externally sourced geometry payload to retain source id, version, URL where available, license metadata, and retrieval/build version.
5. Keep geometry payloads replaceable independently of award rules and award progress.
6. Fail closed on malformed geometry, identity mismatch, source/license omission, or unsupported target kinds.
7. Keep geometry usable offline and keep Android/Compose/map-SDK rendering outside the core provider contract.
8. Do not derive state/grid boundaries or coordinates from callsigns.
9. CP-0003C remains DEFERRED; do not return to it until the owner explicitly says resume CP-0003C.
10. Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue with the next GitHub/CI-only checkpoint.

"""

readme = replace_one(
    readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract",
    "current exact next action",
)
readme_path.write_text(readme)

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0007B — Award geometry providers and offline geometry-pack contract**

Define a versioned geometry provider/payload boundary for CP-0007A identities, implement authoritative deterministic Maidenhead four-character cell geometry, and define a source/license-attributed U.S. state geometry-pack adapter using compact CI fixtures. Keep Android map rendering and callsign-derived geography out of the core provider.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0007A-AWARD_MAP_PROJECTION" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0007B-AWARD_GEOMETRY_PROVIDERS

Required scope:

- platform-independent versioned geometry provider/payload contract
- resolve stable CP-0007A target identities without embedding award rules
- authoritative/primary-source verification for Maidenhead four-character locator geometry
- deterministic Maidenhead cell bounds
- source/version/license-attributed U.S. state geometry-pack adapter contract
- compact CI geometry fixtures before any full offline dataset
- fail closed on malformed geometry, identity mismatch, missing source/license metadata, or unsupported target kinds
- geometry payload replaceable independently of award progress
- offline-capable core data path
- no callsign-derived geography
- no Android/Compose/map SDK dependency in core provider contract
- no real sponsor accounts, credentials, claim submission, phone/radio/RF/manual hardware work
- skip later hardware/account-gated checkpoints under the owner execution override

## Resume rule""",
    track,
    count=1,
    flags=re.S,
)
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0007A — Award-area map projection foundation" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0007A — Award-area map projection foundation

Parent durable checkpoint: CP-0006G-EXTENDED_AWARD_CATALOG.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a platform-independent award-area map projection for U.S. states and Maidenhead four-character grids. It distinguishes needed, worked-unconfirmed, confirmed, and local-threshold-met semantics; handles finite WAS/FFMA universes separately from open-ended VUCC grids; preserves deterministic QSO/provenance aggregation; and exposes metadata-only geometry bindings with no synthesized coordinates or boundaries. AwardsCenterApplicationService reads the same authoritative logbook/evidence repositories for both cards and map layers.

Host/CI gate: award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/maps/CP-0007A_AWARD_MAP_PROJECTION.md.
"""
    history_path.write_text(history)
