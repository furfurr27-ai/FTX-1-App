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

(root / "VERSION").write_text("v34-concrete-public-https-transport\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT

Parent durable checkpoint: CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008I is a GREEN host/CI checkpoint. It provides a concrete JVM/Android-compatible HTTPS GET implementation behind the CP-0008H public transport contract while keeping required verification independent of external provider availability.

CP-0008I proves:

- HttpsUrlConnectionPublicPropagationTransport uses standard HttpsURLConnection behind the existing PublicPropagationTransport interface.
- Connection creation remains injectable through HttpsConnectionFactory, allowing deterministic host CI without live network access.
- HTTPS URL parsing requires a host and rejects user-info credentials and fragments.
- HTTP GET, redirect refusal, explicit connect/read timeouts, cache disablement, input-only operation, deterministic Accept ordering, UTF-8 and identity-encoding request headers are configured.
- Default connect/read timeouts are 10/15 seconds and validated configuration is bounded to 1..120 seconds.
- Declared Content-Length larger than request.maxResponseBytes fails before body read.
- Unknown-length/chunked bodies are streamed in bounded chunks and fail immediately after crossing request.maxResponseBytes.
- Accept-Encoding identity is requested and unexpected non-identity Content-Encoding fails closed.
- Response bytes are decoded with strict UTF-8 using malformed/unmappable REPORT behavior.
- HTTP >=400 reads the error stream while preserving the provider status for CP-0008H retry classification.
- Socket timeout, I/O and connection-open failures are retryable; invalid URL/credentials, oversize, unsupported encoding and invalid UTF-8 are non-retryable contract failures.
- PublicPropagationSourceAdapters preserves explicit PublicPropagationTransportException retryability.
- Input/error streams close through use and every opened connection reaches disconnect in finally.
- Concrete transport integration feeds the existing NOAA F10.7 parser without changing provider parser semantics.
- Production CP-0008I code has no Android framework/AndroidX, WorkManager, Compose, OkHttp/Retrofit, credentials/accounts, FTX-1/PTT/RF, QSO/logbook-write or LoTW behavior.
- Required CP-0008I CI makes no live HTTP/HTTPS request.
- The superseded CP-0008H finalizer is manual-only.

Host/CI gates:

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

- research/propagation/CP-0008I_CONCRETE_PUBLIC_HTTPS_TRANSPORT.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/HttpsUrlConnectionPublicPropagationTransport.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PublicPropagationSourceAdapters.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/HttpsUrlConnectionPublicPropagationTransportTests.kt
- SOFTWARE_TRACK.md
- CP-0008I finalization workflow run: {run_id}

### Evidence boundary

CP-0008I proves deterministic concrete HttpsURLConnection behavior and integration with the existing source/parser chain. It does not claim Android application packaging, Android network permission/background scheduling, provider uptime, TLS behavior on the actual S23 Ultra, cellular/Wi-Fi behavior, battery behavior, or real-world propagation accuracy.

### Inherited verified ancestry

CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS, CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR, CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION, CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE, CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER, CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER, CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER, CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION and all earlier verified checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)
readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008J — propagation runtime composition",
    readme,
    count=1,
)
readme = re.sub(
    r"- Current Git source baseline: .+",
    "- Current Git source baseline: CP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT",
    readme,
    count=1,
)

next_section = """## Current exact next action

**CP-0008J — propagation runtime composition.**

1. Add a platform-neutral runtime configuration/factory that composes the CP-0008I concrete HTTPS transport, the five CP-0008H public source definitions, snapshot/state storage, the CP-0008G refresh coordinator, and the CP-0008F workspace projection service.
2. Make operator callsign/PSK query inputs and source refresh policies explicit configuration; do not hard-code account/contact identity or infer station geography.
3. Provide safe default refresh cadences that respect GloTEC 10-minute and PSK Reporter five-minute minimum intervals.
4. Expose one manually-invoked refresh-and-project runtime entry point with deterministic source keys/state and last-good snapshot behavior inherited from prior checkpoints.
5. Prove composition with deterministic injected transport/storage CI and no external-provider dependency.
6. Keep WorkManager/background scheduling, Android app lifecycle/network permission, WSPRnet/WSPR.live, GIRO, HFcast/VOACAP, credentials/accounts, and phone/radio/RF/manual validation outside CP-0008J.
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

**CP-0008J — propagation runtime composition**

Compose the CP-0008I concrete HTTPS transport, CP-0008H source definitions, CP-0008G refresh coordinator/cache state, and CP-0008F projection service behind one platform-neutral manually-invoked runtime. Make callsign/query and cadence policy explicit, preserve minimum provider intervals, and prove the full composition with deterministic injected transport/storage CI. Keep Android background scheduling and new propagation providers outside this checkpoint.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(
    track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\nCP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT\n\n## Deferred but incomplete hardware/account work",
    "software-track parent",
)
track = replace_one(
    track,
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008J-PROPAGATION_RUNTIME_COMPOSITION

Required scope:

- platform-neutral runtime config/factory
- compose CP-0008I concrete HTTPS transport
- compose all five CP-0008H public source definitions
- compose snapshot/state storage, CP-0008G refresh coordinator and CP-0008F workspace projection service
- explicit operator callsign/PSK query settings; no inferred station geography
- explicit source cadence policies with safe provider-minimum defaults
- one manually-invoked refresh-and-project runtime entry point
- deterministic injected transport/storage host/CI tests
- no external-provider dependency
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
if "## CP-0008I — Concrete public HTTPS transport" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0008I — Concrete public HTTPS transport

Parent durable checkpoint: CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS.

FieldOps now has a concrete JVM/Android-compatible HttpsURLConnection implementation behind PublicPropagationTransport. The transport enforces HTTPS, explicit timeouts, redirect refusal, bounded streaming reads, identity encoding, strict UTF-8 decoding, exact response metadata, deterministic cleanup, and explicit retryability classes.

Connection creation is injectable, so required CI proves concrete connection behavior without live provider access. PublicPropagationSourceAdapters preserves transport retryability, and an integration test proves the concrete transport reaches the existing NOAA F10.7 parser unchanged.

Host/CI gate: concrete HTTPS 70; public transport 98; refresh coordinator 112; projection 79; aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended awards 96; Awards service 64; persistence 92; Awards projection 114; target 77; catalog 115; award evaluator 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: {run_id}.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008I_CONCRETE_PUBLIC_HTTPS_TRANSPORT.md, HttpsUrlConnectionPublicPropagationTransport.kt, PublicPropagationSourceAdapters.kt, and HttpsUrlConnectionPublicPropagationTransportTests.kt.
"""
    history_path.write_text(history)
