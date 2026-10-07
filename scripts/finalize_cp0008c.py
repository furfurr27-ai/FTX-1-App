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
(root / "VERSION").write_text("v28-noaa-swpc-glotec-ionospheric-adapter\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER

Parent durable checkpoint: CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008C is a GREEN host/CI + pinned-public-source checkpoint. It adds transport-independent normalization of NOAA/SWPC GloTEC vertical total electron content into the provider-neutral ionospheric-map domain without converting TEC into MUF, heard-path evidence, a QSO, or an unexplained propagation score.

CP-0008C proves:

- The official NOAA/SWPC GloTEC operational product, versioned GeoJSON directory, cadence, global grid semantics, TEC units, and provider quality semantics are source-pinned.
- Provider-neutral ionospheric metrics now include VTEC_TECU while remaining distinct from foF2, MUF, and hmF2.
- Generic ionospheric samples can retain paired provider quality code/explanation metadata without hard-coding GloTEC into the domain model.
- A bounded four-cell fixture extracted from the recorded NOAA 2026-09-09T15:15:00Z grid is pinned at 1,573 bytes with SHA-256 a98741d9a9586082db0eb357f3baf35be09a2646c8ab5b1b4203d4852b09bac2.
- GeoJSON coordinates remain explicit provider coordinates in longitude/latitude order; no callsign-derived geography is introduced.
- Canonical artifact filename time must match provider time_tag; retrieval UTC remains separate.
- No separately established generation timestamp is fabricated; generatedAtUtcMillis remains null for this pinned schema.
- GloTEC quality_flag values 0 through 5 are preserved per sample with provider semantics rather than being converted into path confidence.
- Root, metadata, feature, geometry, property, numeric-domain, duplicate-coordinate, timestamp, source-URL, and duplicate-JSON-key drift fail closed.
- GloTEC-only ionospheric context remains UNKNOWN for HF path usability and retains INSUFFICIENT_PATH_EVIDENCE.
- Production GloTEC parsing code contains no network transport, Android/Compose/map-SDK types, QSO/LoTW state, FTX-1/PTT behavior, or direct TEC-to-MUF/path conversion.
- The superseded CP-0008B finalizer is manual-only so later propagation checkpoints cannot regress durable state.

Authoritative/direct research checked:

- NWS Service Change Notice 25-04 for GloTEC transition to operations, real-time assimilation, ten-minute ASCII GeoJSON cadence, and the 2.5-degree latitude by 5-degree longitude global grid.
- Current NOAA/SWPC GloTEC product information for TEC output and quality_flag semantics.
- NOAA/SWPC public versioned GloTEC GeoJSON directory.

Fixture provenance additionally checked:

- RealDougEubanks/solarham pinned commit 7382dc23cd5f74c00fae99387d2052940c5c63b7, which retains the recorded full NOAA grid used only to extract exact deterministic fixture rows.

Host/CI gates:

- CP-0008C GloTEC ionospheric adapter: **87/87 PASS**.
- CP-0008B NOAA SWPC adapter: **53/53 PASS**.
- CP-0008A propagation intelligence foundation: **154/154 PASS**.
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

- research/propagation/CP-0008C_GLOTEC_IONOSPHERIC_ADAPTER.md
- research/propagation/NOAA_SWPC_GLOTEC_FIXTURE.json
- research/propagation/PROPAGATION_SOURCES.tsv
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/NoaaSwpcGlotecAdapter.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationDomain.kt
- SOFTWARE_TRACK.md
- CP-0008C finalization workflow run: {run_id}

### Evidence boundary

CP-0008C proves deterministic normalization of the pinned GloTEC GeoJSON shape into provider-neutral VTEC context and preservation of provider quality/provenance. It does not claim NOAA uptime, current real-world TEC, a complete runtime global fetch, TEC-to-MUF conversion, HF path prediction from TEC, Android network caching/rendering, or GIRO/PSK Reporter/WSPRnet/HFcast/VOACAP integration.

### Inherited verified ancestry

CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER, CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION, CP-0007C-US_STATE_GEOMETRY_PACK, CP-0007B-AWARD_GEOMETRY_PROVIDERS, CP-0007A-AWARD_MAP_PROJECTION, CP-0006G-EXTENDED_AWARD_CATALOG, CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008D — PSK Reporter public heard-path adapter",
    readme,
    count=1,
)

readme = readme.replace(
    "Current Git source baseline: `CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER`",
    "Current Git source baseline: `CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER`",
    1,
)

next_section = """## Current exact next action

**CP-0008D — PSK Reporter public heard-path adapter.**

1. Pin the official no-credential PSK Reporter retrieval endpoint/query semantics and a deterministic representative XML response fixture.
2. Normalize reception reports into CP-0008A HeardPathObservation only when both transmitter and receiver have explicit valid Maidenhead locators; never derive geography from callsigns.
3. Preserve sender/receiver callsigns as labels, exact frequency, provider observation timestamp, mode and SNR when supplied, plus source/retrieval provenance and data quality.
4. Keep every reception report separate from QSO/logbook/LoTW state; a one-way heard report must never become an authoritative contact.
5. Fail closed or explicitly reject records lacking the minimum fields required for a geographic heard path; do not invent location, frequency, mode or time.
6. Respect PSK Reporter's published retrieval guidance, including avoiding retrieval more often than once every five minutes; deterministic CI must not poll the live service.
7. Keep transport outside the parser/normalizer boundary. A live schema check, if used, must be separately bounded and must not make CI depend on current station activity.
8. Keep WSPRnet, GIRO, HFcast/VOACAP, Android/Compose/map-SDK rendering and account/credential providers outside this checkpoint.
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

**CP-0008D — PSK Reporter public heard-path adapter**

Pin the official no-credential PSK Reporter retrieval/query schema and a deterministic XML fixture, then normalize only reports with explicit transmitter and receiver Maidenhead locators into HeardPathObservation. Preserve provider frequency/time/mode/SNR/provenance, never infer geography from callsigns, never promote a reception report into a QSO, and keep live polling out of deterministic CI.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n`CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER`\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER

Required scope:

- official no-credential PSK Reporter retrieval endpoint/query/schema pin
- deterministic representative XML fixture; no live-service dependency in required CI
- transport-independent parser/normalizer into CP-0008A HeardPathObservation
- explicit valid transmitter and receiver Maidenhead locators required; no callsign-derived geography
- exact provider frequency and observation timestamp retained
- mode/SNR retained when explicitly supplied by provider
- sender/receiver callsigns retained as labels only
- source/retrieval provenance, quality and confidence explanation retained
- one-way reports remain separate from QSO/logbook/LoTW state
- fail closed or explicitly reject records missing minimum geographic/path fields
- published PSK Reporter retrieval-rate guidance documented and respected by any later transport adapter
- no WSPRnet/GIRO/HFcast/VOACAP integration
- no Android/Compose/map SDK dependency
- no real accounts/credentials
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
if "## CP-0008C — NOAA SWPC GloTEC public ionospheric map adapter" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0008C — NOAA SWPC GloTEC public ionospheric map adapter

Parent durable checkpoint: CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now normalizes the pinned NOAA/SWPC GloTEC GeoJSON product into provider-neutral VTEC/TECU ionospheric-map evidence. Explicit provider coordinates, provider time_tag, retrieval UTC, quality_flag metadata, source identity, confidence basis/explanation and bounded coverage are retained. VTEC remains distinct from foF2/MUF/hmF2, and GloTEC-only context cannot produce a GOOD/MARGINAL/POOR path assessment or become a QSO.

The bounded deterministic fixture contains four exact feature rows from the recorded NOAA 2026-09-09T15:15:00Z grid, is 1,573 bytes, and has SHA-256 a98741d9a9586082db0eb357f3baf35be09a2646c8ab5b1b4203d4852b09bac2. NOAA/NWS remains authoritative for product semantics; the pinned public GitHub mirror is used only for exact fixture-row provenance.

Host/CI gate: GloTEC adapter 87; NOAA solar/geomagnetic adapter 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/propagation/CP-0008C_GLOTEC_IONOSPHERIC_ADAPTER.md, research/propagation/NOAA_SWPC_GLOTEC_FIXTURE.json, research/propagation/PROPAGATION_SOURCES.tsv, and core/src/main/kotlin/dev/n0png/fieldops/core/propagation/NoaaSwpcGlotecAdapter.kt.
"""
    history_path.write_text(history)
