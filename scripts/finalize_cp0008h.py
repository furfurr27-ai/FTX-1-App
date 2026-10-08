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

(root / "VERSION").write_text("v33-public-propagation-transport-adapters\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS

Parent durable checkpoint: CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008H is a GREEN host/CI checkpoint. It connects the verified NOAA SWPC, GloTEC and PSK Reporter parsers to the CP-0008G refresh coordinator through a bounded platform-neutral public request/response contract.

CP-0008H proves:

- PublicPropagationTransport exposes a GET request/response boundary without binding core propagation code to Android, WorkManager, OkHttp, Retrofit, HttpURLConnection, java.net transport or any other concrete client.
- Requests require HTTPS, reject fragments/control-line separators, declare accepted media types and carry explicit maximum response bytes.
- Responses preserve requested URL, effective URL, status, optional media type and decoded body.
- Response validation fails closed on request-provenance mismatch, unexpected redirects, non-200 status, oversized bodies and unexpected media types.
- Retry classification treats 408/425/429/5xx and transport exceptions as retryable while parser/schema/provenance violations remain non-rapid-retry failures.
- NOAA observed Kp, Kp forecast and F10.7 source factories reuse the existing verified CP-0008B parsers and exact official endpoints.
- GloTEC refresh performs a two-stage index then artifact retrieval using exact official NOAA endpoints.
- NoaaSwpcGlotecIndexSelector requires valid JSON, accepts only canonical glotec_icao_YYYYMMDDTHHMMSSZ.geojson filenames or exact official full URLs, rejects malformed/non-official candidate references and selects the greatest canonical timestamp.
- GloTEC source cadence may not poll faster than the pinned 10-minute product cadence.
- PskReporterPublicQuery builds deterministic sender/receiver/either callsign XML queries, enforces a maximum 24-hour lookback, supports bounded report limits/mode/frequency filters, and never adds appcontact or callback.
- PSK Reporter source cadence may not violate the documented five-minute minimum retrieval guidance.
- Deterministic fake-transport integration proves five public source definitions feed CP-0008G and still preserve canonical NOAA observed-Kp selection.
- Required CI never depends on live provider availability.
- The superseded CP-0008G finalizer is manual-only.

Host/CI gates:

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

- research/propagation/CP-0008H_PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PublicPropagationTransport.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PublicPropagationSourceAdapters.kt
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/NoaaSwpcGlotecIndexSelector.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/PublicPropagationTransportAdapterTests.kt
- SOFTWARE_TRACK.md
- CP-0008H finalization workflow run: {run_id}

### Evidence boundary

CP-0008H proves transport-independent endpoint/query/response semantics using deterministic fake transport plus pinned provider fixtures. It does not claim a concrete HTTP client, TLS/runtime networking, Android background execution, provider uptime, battery behavior, or real-world propagation accuracy.

### Inherited verified ancestry

CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR, CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION, CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE, CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER, CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER, CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER, CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION and all earlier verified checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)
readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008I — concrete public HTTPS transport implementation",
    readme,
    count=1,
)
readme = re.sub(
    r"- Current Git source baseline: .+",
    "- Current Git source baseline: CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS",
    readme,
    count=1,
)

next_section = """## Current exact next action

**CP-0008I — concrete public HTTPS transport implementation.**

1. Implement a concrete JVM/Android-compatible HTTPS GET transport behind the CP-0008H PublicPropagationTransport interface.
2. Enforce connect/read timeouts, bounded streaming reads using request.maxResponseBytes, HTTPS-only requests, redirect refusal, deterministic UTF-8 decoding, status/content-type/effective-URL reporting, and safe connection cleanup.
3. Prove the implementation with a deterministic local HTTP/HTTPS-compatible test harness or injectable connection factory; required CI must not depend on external provider uptime.
4. Integrate the concrete transport with the existing CP-0008H source definitions without changing provider parser semantics or CP-0008G refresh/cache behavior.
5. Keep WorkManager/background scheduling, WSPRnet/WSPR.live, GIRO, HFcast/VOACAP, real credentials/accounts, phone/radio/RF/manual validation outside CP-0008I.
6. CP-0003C remains DEFERRED until the owner explicitly says resume CP-0003C.

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

**CP-0008I — concrete public HTTPS transport implementation**

Implement a concrete JVM/Android-compatible HTTPS GET transport behind the CP-0008H PublicPropagationTransport interface. Enforce timeouts, bounded streaming reads, redirect refusal, exact response metadata, UTF-8 decoding, and cleanup with deterministic CI. Keep WorkManager/background scheduling and new propagation providers outside this checkpoint.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(
    track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\nCP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS\n\n## Deferred but incomplete hardware/account work",
    "software-track parent",
)
track = replace_one(
    track,
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT

Required scope:

- concrete JVM/Android-compatible HTTPS GET transport behind PublicPropagationTransport
- explicit connect/read timeouts
- bounded streaming reads using request.maxResponseBytes
- HTTPS-only request enforcement
- redirect refusal
- deterministic UTF-8 decoding
- response status/content-type/effective-URL reporting
- safe stream/connection cleanup
- deterministic CI without external-provider dependency
- integrate with existing CP-0008H source definitions without parser-policy changes
- no WorkManager/background scheduling
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
if "## CP-0008H — Public propagation transport adapters" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0008H — Public propagation transport adapters

Parent durable checkpoint: CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR.

FieldOps now has a platform-neutral public request/response boundary connecting the existing NOAA SWPC, GloTEC and PSK Reporter parsers to the refresh coordinator. Requests are HTTPS-only, bounded by declared response size and media type, and responses retain request/effective URL, status, media type and body.

GloTEC uses deterministic two-stage index/artifact retrieval. The index selector requires valid JSON and only accepts canonical official NOAA artifact references. PSK Reporter queries are deterministic, enforce the documented five-minute minimum retrieval cadence, and never add appcontact or callback parameters.

Deterministic fake-transport integration proves all five public source definitions feed CP-0008G while canonical observed-Kp selection remains intact.

Host/CI gate: public transport 98; refresh coordinator 112; projection 79; aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended awards 96; Awards service 64; persistence 92; Awards projection 114; target 77; catalog 115; award evaluator 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: {run_id}.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008H_PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS.md, PublicPropagationTransport.kt, PublicPropagationSourceAdapters.kt, NoaaSwpcGlotecIndexSelector.kt, and PublicPropagationTransportAdapterTests.kt.
"""
    history_path.write_text(history)
