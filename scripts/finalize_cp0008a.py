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
(root / "VERSION").write_text("v26-propagation-intelligence-foundation\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION

Parent durable checkpoint: CP-0007C-US_STATE_GEOMETRY_PACK.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008A is a GREEN host/CI + authoritative-source-research checkpoint. It establishes the provider-neutral propagation domain, freshness/provenance/confidence model, observed-vs-modeled separation, explainable path assessment, and offline snapshot/cache boundary using deterministic synthetic evidence only.

CP-0008A proves:

- Propagation sources retain source id, provider name, source class, source/product version, retrieval UTC, optional HTTPS source URL, and optional HTTPS terms URL.
- Source classes distinguish MEASUREMENT, DERIVED_PRODUCT, MODEL, FORECAST, and SYNTHETIC_FIXTURE.
- Synthetic fixtures cannot masquerade as live providers by carrying a live source URL.
- Every normalized evidence record carries explicit confidence basis/explanation and one or more quality flags.
- Geographic evidence requires explicit coordinates or a valid 4/6/8-character Maidenhead locator; callsign-only geography is not supported.
- Freshness is explicit and configurable as FRESH, AGING, STALE, or FUTURE_DATED rather than being inferred silently from display age.
- Solar/geomagnetic context normalizes F10.7, planetary Kp/Ap, sunspot number, and X-ray flux without pretending those values are observed RF paths.
- NOAA's official Kp=5 / G1 threshold is represented only as an explainable geomagnetic-storm caution; it does not override direct path evidence.
- Provider-neutral ionospheric map products represent foF2, MUF, and hmF2-style samples with provenance, quality, confidence, coverage, observation/generation times, and explicit MUF reference distance.
- Heard/spot path observations retain explicit transmitter/receiver locations, band, exact frequency, mode, optional SNR, report count, provenance, quality, and confidence while remaining separate from QSO/LoTW state.
- Modeled path estimates remain distinct from heard observations and retain explicit path endpoints, MUF/LUF limits, model input summary, provenance, quality, and confidence.
- Explainable path assessment returns GOOD, MARGINAL, POOR, or UNKNOWN together with structured reasons and exact evidence ids.
- Fresh direct heard-path evidence can produce GOOD; aging direct evidence is MARGINAL; model-only within explicit limits is MARGINAL; above-MUF/below-LUF is POOR; insufficient current path evidence is UNKNOWN.
- Solar/geomagnetic and ionospheric map context can enrich explanations but cannot silently become observed path proof.
- Stale and future-dated evidence is retained for inspection but ignored for current path usability decisions.
- Propagation snapshots preserve normalized evidence and source ids while enforcing unique evidence ids and snapshot/retrieval ordering.
- PropagationSnapshotStore defines save/latest/id/as-of/history operations for offline-capable persistence adapters; deterministic in-memory behavior is host-proven.
- Observed/heard stations are not silently promoted to QSOs.
- Production propagation code contains no Android/Compose/map-SDK types, network clients, provider account credentials, FTX-1 hardware control, or PTT behavior.

Authoritative research checked:

- NOAA/SWPC K-index documentation.
- NOAA Space Weather Scales.
- NOAA/SWPC public products directory.
- GIRO/LGDC ionospheric/MUF products and Rules of the Road.
- GIRO/GAMBIT access information.

GIRO licensing/access constraints are recorded explicitly; CP-0008A bundles or fetches no GIRO dataset and implements no credentialed provider adapter.

Host/CI gates:

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

- research/propagation/CP-0008A_PROPAGATION_FOUNDATION.md
- research/propagation/PROPAGATION_SOURCES.tsv
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationDomain.kt
- SOFTWARE_TRACK.md
- CP-0008A finalization workflow run: {run_id}

### Evidence boundary

CP-0008A proves the provider-neutral propagation domain and synthetic host/CI behavior. It does not claim live NOAA/GIRO/PSK Reporter/WSPRnet integration, HFcast/VOACAP predictions, a scientifically calibrated universal path score, Android map rendering, on-device network caching, or current real-world propagation conditions.

### Inherited verified ancestry

CP-0007C-US_STATE_GEOMETRY_PACK, CP-0007B-AWARD_GEOMETRY_PROVIDERS, CP-0007A-AWARD_MAP_PROJECTION, CP-0006G-EXTENDED_AWARD_CATALOG, CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008B — NOAA SWPC public propagation source adapter",
    readme,
    count=1,
)

readme = readme.replace(
    "Current Git source baseline: " + chr(96) + "CP-0007C-US_STATE_GEOMETRY_PACK" + chr(96),
    "Current Git source baseline: " + chr(96) + "CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION" + chr(96),
    1,
)

next_section = """## Current exact next action

**CP-0008B — NOAA SWPC public propagation source adapter.**

1. Pin the exact official no-credential NOAA/SWPC endpoints and capture representative response schemas for planetary Kp and F10.7 solar flux.
2. Record source URL, retrieval date, schema/version observations, units, cadence/validity semantics, and fixture SHA-256 values.
3. Implement a provider adapter that parses captured NOAA payloads into the CP-0008A provider-neutral solar/geomagnetic domain.
4. Keep observed, estimated, and forecast NOAA records distinct; do not silently collapse forecast values into observations.
5. Normalize missing/sentinel fields fail-closed and retain provider timestamps/provenance rather than substituting device time.
6. Use pinned deterministic captured fixtures for parser CI; a schema-freshness check may fetch official public endpoints but must not make tests dependent on current space-weather values.
7. Do not derive a path-quality score directly from Kp or F10.7; these remain contextual inputs to the explainable assessment layer.
8. Keep network transport outside the provider-neutral core parser boundary so offline cached snapshots remain supported.
9. Keep GIRO, PSK Reporter, WSPRnet, HFcast/VOACAP, and other providers outside this checkpoint.
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

**CP-0008B — NOAA SWPC public propagation source adapter**

Pin the exact official no-credential NOAA/SWPC planetary-Kp and F10.7 endpoints, capture/version/hash representative response fixtures, and implement a transport-independent parser/normalizer into the CP-0008A solar/geomagnetic domain. Preserve observed/estimated/forecast distinctions and provider timestamps; keep current-value-dependent assertions, credentials, other providers, and Android rendering out of this checkpoint.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + chr(96) + "CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION" + chr(96) + "\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER

Required scope:

- exact official no-credential NOAA/SWPC endpoint pins for planetary Kp and F10.7 solar flux
- captured representative response fixtures with retrieval date and SHA-256
- documented response schema, units, timestamp/cadence semantics, and any sentinel/missing-value behavior
- transport-independent NOAA parser/adapter into CP-0008A SolarGeomagneticObservation
- explicit observed vs estimated vs forecast source/provenance distinction
- provider timestamps retained; no replacement with device/current time
- fail-closed malformed/missing required fields
- deterministic parser tests against pinned fixtures
- optional live schema-freshness check must not assert changing current values
- no universal path score derived directly from Kp/F10.7
- no GIRO/PSK Reporter/WSPRnet/HFcast/VOACAP integration in this checkpoint
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
if "## CP-0008A — Propagation intelligence domain and source-normalization foundation" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0008A — Propagation intelligence domain and source-normalization foundation

Parent durable checkpoint: CP-0007C-US_STATE_GEOMETRY_PACK.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a provider-neutral propagation intelligence core separating direct heard/spot RF observations, solar/geomagnetic context, ionospheric map products, and modeled path estimates. Evidence retains source/version/retrieval/observation metadata, configurable freshness, confidence basis/explanation, quality flags, and explicit geography. Callsign-only geography is rejected. Path usability is explainable and conservative, stale/future evidence is ignored for current decisions, and offline snapshot/cache interfaces are defined without Android or network dependencies.

Official NOAA/SWPC and GIRO/LGDC documentation was checked for Kp storm semantics, F10.7 context, ionospheric/MUF concepts, MUF reference-distance semantics, and GIRO licensing/access constraints. All host evidence remains deterministic synthetic data; no live provider/account data is bundled.

Host/CI gate: propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/propagation/CP-0008A_PROPAGATION_FOUNDATION.md and research/propagation/PROPAGATION_SOURCES.tsv.
"""
    history_path.write_text(history)
