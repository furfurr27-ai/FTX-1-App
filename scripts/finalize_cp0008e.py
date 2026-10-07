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
(root / "VERSION").write_text("v30-propagation-aggregation-offline-cache\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE

Parent durable checkpoint: CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008E is a GREEN host/CI checkpoint. It adds deterministic provider-neutral propagation evidence aggregation, source-specific FieldOps freshness policies, repeated-payload deduplication, and a bounded versioned offline snapshot cache around the existing PropagationSnapshotStore contract.

CP-0008E proves:

- Already-normalized NOAA solar/geomagnetic, GloTEC ionospheric, PSK Reporter heard-path and modeled-path evidence can be combined into deterministic PropagationSnapshot instances.
- Snapshot capture UTC cannot precede retained source retrieval UTC; provider observation/retrieval timestamps are preserved rather than rewritten.
- Missing provider generation timestamps remain missing, including GloTEC generatedAtUtcMillis = null.
- Evidence categories are sorted deterministically and remain separate: solar/geomagnetic, ionospheric, heard paths and modeled paths.
- Repeated payloads from the same normalized source deduplicate by evidence id and retain latest retrieval provenance.
- Heard-path repeated-payload merge uses max(reportCount), not addition, so repeated polling cannot inflate path evidence.
- Same evidence ids with materially different content/provenance fail closed.
- The overlapping NOAA observed-Kp row present in both dedicated Kp and forecast fixtures is treated as a provenance collision; orchestration uses the dedicated Kp feed for observed records and forecast feed for estimated/predicted records.
- Source-specific FieldOps freshness defaults exist for PSK Reporter, GloTEC, NOAA Kp-family and NOAA F10.7; unknown providers retain the inherited operational default.
- Future-dated provider timestamps remain FUTURE_DATED rather than being rewritten to look current.
- Deterministic snapshot ids fingerprint the complete versioned normalized snapshot content rather than depending on input ordering.
- FilePropagationSnapshotStore persists a bounded versioned history in propagation-snapshots-v1.bin and supports latest, exact-id, as-of and history lookup across restart.
- The binary codec preserves all current propagation domain fields, all coverage shapes, optional values, confidence/quality metadata and source provenance.
- Cache collection/string sizes are bounded; corrupt files, unsupported versions and trailing bytes fail closed.
- Writes use a temporary file plus atomic replace where supported.
- Context-only aggregation does not create a heard path, modeled path or propagation score; assessment remains UNKNOWN without matching path evidence.
- Production aggregation/cache code contains no HTTP transport, Android/Compose/map-SDK, FTX-1/PTT, QSO/logbook-write or LoTW behavior.
- The superseded CP-0008D finalizer is manual-only so later propagation checkpoints cannot regress durable state.

Host/CI gates:

- CP-0008E propagation aggregation/offline cache: **101/101 PASS**.
- CP-0008D PSK Reporter heard-path adapter: **168/168 PASS**.
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

- research/propagation/CP-0008E_PROPAGATION_AGGREGATION_OFFLINE_CACHE.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationAggregation.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/FilePropagationSnapshotStore.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationAggregationOfflineCacheTests.kt
- SOFTWARE_TRACK.md
- CP-0008E finalization workflow run: {run_id}

### Evidence boundary

CP-0008E proves deterministic aggregation and bounded offline persistence of already-normalized propagation evidence. It does not claim live provider polling, Android filesystem behavior, multi-process locking, WSPRnet/GIRO ingestion, HFcast/VOACAP prediction, map rendering, or real-world propagation accuracy.

### Inherited verified ancestry

CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER, CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER, CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER, CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION, CP-0007C-US_STATE_GEOMETRY_PACK, CP-0007B-AWARD_GEOMETRY_PROVIDERS, CP-0007A-AWARD_MAP_PROJECTION, CP-0006G-EXTENDED_AWARD_CATALOG, CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008F — propagation operating-picture projection service",
    readme,
    count=1,
)

readme = readme.replace(
    "Current Git source baseline: `CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER`",
    "Current Git source baseline: `CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE`",
    1,
)

next_section = """## Current exact next action

**CP-0008F — propagation operating-picture projection service.**

1. Build a platform-independent projection/application service over PropagationSnapshot and PropagationSnapshotStore for the operating map/workspace.
2. Project heard-path evidence into deterministic map-ready path records while preserving explicit endpoint geography, callsign labels, frequency, band, mode, SNR, reportCount, source provenance and source-specific freshness.
3. Project ionospheric products into provider-neutral metric/sample records with explicit metric identity and freshness; do not interpolate GloTEC/TEC into MUF or an unexplained heat score.
4. Project solar/geomagnetic context into concise provider-neutral status records with freshness and provenance.
5. Support deterministic filters for band/frequency, mode, source and freshness without modifying underlying evidence.
6. Allow an optional selected-path assessment using the existing explainable PropagationAssessmentEngine and preserve its reason/evidence links.
7. Surface snapshot capture time, source retrieval age and offline-cache availability so stale/offline operating pictures remain inspectable rather than appearing live.
8. Keep logged QSOs, heard paths, ionospheric context and modeled paths visually/logically distinct in projection state.
9. Keep Android, Compose, Google Maps/Mapbox and concrete rendering outside CP-0008F.
10. Keep WSPRnet/WSPR.live, GIRO, HFcast/VOACAP, live provider polling, accounts/credentials, phone/radio/RF testing and manual hardware validation outside this checkpoint.
11. CP-0003C remains DEFERRED; do not return to it until the owner explicitly says resume CP-0003C.

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

**CP-0008F — propagation operating-picture projection service**

Build a platform-independent projection/application service over PropagationSnapshot/PropagationSnapshotStore that exposes deterministic heard-path, ionospheric, solar/geomagnetic, modeled-path and optional explainable selected-path assessment records for the future operating map/workspace. Preserve provenance/freshness and evidence-category separation; do not add Android/map-SDK rendering or new live providers in this checkpoint.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n`CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE`\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION

Required scope:

- platform-independent projection/application service over PropagationSnapshot and PropagationSnapshotStore
- deterministic heard-path map-ready records retaining explicit endpoint geography, callsigns, frequency/band/mode/SNR/reportCount/provenance/freshness
- provider-neutral ionospheric metric/sample projection with no TEC-to-MUF or unexplained heat-score conversion
- concise solar/geomagnetic context projection with freshness/provenance
- modeled paths remain distinct from observed heard paths
- deterministic band/frequency/mode/source/freshness filters
- optional selected-path assessment using existing explainable PropagationAssessmentEngine
- snapshot capture/source-retrieval/offline-cache status surfaced explicitly
- no QSO promotion from heard evidence
- no Android/Compose/Google Maps/Mapbox dependency
- no WSPRnet/WSPR.live/GIRO/HFcast/VOACAP integration
- no live provider polling requirement
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
if "## CP-0008E — Propagation evidence aggregation and offline cache service" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0008E — Propagation evidence aggregation and offline cache service

Parent durable checkpoint: CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now aggregates already-normalized NOAA solar/geomagnetic, GloTEC ionospheric, PSK Reporter heard-path and modeled evidence into deterministic PropagationSnapshot instances. Provider observation/retrieval timestamps survive unchanged, repeated same-source payloads deduplicate deterministically, heard reportCount uses max rather than addition, and materially conflicting provenance fails closed.

The existing PropagationSnapshotStore contract now has a bounded versioned file-backed implementation with deterministic binary round-trip, restart/reload history, as-of lookup, deterministic trimming and corruption/version checks. Context-only aggregation remains UNKNOWN for path usability and no new QSO, LoTW, model or heat score is created by aggregation.

The pinned NOAA fixtures expose one observed-Kp identity in both the dedicated Kp and forecast products. CP-0008E treats differing provenance as a collision; deterministic orchestration uses the dedicated feed for observed Kp and the forecast feed for estimated/predicted Kp.

Host/CI gate: aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA adapter 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/propagation/CP-0008E_PROPAGATION_AGGREGATION_OFFLINE_CACHE.md, core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationAggregation.kt, core/src/main/kotlin/dev/n0png/fieldops/core/propagation/FilePropagationSnapshotStore.kt.
"""
    history_path.write_text(history)
