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
(root / "VERSION").write_text("v27-noaa-swpc-propagation-adapter\n")

readme_path = root / "README.md"
readme = readme_path.read_text()

baseline = f"""## Verified durable baseline

**Latest verified checkpoint:** CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER

Parent durable checkpoint: CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION.

CP-0003C and CP-0004A/B/C remain explicitly deferred hardware/account checkpoints and are not implied complete by this software checkpoint.

CP-0008B is a GREEN host/CI + pinned-public-source checkpoint. It adds a transport-independent NOAA/SWPC adapter for planetary Kp and observed F10.7 context while preserving the CP-0008A provider-neutral propagation model.

CP-0008B proves:

- Exact public no-credential NOAA/SWPC endpoints are pinned for planetary Kp history, the Kp observed/estimated/predicted feed, and the F10.7 summary.
- The 2026 NWS Service Change Notice 26-21 JSON format boundary is recorded as `swpc-json-post-scn26-21-v1`.
- Three deterministic representative fixtures are committed with byte counts and SHA-256 hashes.
- Historical Kp `time_tag`, `Kp`, `a_running`, and `station_count` fields are parsed fail-closed.
- `a_running` is retained as NOAA provider metadata and is not silently promoted to planetary Ap.
- The Kp feed's provider states `observed`, `estimated`, and `predicted` remain distinct through normalization.
- Predicted Kp records use FORECAST provenance rather than masquerading as observations.
- Estimated/predicted quality metadata is explicit and provider timestamps are retained separately from retrieval UTC.
- F10.7 is normalized in solar flux units from the NOAA 10.7 cm / 2800 MHz summary without becoming path evidence.
- Old quoted-number schema rows, unknown statuses, missing fields, extra fields, malformed timestamps, duplicate keys, and invalid numeric domains fail closed.
- A live no-credential schema guard verifies only structural invariants and never asserts changing current space-weather values.
- Production NOAA parsing code contains no network transport, Android/Compose/map-SDK types, QSO/LoTW state, FTX-1 hardware control, PTT behavior, or direct path-usability scoring.

Authoritative research checked:

- NOAA/SWPC public product directory and live public JSON products.
- NWS Service Change Notice 26-21 for the March 2026 JSON object/numeric-value migration.
- NOAA F10.7 technical documentation for 10.7 cm / 2800 MHz solar flux units and semantics.

Host/CI gates:

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

- research/propagation/CP-0008B_NOAA_SWPC_ADAPTER.md
- research/propagation/NOAA_SWPC_FIXTURES.json
- research/propagation/PROPAGATION_SOURCES.tsv
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/NoaaSwpcPropagationAdapter.kt
- SOFTWARE_TRACK.md
- CP-0008B finalization workflow run: {run_id}

### Evidence boundary

CP-0008B proves deterministic normalization of the pinned post-SCN NOAA schemas and a successful schema-only live check at verification time. It does not claim NOAA uptime, current propagation conditions, a scientifically calibrated universal path score, GIRO/PSK Reporter/WSPRnet/HFcast/VOACAP integration, Android network caching, or map rendering.

### Inherited verified ancestry

CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION, CP-0007C-US_STATE_GEOMETRY_PACK, CP-0007B-AWARD_GEOMETRY_PROVIDERS, CP-0007A-AWARD_MAP_PROJECTION, CP-0006G-EXTENDED_AWARD_CATALOG, CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

"""

readme = replace_one(
    readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals",
    "verified durable baseline",
)

readme = re.sub(
    r"\*\*Active software track:\*\* .*",
    "**Active software track:** CP-0008C — NOAA SWPC GloTEC public ionospheric map adapter",
    readme,
    count=1,
)

readme = readme.replace(
    "Current Git source baseline: `CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION`",
    "Current Git source baseline: `CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER`",
    1,
)

next_section = """## Current exact next action

**CP-0008C — NOAA SWPC GloTEC public ionospheric map adapter.**

1. Pin the exact official no-credential NOAA/SWPC GloTEC GeoJSON endpoint/schema and capture a bounded representative fixture with SHA-256.
2. Extend the provider-neutral ionospheric metric model only as required to represent vertical TEC in TECU; do not reinterpret TEC as foF2 or MUF.
3. Parse explicit provider coordinates, observation/generation/validity timestamps, quality metadata, and source provenance into the CP-0008A ionospheric-product boundary.
4. Keep the parser transport-independent and deterministic; use captured fixtures for the required CI gate.
5. A live schema-freshness check may inspect the public endpoint structure but must not make tests depend on today's TEC values.
6. Do not derive MUF, band/path usability, or a heat-map score directly from TEC without a separately justified and tested scientific model.
7. Keep GIRO, PSK Reporter, WSPRnet, HFcast/VOACAP, Android/Compose/map-SDK rendering, and account/credential providers outside this checkpoint.
8. CP-0003C remains DEFERRED; do not return to it until the owner explicitly says resume CP-0003C.
9. Skip any checkpoint requiring the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation; continue with the next GitHub/CI-only checkpoint.

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

**CP-0008C — NOAA SWPC GloTEC public ionospheric map adapter**

Pin the exact official no-credential GloTEC GeoJSON schema, capture/hash a bounded representative fixture, extend the provider-neutral ionospheric metric model only as required for TEC, and normalize explicit coordinates/timestamps/provenance without deriving MUF or path usability directly from TEC.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = re.sub(
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n`CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER`\n\n## Deferred but incomplete hardware/account work",
    track,
    count=1,
    flags=re.S,
)
track = re.sub(
    r"## Active software checkpoint\n\n.*?\n\nRequired scope:\n.*?\n## Resume rule",
    """## Active software checkpoint

CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER

Required scope:

- exact official no-credential NOAA/SWPC GloTEC GeoJSON endpoint/schema pin
- bounded captured fixture with byte count and SHA-256
- provider-neutral ionospheric metric extension only as required for TEC/TECU
- explicit coordinates and provider observation/generation/validity timestamps
- provenance/confidence/quality retained without callsign-derived geography
- transport-independent parser and deterministic fixture CI
- optional live schema-freshness guard must not assert changing current TEC values
- no derived MUF/path-usability score directly from TEC
- no GIRO/PSK Reporter/WSPRnet/HFcast/VOACAP integration
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
if "## CP-0008B — NOAA SWPC public propagation source adapter" not in history:
    history = history.rstrip() + "\n\n" + f"""## CP-0008B — NOAA SWPC public propagation source adapter

Parent durable checkpoint: CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a transport-independent NOAA/SWPC solar/geomagnetic adapter pinned to the post-SCN 26-21 JSON object schema. Planetary Kp history, provider-labeled observed/estimated/predicted Kp records, and F10.7 summary observations normalize into the CP-0008A evidence domain with provider timestamps, retrieval UTC, explicit provenance, confidence basis/explanation, and quality metadata. Forecast records remain FORECAST; quoted legacy numeric fields and schema drift fail closed.

Three representative NOAA fixtures captured on 2026-10-07 are pinned by SHA-256. A separate live schema-only check verifies the public endpoints without asserting current values.

Host/CI gate: NOAA adapter 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: {run_id}.

Evidence: research/propagation/CP-0008B_NOAA_SWPC_ADAPTER.md, research/propagation/NOAA_SWPC_FIXTURES.json, and research/propagation/PROPAGATION_SOURCES.tsv.
"""
    history_path.write_text(history)
