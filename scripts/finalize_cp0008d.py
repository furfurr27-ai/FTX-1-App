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
(root / "VERSION").write_text("v29-psk-reporter-heard-path-adapter\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER

Parent durable checkpoint: CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008D is a GREEN host/CI + pinned-public-source checkpoint. It adds transport-independent normalization of PSK Reporter public XML reception reports into provider-neutral HeardPathObservation evidence without inferring geography from callsigns or promoting one-way receptions into QSOs.

CP-0008D proves:

- The official PSK Reporter public query endpoint, XML response semantics, field meanings and five-minute retrieval guidance are source-pinned.
- A 1,196-byte bounded fixture containing four exact recorded reception rows is pinned at SHA-256 fb41c07330c8d446dbd52eb4b35358950145b8a75fab76f225e69859b5752da7.
- Accepted reports require explicit valid sender and receiver Maidenhead locators; callsigns are never geocoded or used to invent geography.
- Provider frequency is retained in integer hertz and resolved through the existing amateur-band catalog; unmapped frequencies are rejected rather than guessed.
- Provider flowStartSeconds is retained as the observation UTC; caller retrieval UTC remains separate.
- Mode is required and retained; optional integer sNR is retained when valid and remains null when absent.
- Present informationSource values marked QSO/call-log, manual, or test are rejected from direct heard-path normalization.
- Missing/invalid per-report facts produce explicit rejection reasons while other complete reports in the same XML document remain usable.
- Exact duplicate normalized reports collapse deterministically and increment reportCount.
- XML DOCTYPE/external entity/external DTD/schema access is disabled.
- The parser accepts only the official HTTPS query endpoint, rejects callback/JSONP provenance, and refuses to persist appcontact email addresses in source URLs.
- Accepted evidence is MEASUREMENT / PROVISIONAL with PROVIDER_REPORTED confidence and an explicit statement that it is not a QSO.
- Production parsing code performs no HTTP/network access and contains no Android/Compose/map-SDK, FTX-1/PTT, logbook-write, or LoTW behavior.
- The superseded CP-0008C finalizer is manual-only so later propagation checkpoints cannot regress durable state.

Authoritative/direct research checked:

- PSK Reporter developer documentation for query parameters, XML retrieval, locator/frequency/time/mode/SNR/informationSource semantics and five-minute retrieval guidance.

Fixture provenance additionally checked:

- jasonhancock/go-pskreporter pinned commit b424d3bc83c52e424be7e6e32572ef652cecca4c, recorded response testdata/output.xml, used only for exact deterministic response metadata/row provenance.

Host/CI gates:

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

- research/propagation/CP-0008D_PSK_REPORTER_HEARD_PATH_ADAPTER.md
- research/propagation/PSK_REPORTER_FIXTURE.json
- research/propagation/PROPAGATION_SOURCES.tsv
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PskReporterHeardPathAdapter.kt
- SOFTWARE_TRACK.md
- CP-0008D finalization workflow run: {run_id}

### Evidence boundary

CP-0008D proves deterministic normalization of the pinned PSK Reporter XML response shape into explicit-grid one-way heard-path evidence and conservative per-report rejection. It does not claim current PSK Reporter uptime/activity, callsign/locator truth, QSO completion, live Android networking/cache/rendering, WSPRnet/GIRO integration, or HFcast/VOACAP prediction.

### Inherited verified ancestry

CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER, CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER, CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION, CP-0007C-US_STATE_GEOMETRY_PACK, CP-0007B-AWARD_GEOMETRY_PROVIDERS, CP-0007A-AWARD_MAP_PROJECTION, CP-0006G-EXTENDED_AWARD_CATALOG, CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008E — propagation evidence aggregation and offline cache service",
    readme,
    count=1,
)

readme = readme.replace(
    "Current Git source baseline: `CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER`",
    "Current Git source baseline: `CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER`",
    1,
)

next_section = """## Current exact next action

**CP-0008E — propagation evidence aggregation and offline cache service.**

1. Add a transport-neutral ingestion/orchestration boundary that accepts normalized NOAA solar/geomagnetic, GloTEC ionospheric and PSK Reporter heard-path records and produces deterministic PropagationSnapshot instances.
2. Define source-specific freshness defaults and snapshot capture rules without changing the provider evidence timestamps or fabricating missing generation times.
3. Add deterministic deduplication/merge behavior across repeated provider payloads while preserving source provenance and reportCount semantics.
4. Add an offline cache/repository implementation around the existing PropagationSnapshotStore contract with bounded history and deterministic restart/reload tests.
5. Keep observed heard paths, ionospheric context and modeled paths in separate evidence collections; aggregation must not create a propagation score by itself.
6. Keep transport/network scheduling outside the normalized source adapters and do not add Android/Compose/map-SDK dependencies to the core aggregation layer.
7. Do not integrate WSPRnet, GIRO, HFcast/VOACAP, real accounts/credentials, phone/radio/RF testing or manual hardware validation in this checkpoint.
8. CP-0003C remains DEFERRED; do not return to it until the owner explicitly says resume CP-0003C.

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

**CP-0008E — propagation evidence aggregation and offline cache service**

Build a transport-neutral orchestration layer that combines already-normalized NOAA solar/geomagnetic, GloTEC ionospheric and PSK Reporter heard-path evidence into deterministic PropagationSnapshot instances, with source-specific freshness rules, deduplication, bounded offline persistence/reload, and strict separation between observed context and modeled path predictions.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n`CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER`\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE

Required scope:

- transport-neutral aggregation of normalized NOAA, GloTEC and PSK Reporter evidence
- deterministic PropagationSnapshot construction
- source-specific freshness defaults without rewriting provider timestamps
- deterministic repeated-payload deduplication and merge behavior
- bounded offline PropagationSnapshotStore persistence/reload semantics
- preserve observed, ionospheric and modeled evidence as separate categories
- no automatic path score created merely by aggregation
- no Android/Compose/map SDK dependency
- no live network scheduling requirement in deterministic CI
- no WSPRnet/GIRO/HFcast/VOACAP integration
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
if "## CP-0008D — PSK Reporter public heard-path adapter" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0008D — PSK Reporter public heard-path adapter

Parent durable checkpoint: CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now normalizes the pinned PSK Reporter public XML retrieval shape into provider-neutral one-way HeardPathObservation evidence only when both endpoints carry explicit valid Maidenhead locators. Frequency/time/mode/SNR/source provenance are retained, incomplete reports are explicitly rejected rather than repaired from callsigns, and QSO/manual/test informationSource values are excluded when exposed. Exact duplicates collapse into reportCount rather than generating unstable evidence ids.

The deterministic fixture is 1,196 bytes with SHA-256 fb41c07330c8d446dbd52eb4b35358950145b8a75fab76f225e69859b5752da7 and contains four exact rows from the pinned public go-pskreporter recorded response. PSK Reporter developer documentation remains authoritative for API and field semantics.

Host/CI gate: PSK Reporter adapter 168; GloTEC adapter 87; NOAA adapter 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/propagation/CP-0008D_PSK_REPORTER_HEARD_PATH_ADAPTER.md, research/propagation/PSK_REPORTER_FIXTURE.json, research/propagation/PROPAGATION_SOURCES.tsv, and core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PskReporterHeardPathAdapter.kt.
"""
    history_path.write_text(history)
