#!/usr/bin/env python3
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]

def replace_one(text, pattern, replacement, label):
    out, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; found {count}")
    return out

(root / "VERSION").write_text("v31-propagation-projection-service\n")
readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION

Parent durable checkpoint: CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008F is a GREEN host/CI checkpoint. It projects cached provider-neutral propagation evidence into deterministic platform-independent workspace state without Android/map-SDK rendering or evidence-category collapse.

CP-0008F proves:

- Heard paths project separately with explicit endpoints, callsigns, exact frequency, band, mode, SNR, reportCount, provenance, confidence, quality and source-specific freshness.
- Ionospheric products preserve coverage, metric identity, samples, provider quality and optional generation/reference-distance fields; VTEC remains VTEC and is not converted into MUF or a heat score.
- Solar/geomagnetic context preserves explicit measurements without becoming an observed RF path.
- Modeled paths remain separate from heard paths and preserve explicit endpoints, MUF/LUF limits, model-input summary and provenance.
- Deterministic band/mode/frequency filtering applies only to heard records where those fields exist; source/freshness filtering applies across evidence categories.
- Projection ordering is deterministic: newest observation first, evidence id as tie-break.
- Optional selected-path assessment uses the existing explainable PropagationAssessmentEngine against the complete snapshot and retains reason/evidence links independently of display filtering.
- Snapshot status exposes capture time/age, source count, source retrieval bounds, stale/future evidence flags and offline-cache availability.
- Future snapshot/retrieval times are explicit and use null age rather than fabricated negative ages.
- FilePropagationSnapshotStore advertises OfflinePropagationSnapshotStore capability without coupling the projection service to the concrete file-store class.
- Production projection code has no Android/Compose, Google Maps/Mapbox, HTTP/provider polling, FTX-1/PTT, QSO/logbook-write, LoTW or opaque heat-score state.
- The superseded CP-0008E finalizer is manual-only.

Host/CI gates:

- CP-0008F propagation projection: **79/79 PASS**.
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

- research/propagation/CP-0008F_PROPAGATION_PROJECTION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationProjectionModels.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationProjectionService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationProjectionServiceTests.kt
- SOFTWARE_TRACK.md
- CP-0008F finalization workflow run: {run_id}

### Evidence boundary

CP-0008F proves deterministic provider-neutral projection/application state over stored propagation snapshots. It does not claim Android UI/map rendering, live provider polling, WSPRnet/GIRO ingestion, HFcast/VOACAP prediction, geographic interpolation, or real-world propagation accuracy.

### Inherited verified ancestry

CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE, CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER, CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER, CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER, CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION and all earlier verified checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "baseline",
)
readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008G — propagation source refresh coordinator",
    readme,
    count=1,
)
readme = re.sub(
    r"- Current Git source baseline: .+",
    "- Current Git source baseline: CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION",
    readme,
    count=1,
)

next_section = """## Current exact next action

**CP-0008G — propagation source refresh coordinator.**

1. Add a platform-neutral refresh coordinator above the existing normalized source adapters, aggregator, snapshot store and projection service.
2. Define source fetch-result interfaces and deterministic scheduling/cadence state without binding core code to Android WorkManager or a concrete HTTP client.
3. Encode canonical NOAA Kp selection: dedicated Kp for observed records; forecast product for estimated/predicted records.
4. Track per-source success/failure, last attempt, last success, next eligible refresh and bounded retry/backoff state without discarding the last good snapshot.
5. Aggregate successful normalized source results into a new snapshot only when capture/provenance invariants hold, then persist through PropagationSnapshotStore.
6. Preserve partial-source failure explicitly; do not invent evidence for a failed source and do not erase still-inspectable cached state.
7. Provide deterministic fake-source CI proving cadence, retry, canonical-source handling, partial failure and last-good snapshot behavior.
8. Keep Android scheduling/network stacks, credentials/accounts, WSPRnet/WSPR.live, GIRO, HFcast/VOACAP, phone/radio/RF/manual validation outside CP-0008G.
9. CP-0003C remains DEFERRED until the owner explicitly says resume CP-0003C.

"""
readme = replace_one(
    readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract",
    "next action",
)
readme_path.write_text(readme)

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0008G — propagation source refresh coordinator**

Build a platform-neutral refresh coordinator over the existing public-source adapters, aggregation/cache, and projection layers. Track source cadence, success/failure, bounded retry/backoff, canonical NOAA observed/forecast selection, partial-source failure, and last-good cached snapshots using deterministic fake-source CI. Keep concrete Android scheduling/network transport and new providers outside this checkpoint.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(
    track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\nCP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION\n\n## Deferred but incomplete hardware/account work",
    "track parent",
)
track = replace_one(
    track,
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR

Required scope:

- platform-neutral refresh coordinator over normalized propagation source adapters
- deterministic source cadence and eligibility state
- source success/failure, last-attempt/last-success and bounded retry/backoff metadata
- canonical NOAA Kp observed versus estimated/predicted source selection
- partial-source failure without fabricated replacement evidence
- last-good snapshot preservation through PropagationSnapshotStore
- deterministic fake-source host/CI tests
- no Android WorkManager/concrete HTTP client
- no WSPRnet/WSPR.live/GIRO/HFcast/VOACAP integration
- no credentials/accounts
- no phone/radio/RF/manual hardware work
- preserve CP-0003C owner deferral

## Resume rule""",
    "track active checkpoint",
)
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008F — Propagation operating-picture projection service" not in history:
    history += f"""

## CP-0008F — Propagation operating-picture projection service

Parent durable checkpoint: CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE.

FieldOps now projects stored provider-neutral propagation snapshots into deterministic workspace state while keeping heard RF paths, ionospheric context, solar/geomagnetic context and modeled paths distinct. Heard records retain explicit endpoint geography/callsigns/frequency/band/mode/SNR/reportCount/provenance/freshness. Ionospheric VTEC remains VTEC and is not converted into MUF or a heat score. Optional selected-path assessment reuses the explainable assessment engine against the complete snapshot.

Projection status exposes snapshot age, source retrieval bounds, stale/future evidence state and whether an offline-persistent store is available. FilePropagationSnapshotStore now advertises that capability through a provider-neutral marker interface.

Host/CI gate: projection 79; aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended awards 96; Awards service 64; persistence 92; Awards projection 114; target 77; catalog 115; award evaluator 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: {run_id}.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008F_PROPAGATION_PROJECTION.md and the PropagationProjectionModels/PropagationProjectionService source and tests.
"""
    history_path.write_text(history)
