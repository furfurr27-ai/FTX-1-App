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
(root / "VERSION").write_text("v25-production-us-state-geometry-pack\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0007C-US_STATE_GEOMETRY_PACK

Parent durable checkpoint: CP-0007B-AWARD_GEOMETRY_PROVIDERS.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0007C is a GREEN host/CI + official-source-data checkpoint. It pins the official U.S. Census Bureau 2025 national States 1:20,000,000 KML artifact and converts exactly the 50 ARRL WAS state identities into a deterministic, integrity-checked offline geometry pack.

CP-0007C proves:

- Exact upstream artifact: cb_2025_us_state_20m.zip from the official Census GENZ2025 KML directory.
- Upstream byte size: 158,017; SHA-256: efddd884f1442ef233b1ba9c12dddbd66b6fdf94da6a373e1556aefe3dbc5751.
- The upstream KML contains 52 placemarks; the builder emits exactly the 50 verified WAS state identities and excludes non-WAS state/equivalent records.
- DC, PR, AS, GU, MP, and VI are not members of the production 50-state WAS geometry universe.
- Census multipart/island geometry is preserved, including Alaska 47 polygons, Hawaii 8, California 6, Michigan 6, Florida 4, and Massachusetts 3.
- Alaska preserves geometry on both longitude signs and has no unsplit ring segment crossing the antimeridian; maximum adjacent longitude jump is 0.980542000000014 degrees.
- The canonical production offline pack is 324,531 bytes with SHA-256 5feb8c18688936a526523cb536766130be06b14ebfa918b3d99e39bfbcb0a130.
- Every state has a deterministic canonical feature SHA-256 and verified polygon count.
- The build workflow re-downloads the pinned Census artifact, verifies its hash/size, regenerates the production pack and metadata, and byte-compares the results against committed artifacts.
- Production coordinates are stored as an offline data asset rather than a giant Kotlin initializer, avoiding compiler heap exhaustion while preserving identical canonical geometry and hashes.
- The runtime loader verifies raw pack SHA, canonical feature hashes, polygon counts, exact state identity set, sorted state order, and canonical whole-pack hash before exposing geometry.
- Corrupted pack bytes or non-canonical line endings fail closed.
- All 50 WAS states bind production Census geometry and all 488 FFMA grids continue to bind deterministic IARU-derived geometry.
- Runtime geometry loading requires no network access and does not derive geography from callsigns.
- Production geometry remains independent of Android, Compose, map SDKs, accounts, credentials, the FTX-1, and RF behavior.

Host/CI gates:

- CP-0007C production U.S. state geometry pack: **363/363 PASS**.
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

- research/maps/CP-0007C_PRODUCTION_US_STATE_PACK.md
- research/maps/US_STATE_2025_20M_PACK.json
- research/maps/GEOMETRY_SOURCES.tsv
- core/src/main/resources/dev/n0png/fieldops/maps/us_states_2025_20m.pack
- SOFTWARE_TRACK.md
- CP-0007C finalization workflow run: {run_id}

### Evidence boundary

CP-0007C proves the production offline geometry pack for the 50 WAS states. It does not claim Android map rendering, basemap/offline tiles, DXCC boundaries, CQ/ITU zones, IOTA/POTA/SOTA geometry, propagation data, live/heard station geography, or real sponsor/account state.

### Inherited verified ancestry

CP-0007B-AWARD_GEOMETRY_PROVIDERS, CP-0007A-AWARD_MAP_PROJECTION, CP-0006G-EXTENDED_AWARD_CATALOG, CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008A — Propagation intelligence domain and source-normalization foundation",
    readme,
    count=1,
)

readme = readme.replace(
    "Current Git source baseline: " + chr(96) + "CP-0007B-AWARD_GEOMETRY_PROVIDERS" + chr(96),
    "Current Git source baseline: " + chr(96) + "CP-0007C-US_STATE_GEOMETRY_PACK" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0008A — Propagation intelligence domain and source-normalization foundation.**

1. Define platform-independent normalized propagation observations and snapshots.
2. Model source identity/provenance, observation time, retrieval time, staleness, confidence, geographic coverage, band/frequency context, and data quality.
3. Define normalized solar/geomagnetic context and ionospheric map-product records capable of representing MUF/foF2-style information without hard-coding one provider into UI code.
4. Define normalized heard/spot path evidence suitable for later PSK Reporter, WSPR, and FieldOps observations while keeping observations distinct from QSOs.
5. Define band-specific path/usability assessments with explicit reasons/explanations rather than one unexplained heat-map score.
6. Define offline snapshot/cache interfaces so last-known propagation context remains inspectable without a network connection.
7. Use deterministic synthetic CI fixtures only for this foundation; do not require real API keys or provider accounts.
8. Keep live external provider integrations separately gated when credentials/rate limits/terms require them.
9. Keep Android/Compose/map-SDK rendering outside this checkpoint.
10. CP-0003C remains DEFERRED; do not return to it until the owner explicitly says resume CP-0003C.
11. Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue with the next GitHub/CI-only checkpoint.

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

**CP-0008A — Propagation intelligence domain and source-normalization foundation**

Define provider-neutral propagation observations/snapshots, provenance and freshness/confidence metadata, solar/geomagnetic and ionospheric product models, heard/spot path evidence, explainable band/path usability assessments, and offline snapshot/cache interfaces. Use deterministic synthetic CI fixtures only; keep real credentialed providers and Android map rendering out of this foundation.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0007C-US_STATE_GEOMETRY_PACK" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION

Required scope:

- platform-independent normalized propagation observations and snapshots
- explicit source provenance, observation/retrieval timestamps, freshness/staleness, confidence, geographic coverage, and quality metadata
- normalized solar/geomagnetic context model
- provider-neutral ionospheric map-product model for MUF/foF2-style data
- normalized heard/spot path evidence for later PSK Reporter, WSPR, and FieldOps observations
- observations remain distinct from QSOs
- band/frequency-aware path usability assessment with explicit reason/explanation model
- offline snapshot/cache interfaces for last-known propagation context
- deterministic synthetic CI fixtures only in the foundation
- no hard-coded provider inside UI/domain contracts
- no real API keys/provider accounts in this checkpoint
- no callsign-derived fabricated geography
- no Android/Compose/map SDK dependency
- no phone/radio/RF/manual hardware work
- skip hardware/account-gated checkpoints under the owner execution override

## Resume rule""",
    track,
    count=1,
    flags=re.S,
)
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0007C — Production U.S. state offline geometry pack" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0007C — Production U.S. state offline geometry pack

Parent durable checkpoint: CP-0007B-AWARD_GEOMETRY_PROVIDERS.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now includes a production offline geometry pack for exactly the 50 ARRL WAS states generated from the official U.S. Census Bureau 2025 national States 1:20,000,000 KML artifact. The upstream artifact is pinned by filename, URL, byte size, vintage, scale, and SHA-256; the generated 324,531-byte canonical pack and every state feature are independently hashed. Multipart/island geometry is preserved, Alaska antimeridian behavior is explicitly verified, and the pack is reproducible byte-for-byte from the pinned source. Production coordinates are stored as an offline asset with a small fail-closed loader instead of a compiler-heavy generated Kotlin initializer.

Host/CI gate: production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/maps/CP-0007C_PRODUCTION_US_STATE_PACK.md, research/maps/US_STATE_2025_20M_PACK.json, research/maps/GEOMETRY_SOURCES.tsv, and the production offline geometry asset.
"""
    history_path.write_text(history)
