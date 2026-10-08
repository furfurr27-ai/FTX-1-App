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

(root / "VERSION").write_text("v35-propagation-runtime-composition\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008J-PROPAGATION_RUNTIME_COMPOSITION

Parent durable checkpoint: CP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008J is a GREEN host/CI checkpoint. It composes the verified propagation transport, five public sources, refresh/cache state and workspace projection into one platform-neutral manually-invoked runtime.

CP-0008J proves:

- PropagationRuntimeConfig requires an explicit operator callsign and carries an explicit PSK Reporter query, refresh policies and HTTPS transport configuration.
- Runtime configuration contains no inferred station latitude, longitude or Maidenhead grid.
- Safe default cadences are observed Kp 15 minutes, Kp forecast 15 minutes, F10.7 60 minutes, GloTEC 10 minutes and PSK Reporter 5 minutes.
- Runtime configuration rejects GloTEC cadence below the pinned 10-minute product cadence and PSK Reporter cadence below the documented five-minute retrieval minimum.
- PropagationRuntimeFactory composes the CP-0008I concrete HTTPS transport by default and all five CP-0008H public source definitions.
- The runtime composes snapshot storage, refresh-state storage, CP-0008G PropagationSourceRefreshCoordinator and CP-0008F PropagationWorkspaceProjectionService.
- Transport, snapshot store, refresh-state store, aggregator and assessment engine remain injectable for deterministic testing and later application integration.
- PropagationRuntime.refreshAndProject is the single manually-invoked refresh/project entry point and never reads wall-clock time internally.
- Full fake-transport integration produces canonical NOAA Kp/F10.7 evidence, GloTEC ionospheric context and PSK Reporter heard paths in one saved/projected snapshot.
- At four minutes all sources remain cadence-skipped; at five minutes only PSK Reporter refreshes; at ten minutes GloTEC and PSK Reporter refresh.
- If the only eligible source fails, no new snapshot is written and the runtime continues projecting the last good snapshot while explicit source failure state remains inspectable.
- Caller-provided stores and projection filters remain authoritative.
- Production CP-0008J code contains no Android framework/AndroidX, WorkManager, Android lifecycle/network-permission behavior, credentials/accounts, FTX-1/PTT/RF, QSO/logbook-write or LoTW behavior.
- The superseded CP-0008I finalizer is manual-only.

Host/CI gates:

- CP-0008J propagation runtime composition: **67/67 PASS**.
- CP-0008I concrete public HTTPS transport: **70/70 PASS**.
- CP-0008H public propagation transport adapters: **98/98 PASS**.
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

- research/propagation/CP-0008J_PROPAGATION_RUNTIME_COMPOSITION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationRuntime.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PropagationRuntimeTests.kt
- SOFTWARE_TRACK.md
- CP-0008J finalization workflow run: {run_id}

### Evidence boundary

CP-0008J proves platform-neutral runtime composition and manual invocation using deterministic injected transport/storage. It does not claim Android app lifecycle/background scheduling, process-restart persistence for the default in-memory stores, provider uptime, WSPRnet/WSPR.live, GIRO, HFcast/VOACAP or real phone/radio/RF behavior.

### Inherited verified ancestry

CP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT, CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS, CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR, CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION, CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE and all earlier verified checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)
readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008K — propagation refresh-state persistence",
    readme,
    count=1,
)
readme = re.sub(
    r"- Current Git source baseline: .+",
    "- Current Git source baseline: CP-0008J-PROPAGATION_RUNTIME_COMPOSITION",
    readme,
    count=1,
)

next_section = """## Current exact next action

**CP-0008K — propagation refresh-state persistence.**

1. Add a platform-neutral file-backed PropagationRefreshStateStore with deterministic versioned serialization and atomic replace semantics.
2. Preserve per-source role, last attempt, last success, consecutive failures, next eligible refresh, failure message and retryability across process/runtime recreation.
3. Fail closed on corrupt, duplicate, unknown-version or role-mismatched persisted state while preserving the last valid state file when a write fails.
4. Integrate persisted refresh state with PropagationRuntimeFactory through explicit store injection; do not change provider cadence, parser, cache or projection semantics.
5. Prove restart/corruption/atomic-write behavior with deterministic filesystem CI.
6. Keep Android WorkManager/background scheduling, Android lifecycle/network permission, credentials/accounts, new propagation providers and phone/radio/RF/manual validation outside CP-0008K.
7. CP-0003C remains DEFERRED until the owner explicitly says resume CP-0003C.

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

**CP-0008K — propagation refresh-state persistence**

Add a platform-neutral file-backed PropagationRefreshStateStore with deterministic versioned serialization and atomic replacement. Preserve cadence/failure state across runtime recreation and prove restart/corruption/write-failure behavior with deterministic filesystem CI. Keep Android background scheduling and new propagation providers outside this checkpoint.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(
    track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\nCP-0008J-PROPAGATION_RUNTIME_COMPOSITION\n\n## Deferred but incomplete hardware/account work",
    "software-track parent",
)
track = replace_one(
    track,
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008K-PROPAGATION_REFRESH_STATE_PERSISTENCE

Required scope:

- platform-neutral file-backed PropagationRefreshStateStore
- deterministic versioned serialization
- atomic replace semantics
- preserve role/attempt/success/failure/next-eligible state across runtime recreation
- fail closed on corrupt, duplicate, unknown-version or role-mismatched state
- preserve last valid file on failed write
- explicit integration through PropagationRuntimeFactory store injection
- deterministic filesystem host/CI tests
- no WorkManager/background scheduling or Android lifecycle/network-permission work
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
if "## CP-0008J — Propagation runtime composition" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0008J — Propagation runtime composition

Parent durable checkpoint: CP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT.

FieldOps now has a platform-neutral PropagationRuntime factory/config layer composing the concrete HTTPS transport, all five verified public sources, snapshot/state storage, refresh coordination and workspace projection behind one manual refresh-and-project entry point.

The runtime requires explicit operator callsign/query configuration, uses safe provider-respecting default cadences, does not infer station geography, and retains prior checkpoint behavior for canonical Kp selection, retry/backoff, last-good snapshots and projection filtering.

Host/CI gate: runtime 67; concrete HTTPS 70; public transport 98; refresh 112; projection 79; aggregation 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation 154; production state pack 363; geometry 102; map 96; extended 96; service 64; persistence 92; awards projection 114; target 77; catalog 115; award 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: {run_id}.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008J_PROPAGATION_RUNTIME_COMPOSITION.md, PropagationRuntime.kt and PropagationRuntimeTests.kt.
"""
    history_path.write_text(history)
