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
(root / "VERSION").write_text("v24-award-geometry-providers\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0007B-AWARD_GEOMETRY_PROVIDERS

Parent durable checkpoint: CP-0007A-AWARD_MAP_PROJECTION.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0007B is a GREEN host/CI + authoritative-source research checkpoint. It adds platform-independent geometry payload/provider contracts, deterministic four-character Maidenhead grid bounds derived from the IARU Region 1 specification, and a source/version/license-gated offline U.S. state geometry-pack adapter contract.

CP-0007B proves:

- Geometry payload types validate world coordinates, bounds, polygon rings, and multi-polygons without Android or map-SDK types.
- Geometry provenance is explicitly classified as DERIVED_STANDARD, EXTERNAL_DATASET, or SYNTHETIC_FIXTURE.
- Every non-derived geometry payload requires license/public-domain metadata.
- External geometry datasets require an authoritative HTTPS source URL.
- Maidenhead four-character cells are deterministically derived from IARU Region 1 VHF Handbook 9.01 sections 6.2-6.3 using the documented 20x10 degree fields, 2x1 degree squares, west-to-east/south-to-north indexing, origin at 180W/90S, and WGS-84 basis.
- Known and edge grid cells resolve to the expected 2 degree longitude by 1 degree latitude bounds.
- U.S. state geometry packs are versioned, offline, target-kind constrained, feature-count checked, identity-unique, and asset-id unique.
- The production state-pack contract pins the official U.S. Census Bureau 2025 Cartographic Boundary Files source and records the Census government-work/attribution rights metadata.
- Synthetic CI state polygons are explicitly marked fixture-only and explicitly state that they contain no Census boundary geometry.
- AwardGeometryRegistry rejects duplicate provider ids, multi-provider target conflicts, and mismatched returned target identities.
- CP-0007A map bindings remain metadata-only while actual payloads resolve separately through the registry.
- All 488 FFMA four-character grids resolve to deterministic IARU-derived geometry in host tests.
- Geometry provider production code performs no network access and never derives geography from callsigns.

Host/CI gates:

- CP-0007B award geometry providers/offline pack contract: **102/102 PASS**.
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

- research/maps/CP-0007B_AWARD_GEOMETRY_PROVIDERS.md
- research/maps/GEOMETRY_SOURCES.tsv
- research/maps/CP-0007A_AWARD_MAP_PROJECTION.md
- SOFTWARE_TRACK.md
- CP-0007B finalization workflow run: {run_id}

### Evidence boundary

CP-0007B proves geometry-domain validation, deterministic Maidenhead cell geometry, offline geometry-pack/provider semantics, Census source/rights metadata, synthetic fixture separation, and provider-registry integration. It does not claim a production Census state boundary pack, shapefile/KML/GeoPackage parsing, full production geometry hashes, Android map rendering, offline tiles, or DXCC/IOTA/POTA/SOTA/CQ/ITU geometry.

### Inherited verified ancestry

CP-0007A-AWARD_MAP_PROJECTION, CP-0006G-EXTENDED_AWARD_CATALOG, CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0007C — Production U.S. state offline geometry pack",
    readme,
    count=1,
)

readme = readme.replace(
    "Current Git source baseline: " + chr(96) + "CP-0007A-AWARD_MAP_PROJECTION" + chr(96),
    "Current Git source baseline: " + chr(96) + "CP-0007B-AWARD_GEOMETRY_PROVIDERS" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0007C — Production U.S. state offline geometry pack.**

1. Fetch and pin the official U.S. Census Bureau 2025 national States 1:20,000,000 source artifact.
2. Record the exact upstream filename, source URL, retrieval date, source vintage, and cryptographic hash.
3. Convert only the 50 WAS state identities into the CP-0007B platform-independent multi-polygon payload format.
4. Preserve multipart state/island geometry and handle Alaska/antimeridian geometry explicitly; do not clip or silently rewrite topology.
5. Generate deterministic per-feature hashes plus an overall pack hash and versioned manifest.
6. Verify every one of the 50 WAS state identities resolves exactly once and no WAS identity is missing.
7. Keep District of Columbia, Puerto Rico, and territories outside the 50-state WAS geometry universe even if present in the Census source.
8. Retain Census attribution, statistical-boundary disclaimer, source vintage, scale, and rights metadata in the generated pack.
9. Keep raw/upstream source metadata, production generated geometry, and synthetic CI fixtures clearly distinguishable.
10. Keep Android/Compose/map-SDK rendering outside this checkpoint.
11. CP-0003C remains DEFERRED; do not return to it until the owner explicitly says resume CP-0003C.
12. Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue with the next GitHub/CI-only checkpoint.

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

**CP-0007C — Production U.S. state offline geometry pack**

Pin the official Census 2025 national States 1:20,000,000 source artifact, convert exactly the 50 WAS state identities into the CP-0007B offline multi-polygon format, preserve multipart/antimeridian behavior, record deterministic hashes and Census attribution/rights metadata, and keep Android rendering out of the pack builder.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0007B-AWARD_GEOMETRY_PROVIDERS" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0007C-US_STATE_GEOMETRY_PACK

Required scope:

- official Census 2025 national States 1:20,000,000 source artifact pinned with filename/source/hash/vintage
- deterministic conversion into CP-0007B platform-independent multi-polygon payloads
- exactly the 50 WAS state identities in the award geometry pack
- District of Columbia, Puerto Rico, and territories excluded from the WAS pack even if present upstream
- preserve multipart/island topology
- explicit Alaska/antimeridian handling with no silent clipping
- deterministic per-feature hashes and overall pack hash
- Census attribution, statistical-boundary disclaimer, source vintage/scale, and rights metadata retained
- raw/upstream source metadata, generated production pack, and synthetic fixtures clearly separated
- host/CI verification
- no callsign-derived geography
- no Android/Compose/map SDK dependency in the pack builder/provider
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
if "## CP-0007B — Award geometry providers and offline geometry-pack contract" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0007B — Award geometry providers and offline geometry-pack contract

Parent durable checkpoint: CP-0007A-AWARD_MAP_PROJECTION.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has platform-independent geometry primitives, provenance-enforced provider/pack contracts, deterministic IARU-derived four-character Maidenhead cell bounds, a Census-source/rights-attributed U.S. state production-pack manifest contract, explicit synthetic-fixture separation, and a conflict-safe provider registry that bridges actual geometry payloads to CP-0007A metadata-only map bindings.

Host/CI gate: geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/maps/CP-0007B_AWARD_GEOMETRY_PROVIDERS.md and research/maps/GEOMETRY_SOURCES.tsv.
"""
    history_path.write_text(history)
