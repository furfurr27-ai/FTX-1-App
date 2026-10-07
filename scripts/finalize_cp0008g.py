#!/usr/bin/env python3
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]

def replace_one(text, pattern, replacement, label):
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} section, found {count}")
    return updated

(root / "VERSION").write_text("v32-propagation-refresh-coordinator\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR

Parent durable checkpoint: CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008G is a GREEN host/CI checkpoint. It adds deterministic, platform-neutral refresh orchestration over already-normalized propagation sources, the snapshot aggregator/cache, and the operating-picture projection layer.

CP-0008G proves:

- Refresh sources declare a stable key, source role, normal cadence, retry policy, and a fetcher returning normalized PropagationAggregationInput or explicit failure.
- Per-source state retains last attempt, last success, consecutive failures, next eligible refresh, failure message, and retryability.
- Retryable failures use bounded exponential backoff; non-retryable normalized-contract failures wait normal cadence; success clears failure state.
- Source exceptions are isolated as explicit retryable failures rather than aborting the full refresh cycle.
- Built-in source roles fail closed on unmanaged provenance, wrong evidence categories, or retrieval timestamps later than the refresh UTC.
- Canonical NOAA Kp selection uses the dedicated planetary-Kp feed for observed records and removes forecast-product observed rows while retaining estimated/predicted rows.
- Successful sources replace only the cached evidence they manage.
- Failed, cadence-skipped, and unmanaged cached evidence is carried forward unchanged, including original retrieval provenance, so stale evidence ages naturally.
- All-attempt failure and aggregate-invariant failure preserve the last good snapshot and do not mutate snapshot history.
- Empty successful source results never force an invalid empty snapshot.
- Refresh state can survive coordinator recreation through the PropagationRefreshStateStore contract.
- PropagationRefreshWorkspaceService composes refresh with the existing projection layer: successful refresh projects the new snapshot; failed refresh projects the last good snapshot while failure state remains explicit.
- Production refresh code contains no Android/Compose/WorkManager, concrete HTTP/java.net transport, credentials/accounts, FTX-1/PTT/RF behavior, QSO/logbook writes, or LoTW mutation.
- The superseded CP-0008F finalizer is manual-only.

Host/CI gates:

- CP-0008G propagation refresh coordinator: **112/112 PASS**.
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

- research/propagation/CP-0008G_PROPAGATION_REFRESH_COORDINATOR.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRefreshModels.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRefreshCoordinator.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRefreshWorkspaceService.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationRefreshCoordinatorTests.kt
- SOFTWARE_TRACK.md
- CP-0008G finalization workflow run: {run_id}

### Evidence boundary

CP-0008G proves deterministic source orchestration over already-normalized evidence and stored snapshots. It does not claim live network access, provider uptime, Android background execution, battery behavior, new provider schemas, or real-world propagation accuracy.

### Inherited verified ancestry

CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION, CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE, CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER, CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER, CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER, CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION and all earlier verified checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)
readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008H — public propagation transport adapters",
    readme,
    count=1,
)
readme = re.sub(
    r"- Current Git source baseline: .+",
    "- Current Git source baseline: CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR",
    readme,
    count=1,
)

next_section = """## Current exact next action

**CP-0008H — public propagation transport adapters.**

1. Add a platform-neutral request/response transport boundary that can feed the existing NOAA SWPC, GloTEC, and PSK Reporter parsers without putting a concrete HTTP client in core propagation logic.
2. Build source factories/adapters that translate successful public responses into CP-0008G PropagationRefreshSourceDefinition fetch results.
3. Preserve the pinned exact NOAA planetary-Kp, Kp forecast, F10.7, GloTEC index/artifact, and PSK Reporter HTTPS endpoint rules already established by the adapter checkpoints.
4. Implement deterministic GloTEC index-to-latest-artifact selection and fail closed on malformed or non-official artifact URLs.
5. Preserve PSK Reporter query/provenance restrictions and do not persist appcontact or other contact identifiers.
6. Prove response status/body/size/error handling with deterministic fake transport CI; optional live public schema smoke may verify structure but must not make deterministic tests depend on network availability.
7. Keep Android WorkManager, concrete Android networking, credentials/accounts, WSPRnet/WSPR.live, GIRO, HFcast/VOACAP, phone/radio/RF/manual validation outside CP-0008H.
8. CP-0003C remains DEFERRED until the owner explicitly says resume CP-0003C.

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

**CP-0008H — public propagation transport adapters**

Add a platform-neutral request/response transport boundary and source adapters that feed the existing NOAA SWPC, GloTEC, and PSK Reporter parsers into the CP-0008G refresh coordinator. Prove exact endpoint handling, GloTEC latest-artifact selection, response/error/size handling, and PSK Reporter provenance restrictions with deterministic fake transport CI. Keep concrete Android networking/scheduling and new providers outside this checkpoint.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(
    track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\nCP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR\n\n## Deferred but incomplete hardware/account work",
    "software-track parent",
)
track = replace_one(
    track,
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS

Required scope:

- platform-neutral public request/response transport boundary
- source adapters/factories feeding existing NOAA SWPC, GloTEC and PSK Reporter parsers into CP-0008G refresh source definitions
- exact established official HTTPS endpoint restrictions
- deterministic GloTEC index-to-latest-artifact selection
- bounded response/status/body/error handling
- PSK Reporter provenance/contact-identifier restrictions retained
- deterministic fake-transport host/CI tests
- live schema smoke optional and non-authoritative for deterministic completion
- no Android WorkManager/concrete Android networking
- no WSPRnet/WSPR.live/GIRO/HFcast/VOACAP integration
- no credentials/accounts
- no phone/radio/RF/manual hardware work
- preserve CP-0003C owner deferral

## Resume rule""",
    "software-track active checkpoint",
)
track_path.write_text(track)

history_path = root / "checkpoints" / "CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0008G — Propagation source refresh coordinator" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0008G — Propagation source refresh coordinator

Parent durable checkpoint: CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION.

FieldOps now has a deterministic platform-neutral source refresh coordinator over already-normalized propagation evidence. Source definitions carry role/cadence/retry policy, source state tracks attempts/successes/failures/next eligibility, retryable failures use bounded exponential backoff, and successful recovery resets failure state.

Canonical NOAA Kp handling keeps the dedicated Kp feed as observed truth and removes forecast-product observed rows while preserving estimated/predicted rows. Successful sources replace only evidence they manage; failed or cadence-skipped source evidence is carried forward with original retrieval provenance. All-attempt and aggregate failures preserve the last good snapshot.

PropagationRefreshWorkspaceService bridges refresh to the existing projection layer so a successful cycle projects its new snapshot while a failed cycle continues to expose the last good operating picture together with explicit source failure state.

Host/CI gate: refresh coordinator 112; projection 79; aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended awards 96; Awards service 64; persistence 92; Awards projection 114; target 77; catalog 115; award evaluator 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: {run_id}.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008G_PROPAGATION_REFRESH_COORDINATOR.md, PropagationRefreshModels.kt, PropagationRefreshCoordinator.kt, PropagationRefreshWorkspaceService.kt, and PropagationRefreshCoordinatorTests.kt.
"""
    history_path.write_text(history)
