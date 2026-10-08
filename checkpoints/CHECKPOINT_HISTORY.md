# Checkpoint history

## CP-0000 — v5 locked roadmap baseline

Artifact: `FTX1_FieldOps_v5_roadmap_locked.zip`
SHA-256: `2f8d524f31c5681a21d3c42ec0e4f34ed2de58d52f3e0a9c4dfa850ae1cbcb62`

This is the immutable pre-survey baseline created after the one-stop-shop requirements were locked. It is stored outside this project directory in the conversation artifact area and is the recovery point immediately before the GitHub-wide reference survey/checkpoint-system work.

Subsequent checkpoints are created by `scripts/create_checkpoint.py` and verified by `scripts/verify_checkpoint.py`.

## CP-0002A — JS8 native RX

Parent: `CP-0001-GITHUB_SURVEY`

FieldOps JS8 RX now uses the pinned JS8Call Android native-engine boundary on the continuous 12 kHz stream. The deterministic host gate passes 19 assertions, the production binding compiles against the pinned upstream API surface, and the exact successful upstream ARM64 artifact was checked for the required JNI exports. No JS8 TX behavior is included.

Evidence: `research/js8/CP-0002A_JS8_RX_INTEGRATION.md`.

## CP-0002B — JS8 native TX

Parent: `CP-0002A-JS8_NATIVE_RX`

FieldOps now captures the pinned JS8 native TX audio tap while retaining exclusive CAT/PTT/USB-audio ownership. Native modulation is gated until FieldOps acquires the JS8 radio owner and completes PTT lead, then callback PCM is statefully adapted to the 48 kHz playback domain. Normal completion and all tested error/cancel paths collapse to RX-safe state.

Host/CI gate: core 42,062; pipeline 56; LoTW 19; JS8 RX 19; JS8 TX 39 assertions, all PASS.

Finalization workflow run: `37298037260`.

Evidence: `research/js8/CP-0002B_JS8_TX_INTEGRATION.md`.

## CP-0002C — WSPR native RX

Parent: `CP-0002B-JS8_NATIVE_TX`

FieldOps now receives WSPR through the exact pinned rtlsdr-wsprd decoder. The 12 kHz real receive window is mixed and FIR-decimated to the decoder's 375 sps complex-I/Q domain, and a pinned native encoder fixture is recovered end-to-end as `K1JT FN20 20`. Exact vendored upstream Git blobs are verified in CI. WSPR transmit remains unavailable until CP-0002D.

Host/CI gate: core 42,062; pipeline 56; LoTW 19; WSPR RX 22; JS8 RX 19; JS8 TX 39 assertions, all PASS.

Finalization workflow run: `37305788342`.

Evidence: `research/wspr/CP-0002C_WSPR_RX_INTEGRATION.md`.

## CP-0002D — WSPR native TX

Parent: `CP-0002C-WSPR_NATIVE_RX`

FieldOps now uses the pinned upstream WSPR channel-symbol encoder as the production message-codec boundary and synthesizes the full continuous-phase 12 kHz 4-FSK waveform. WSPR transmission is chunked through the common `Ftx1RadioSession` under `Owner.WSPR`; no WSPR modem code owns CAT/PTT or USB audio directly. Competing-owner, cancel, audio-failure and malformed-input paths are proven fail-closed.

Host/CI gate: core 42,062; pipeline 56; LoTW 19; WSPR RX 21; WSPR TX 56; JS8 RX 19; JS8 TX 39 assertions, all PASS.

Finalization workflow run: `37314097215`.

Evidence: `research/wspr/CP-0002D_WSPR_TX_INTEGRATION.md`.

## CP-0002E — Native-mode regression

Parent: `CP-0002D-WSPR_NATIVE_TX`

Independent host/CI gates now cover FT8, FT4, FT2, JS8, WSPR and APRS, followed by a shared composition test for the 48 kHz / 12 kHz audio split, continuous-vs-windowed timing and the common radio TX owner. This checkpoint adds regression evidence rather than a new operating mode.

Host/CI gate: core 42,062; pipeline 56; LoTW 19; FT8 20; FT4 20; FT2 20; JS8 RX 19; JS8 TX 39; WSPR RX 21; WSPR TX 56; APRS 2,048; composition 130 assertions, all PASS.

The public repository still omits the extracted ARM64 FT8AF native binary, so FT-family host execution uses deterministic ABI fixtures after separately compiling the production ABI declarations/adapter. Actual phone/radio proof remains hardware-gated.

Finalization workflow run: `37316846958`.

Evidence: `research/CP-0002E_NATIVE_MODE_REGRESSION.md`.

## CP-0003A — TrustedQSL signer bridge

Parent: `CP-0002E-NATIVE_MODE_REGRESSION`

Official TrustedQSL 2.8.6 is pinned by SourceForge SHA-256 and the production JNI bridge compiles directly against its exact headers. FieldOps now has a fail-closed transactional signing boundary for PKCS#12 import, explicit station-location/callsign/DXCC validation, official ADIF/GABBI conversion calls, compressed TQ8 output, and explicit TrustedQSL duplicate-state commit/rollback.

CP-0003A intentionally does not connect that transaction to the network upload path yet. Runtime CI uses an exact-API deterministic fixture; real TrustedQSL Android ARM64 linking, real certificate cryptography, and LoTW acceptance remain unverified.

Host/CI gate: signer 43; inherited core 42,062; pipeline 56; LoTW 19 assertions, all PASS.

Finalization workflow run: `37322373366`.

Evidence: `research/tqsl/CP-0003A_SOURCE_PIN.md` and `research/tqsl/CP-0003A_SIGNER_BRIDGE.md`.

## CP-0003B — Transaction-safe LoTW upload

Parent: `CP-0003A-TRUSTEDQSL_SIGNER`

FieldOps now preserves the TrustedQSL duplicate-database transaction across the entire upload/verification flow. HTTP upload acceptance is SUBMITTED only; every QSO in the batch must appear in the LoTW accepted-QSO report before TrustedQSL state commits and local QSOs become ACCEPTED.

Signer/network/non-2xx/report-verification failures roll back. Endpoint rejection rolls back and remains visible without automatic retry. SSB, CW and digital QSOs share the same mode-neutral queue/state machine; automatic manual-logger enqueue remains CP-0005B.

Host/CI gate: transaction 53; signer 43; core 42,062; pipeline 56; LoTW 19 assertions, all PASS.

Finalization workflow run: `37329432853`.

Evidence: `research/tqsl/CP-0003B_TRANSACTION_SAFE_UPLOAD.md`.

## CP-0005A — Universal QSO model + fast logger

Parent durable checkpoint: `CP-0003B-LOTW_TRANSACTION_SAFE`.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete.

The existing LoTW-compatible `QsoRecord` is now the universal local QSO model. It preserves exact ADIF mode/submode, physical radio mode, exact frequency, band, UTC start/end, reports, station/remote location, station profile and operating session. `FastQsoLogger` adds manual SSB/CW logging and completed-contact digital auto-log adapters into the same authoritative repository contract.

ADIF export preserves the new exact identity/frequency fields. Automatic logger-save -> LoTW enqueue is intentionally left for CP-0005B.

Host/CI gate: logger 78; LoTW transaction 53; core 42,062; pipeline 56; LoTW 19 assertions, all PASS.

Finalization workflow run: `37444996013`.

Evidence: `research/logbook/CP-0005A_UNIVERSAL_QSO_LOGGER.md`.

## CP-0005B — Manual/digital QSO LoTW queue

Parent durable checkpoint: CP-0005A-UNIVERSAL_QSO_LOGGER.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete.

FastQsoLogger now has an explicit LoTW auto-queue policy. When enabled, successful local SSB/CW and eligible completed-digital saves enter the same local LotwUploadQueue; the authoritative local save always occurs first. Successful enqueue mirrors QUEUED back to the stored QSO, while queue failure leaves the already-saved QSO NOT_UPLOADED and reports the queue failure separately.

LotwUploadQueue is idempotent by immutable local QSO id, preserves retry state on repeated enqueue, and rejects conflicting reuse of an id/profile. The logger path remains network-free; real automatic LoTW upload is still gated by CP-0003C.

Host/CI gate: queue 47; universal logger 78; LoTW transaction 53; core 42,062; pipeline 56; LoTW 19 assertions, all PASS.

Finalization workflow run: 37451754863.

Evidence: research/logbook/CP-0005B_MANUAL_QSO_LOTW_QUEUE.md.

## CP-0006A — Award evaluation engine

Parent durable checkpoint: CP-0005B-MANUAL_QSO_LOTW_QUEUE.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override: do not return to CP-0003C until the owner explicitly says `resume CP-0003C`; skip other checkpoints that require phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a provider-independent award evaluator with separate WORKED, CONFIRMED, THRESHOLD_MET and OFFICIALLY_CLAIMABLE states. Confirmation evidence is explicit, exact QSO MODE/SUBMODE is preserved while a separate controlled award-mode grouping supports CW/PHONE/DIGITAL views, and evaluation can be all-band or band-scoped without mutating the QSO record. Local threshold completion cannot become official claimability without an explicit claimability evaluator.

CP-0006A intentionally uses synthetic/generic definitions only; official sponsor rules and claim URLs remain CP-0006B.

Host/CI gate: award 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; core 42,062 assertions, all PASS.

Finalization workflow run: 37458092347.

Evidence: research/awards/CP-0006A_AWARD_EVALUATION_ENGINE.md.

## CP-0006B — Official award rules/catalog

Parent durable checkpoint: CP-0006A-AWARD_EVALUATION_ENGINE.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a versioned official award catalog with source provenance and conservative rule shapes for ARRL DXCC Mixed, ARRL WAS, IARU WAC, ARRL Triple Play WAS, IOTA 100, POTA Bronze Hunter and SOTA Shack Sloth. Simple distinct-target catalog entries can generate local CP-0006A threshold definitions, but never attach sponsor claimability. Composite/program-scored awards fail closed rather than being flattened to unsafe counts.

Official sponsor standing is separately modeled and AWARDED/CREDITED requires explicit sponsor evidence. The current QSO model's missing normalized award-target fields remain explicit future work rather than being guessed from callsigns/free text.

Host/CI gate: official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37460474655.

Evidence: research/awards/CP-0006B_OFFICIAL_AWARD_CATALOG.md and research/awards/OFFICIAL_AWARD_SOURCES.tsv.

## CP-0006C — Award target enrichment and composite rules

Parent durable checkpoint: CP-0006B-OFFICIAL_AWARD_CATALOG.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has provenance-bearing normalized award target evidence outside QsoRecord plus an official award progress engine. DXCC/WAS/WAC/POTA consume explicit enrichment, Triple Play is a 150-cell state-by-mode LoTW-confirmed matrix, and IOTA 100 requires both 100 confirmed groups and seven-continent coverage. Official date/band/confirmation rules apply before contribution. Conflicting single-valued geography fails closed, callsigns/free text are not used to guess targets, SOTA remains external point scoring, and local progress never becomes sponsor claimability/award state automatically.

Host/CI gate: target/composite evaluator 77; official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37463697312.

Evidence: research/awards/CP-0006C_AWARD_TARGET_ENRICHMENT.md.

## CP-0006D — Awards Center progress projection

Parent durable checkpoint: CP-0006C-AWARD_TARGET_ENRICHMENT.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a UI-independent Awards Center projection over the verified official catalog/progress engine. Cards expose worked/confirmed/remaining state, deterministic display progress, Mixed/CW/Phone/Digital plus band/date views, official source/claim metadata, external-verification warnings and explicit sponsor standing. Triple Play mode-leg display completion stays distinct from full award threshold completion, IOTA count-plus-coverage remains transparent, and external SOTA scoring never receives a fabricated percentage. Local progress never infers sponsor claimability.

Host/CI gate: Awards Center projection 114; target/composite evaluator 77; official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37466410722.

Evidence: research/awards/CP-0006D_AWARDS_CENTER_PROJECTION.md.

## CP-0006E — Award evidence persistence and explicit ADIF enrichment import

Parent durable checkpoint: CP-0006D-AWARDS_CENTER_PROJECTION.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a provider-independent persistent award-evidence boundary with atomic/idempotent/conflict-safe batches and deterministic schema-versioned snapshot serialization. Explicit ADIF DXCC/STATE/CONT/IOTA/POTA metadata can create provenance-bearing target evidence; only explicit received-confirmation flags become confirmation evidence. Callsign/country/grid/notes/MY_* fields are not used for remote award inference, upload/sent state is not confirmation, and backing-store failure cannot advance repository state.

Host/CI gate: award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37469159046.

Evidence: research/awards/CP-0006E_AWARD_EVIDENCE_PERSISTENCE.md.

## CP-0006F — Awards Center application service and evidence-ingestion orchestration

Parent durable checkpoint: CP-0006E-AWARD_EVIDENCE_PERSISTENCE.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has one platform-independent Awards Center application service that composes the authoritative local logbook, persisted award evidence, explicit ADIF enrichment, and the verified projection. Evidence ingestion requires already-resolved immutable QSO ids, converts the full batch before one atomic repository write, fails closed on unknown/mismatched QSOs or malformed/conflicting evidence, and never performs callsign-only matching. Parsed LoTW-style host fixtures feed award progress without network access, while sponsor standing remains explicit external evidence.

Host/CI gate: Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37486907491.

Evidence: research/awards/CP-0006F_AWARDS_APPLICATION_SERVICE.md.

## CP-0006G — Extended official award catalog and evaluator coverage

Parent durable checkpoint: CP-0006F-AWARDS_APPLICATION_SERVICE.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has issuer-sourced ARRL VUCC 50 MHz/144 MHz/432 MHz and FFMA catalog entries, explicit Maidenhead four-character award evidence, ADIF GRIDSQUARE import, and evaluator-enforced required-band restrictions. The exact 488-grid FFMA universe is represented and locally testable. CQ WAZ/WPX and DARC DLD remain deliberately unencoded where a durable issuer-authoritative source could not be fully pinned through this research run.

Host/CI gate: extended catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37491149496.

Evidence: research/awards/CP-0006G_EXTENDED_AWARD_CATALOG.md and research/awards/OFFICIAL_AWARD_SOURCES.tsv.

## CP-0007A — Award-area map projection foundation

Parent durable checkpoint: CP-0006G-EXTENDED_AWARD_CATALOG.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a platform-independent award-area map projection for U.S. states and Maidenhead four-character grids. It distinguishes needed, worked-unconfirmed, confirmed, and local-threshold-met semantics; handles finite WAS/FFMA universes separately from open-ended VUCC grids; preserves deterministic QSO/provenance aggregation; and exposes metadata-only geometry bindings with no synthesized coordinates or boundaries. AwardsCenterApplicationService reads the same authoritative logbook/evidence repositories for both cards and map layers.

Host/CI gate: award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37495347219.

Evidence: research/maps/CP-0007A_AWARD_MAP_PROJECTION.md.

## CP-0007B — Award geometry providers and offline geometry-pack contract

Parent durable checkpoint: CP-0007A-AWARD_MAP_PROJECTION.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has platform-independent geometry primitives, provenance-enforced provider/pack contracts, deterministic IARU-derived four-character Maidenhead cell bounds, a Census-source/rights-attributed U.S. state production-pack manifest contract, explicit synthetic-fixture separation, and a conflict-safe provider registry that bridges actual geometry payloads to CP-0007A metadata-only map bindings.

Host/CI gate: geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37498410494.

Evidence: research/maps/CP-0007B_AWARD_GEOMETRY_PROVIDERS.md and research/maps/GEOMETRY_SOURCES.tsv.

## CP-0007C — Production U.S. state offline geometry pack

Parent durable checkpoint: CP-0007B-AWARD_GEOMETRY_PROVIDERS.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now includes a production offline geometry pack for exactly the 50 ARRL WAS states generated from the official U.S. Census Bureau 2025 national States 1:20,000,000 KML artifact. The upstream artifact is pinned by filename, URL, byte size, vintage, scale, and SHA-256; the generated 324,531-byte canonical pack and every state feature are independently hashed. Multipart/island geometry is preserved, Alaska antimeridian behavior is explicitly verified, and the pack is reproducible byte-for-byte from the pinned source. Production coordinates are stored as an offline asset with a small fail-closed loader instead of a compiler-heavy generated Kotlin initializer.

Host/CI gate: production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37502790657.

Evidence: research/maps/CP-0007C_PRODUCTION_US_STATE_PACK.md, research/maps/US_STATE_2025_20M_PACK.json, research/maps/GEOMETRY_SOURCES.tsv, and the production offline geometry asset.

## CP-0008A — Propagation intelligence domain and source-normalization foundation

Parent durable checkpoint: CP-0007C-US_STATE_GEOMETRY_PACK.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a provider-neutral propagation intelligence core separating direct heard/spot RF observations, solar/geomagnetic context, ionospheric map products, and modeled path estimates. Evidence retains source/version/retrieval/observation metadata, configurable freshness, confidence basis/explanation, quality flags, and explicit geography. Callsign-only geography is rejected. Path usability is explainable and conservative, stale/future evidence is ignored for current decisions, and offline snapshot/cache interfaces are defined without Android or network dependencies.

Official NOAA/SWPC and GIRO/LGDC documentation was checked for Kp storm semantics, F10.7 context, ionospheric/MUF concepts, MUF reference-distance semantics, and GIRO licensing/access constraints. All host evidence remains deterministic synthetic data; no live provider/account data is bundled.

Host/CI gate: propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37582214327.

Evidence: research/propagation/CP-0008A_PROPAGATION_FOUNDATION.md and research/propagation/PROPAGATION_SOURCES.tsv.

## CP-0008B — NOAA SWPC public propagation source adapter

Parent durable checkpoint: CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now has a transport-independent NOAA/SWPC solar/geomagnetic adapter pinned to the post-SCN 26-21 JSON object schema. Planetary Kp history, provider-labeled observed/estimated/predicted Kp records, and F10.7 summary observations normalize into the CP-0008A evidence domain with provider timestamps, retrieval UTC, explicit provenance, confidence basis/explanation, and quality metadata. Forecast records remain FORECAST; quoted legacy numeric fields and schema drift fail closed.

Three representative NOAA fixtures captured on 2026-10-07 are pinned by SHA-256. A separate live schema-only check verifies the public endpoints without asserting current values.

Host/CI gate: NOAA adapter 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37602514427.

Evidence: research/propagation/CP-0008B_NOAA_SWPC_ADAPTER.md, research/propagation/NOAA_SWPC_FIXTURES.json, and research/propagation/PROPAGATION_SOURCES.tsv.

## CP-0008C — NOAA SWPC GloTEC public ionospheric map adapter

Parent durable checkpoint: CP-0008B-NOAA_SWPC_PROPAGATION_ADAPTER.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now normalizes the pinned NOAA/SWPC GloTEC GeoJSON product into provider-neutral VTEC/TECU ionospheric-map evidence. Explicit provider coordinates, provider time_tag, retrieval UTC, quality_flag metadata, source identity, confidence basis/explanation and bounded coverage are retained. VTEC remains distinct from foF2/MUF/hmF2, and GloTEC-only context cannot produce a GOOD/MARGINAL/POOR path assessment or become a QSO.

The bounded deterministic fixture contains four exact feature rows from the recorded NOAA 2026-09-09T15:15:00Z grid, is 1,573 bytes, and has SHA-256 a98741d9a9586082db0eb357f3baf35be09a2646c8ab5b1b4203d4852b09bac2. NOAA/NWS remains authoritative for product semantics; the pinned public GitHub mirror is used only for exact fixture-row provenance.

Host/CI gate: GloTEC adapter 87; NOAA solar/geomagnetic adapter 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37634077198.

Evidence: research/propagation/CP-0008C_GLOTEC_IONOSPHERIC_ADAPTER.md, research/propagation/NOAA_SWPC_GLOTEC_FIXTURE.json, research/propagation/PROPAGATION_SOURCES.tsv, and core/src/main/kotlin/dev/n0png/fieldops/core/propagation/NoaaSwpcGlotecAdapter.kt.

## CP-0008D — PSK Reporter public heard-path adapter

Parent durable checkpoint: CP-0008C-NOAA_SWPC_GLOTEC_IONOSPHERIC_ADAPTER.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now normalizes the pinned PSK Reporter public XML retrieval shape into provider-neutral one-way HeardPathObservation evidence only when both endpoints carry explicit valid Maidenhead locators. Frequency/time/mode/SNR/source provenance are retained, incomplete reports are explicitly rejected rather than repaired from callsigns, and QSO/manual/test informationSource values are excluded when exposed. Exact duplicates collapse into reportCount rather than generating unstable evidence ids.

The deterministic fixture is 1,196 bytes with SHA-256 fb41c07330c8d446dbd52eb4b35358950145b8a75fab76f225e69859b5752da7 and contains four exact rows from the pinned public go-pskreporter recorded response. PSK Reporter developer documentation remains authoritative for API and field semantics.

Host/CI gate: PSK Reporter adapter 168; GloTEC adapter 87; NOAA adapter 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37639835383.

Evidence: research/propagation/CP-0008D_PSK_REPORTER_HEARD_PATH_ADAPTER.md, research/propagation/PSK_REPORTER_FIXTURE.json, research/propagation/PROPAGATION_SOURCES.tsv, and core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PskReporterHeardPathAdapter.kt.

## CP-0008E — Propagation evidence aggregation and offline cache service

Parent durable checkpoint: CP-0008D-PSK_REPORTER_HEARD_PATH_ADAPTER.

Hardware/account checkpoints CP-0003C and CP-0004A/B/C remain deferred and incomplete. Owner override remains active: do not return to CP-0003C until the owner explicitly says resume CP-0003C; skip checkpoints requiring phone/radio/real credentials/accounts/RF/manual hardware validation.

FieldOps now aggregates already-normalized NOAA solar/geomagnetic, GloTEC ionospheric, PSK Reporter heard-path and modeled evidence into deterministic PropagationSnapshot instances. Provider observation/retrieval timestamps survive unchanged, repeated same-source payloads deduplicate deterministically, heard reportCount uses max rather than addition, and materially conflicting provenance fails closed.

The existing PropagationSnapshotStore contract now has a bounded versioned file-backed implementation with deterministic binary round-trip, restart/reload history, as-of lookup, deterministic trimming and corruption/version checks. Context-only aggregation remains UNKNOWN for path usability and no new QSO, LoTW, model or heat score is created by aggregation.

The pinned NOAA fixtures expose one observed-Kp identity in both the dedicated Kp and forecast products. CP-0008E treats differing provenance as a collision; deterministic orchestration uses the dedicated feed for observed Kp and the forecast feed for estimated/predicted Kp.

Host/CI gate: aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA adapter 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended award catalog/grid evaluator 96; Awards Center application service 64; award evidence persistence/import 92; Awards Center projection 114; target/composite evaluator 77; base official catalog 115; award evaluator 67; manual/digital LoTW queue 47; universal logger 78; LoTW transaction 53; inherited core 42,062 assertions, all PASS.

Finalization workflow run: 37653853853.

Evidence: research/propagation/CP-0008E_PROPAGATION_AGGREGATION_OFFLINE_CACHE.md, core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationAggregation.kt, core/src/main/kotlin/dev/n0png/fieldops/core/propagation/FilePropagationSnapshotStore.kt.


## CP-0008F — Propagation operating-picture projection service

Parent durable checkpoint: CP-0008E-PROPAGATION_AGGREGATION_OFFLINE_CACHE.

FieldOps now projects stored provider-neutral propagation snapshots into deterministic workspace state while keeping heard RF paths, ionospheric context, solar/geomagnetic context and modeled paths distinct. Heard records retain explicit endpoint geography/callsigns/frequency/band/mode/SNR/reportCount/provenance/freshness. Ionospheric VTEC remains VTEC and is not converted into MUF or a heat score. Optional selected-path assessment reuses the explainable assessment engine against the complete snapshot.

Projection status exposes snapshot age, source retrieval bounds, stale/future evidence state and whether an offline-persistent store is available. FilePropagationSnapshotStore now advertises that capability through a provider-neutral marker interface.

Host/CI gate: projection 79; aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended awards 96; Awards service 64; persistence 92; Awards projection 114; target 77; catalog 115; award evaluator 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: 37658173873.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008F_PROPAGATION_PROJECTION.md and the PropagationProjectionModels/PropagationProjectionService source and tests.

## CP-0008G — Propagation source refresh coordinator

Parent durable checkpoint: CP-0008F-PROPAGATION_OPERATING_PICTURE_PROJECTION.

FieldOps now has a deterministic platform-neutral source refresh coordinator over already-normalized propagation evidence. Source definitions carry role/cadence/retry policy, source state tracks attempts/successes/failures/next eligibility, retryable failures use bounded exponential backoff, and successful recovery resets failure state.

Canonical NOAA Kp handling keeps the dedicated Kp feed as observed truth and removes forecast-product observed rows while preserving estimated/predicted rows. Successful sources replace only evidence they manage; failed or cadence-skipped source evidence is carried forward with original retrieval provenance. All-attempt and aggregate failures preserve the last good snapshot.

PropagationRefreshWorkspaceService bridges refresh to the existing projection layer so a successful cycle projects its new snapshot while a failed cycle continues to expose the last good operating picture together with explicit source failure state.

Host/CI gate: refresh coordinator 112; projection 79; aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended awards 96; Awards service 64; persistence 92; Awards projection 114; target 77; catalog 115; award evaluator 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: 37670764894.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008G_PROPAGATION_REFRESH_COORDINATOR.md, PropagationRefreshModels.kt, PropagationRefreshCoordinator.kt, PropagationRefreshWorkspaceService.kt, and PropagationRefreshCoordinatorTests.kt.

## CP-0008H — Public propagation transport adapters

Parent durable checkpoint: CP-0008G-PROPAGATION_SOURCE_REFRESH_COORDINATOR.

FieldOps now has a platform-neutral public request/response boundary connecting the existing NOAA SWPC, GloTEC and PSK Reporter parsers to the refresh coordinator. Requests are HTTPS-only, bounded by declared response size and media type, and responses retain request/effective URL, status, media type and body.

GloTEC uses deterministic two-stage index/artifact retrieval. The index selector requires valid JSON and only accepts canonical official NOAA artifact references. PSK Reporter queries are deterministic, enforce the documented five-minute minimum retrieval cadence, and never add appcontact or callback parameters.

Deterministic fake-transport integration proves all five public source definitions feed CP-0008G while canonical observed-Kp selection remains intact.

Host/CI gate: public transport 98; refresh coordinator 112; projection 79; aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended awards 96; Awards service 64; persistence 92; Awards projection 114; target 77; catalog 115; award evaluator 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: 37786722486.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008H_PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS.md, PublicPropagationTransport.kt, PublicPropagationSourceAdapters.kt, NoaaSwpcGlotecIndexSelector.kt, and PublicPropagationTransportAdapterTests.kt.

## CP-0008I — Concrete public HTTPS transport

Parent durable checkpoint: CP-0008H-PUBLIC_PROPAGATION_TRANSPORT_ADAPTERS.

FieldOps now has a concrete JVM/Android-compatible HttpsURLConnection implementation behind PublicPropagationTransport. The transport enforces HTTPS, explicit timeouts, redirect refusal, bounded streaming reads, identity encoding, strict UTF-8 decoding, exact response metadata, deterministic cleanup, and explicit retryability classes.

Connection creation is injectable, so required CI proves concrete connection behavior without live provider access. PublicPropagationSourceAdapters preserves transport retryability, and an integration test proves the concrete transport reaches the existing NOAA F10.7 parser unchanged.

Host/CI gate: concrete HTTPS 70; public transport 98; refresh coordinator 112; projection 79; aggregation/offline cache 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation foundation 154; production state pack 363; geometry providers 102; award-area map projection 96; extended awards 96; Awards service 64; persistence 92; Awards projection 114; target 77; catalog 115; award evaluator 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: 37793790597.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008I_CONCRETE_PUBLIC_HTTPS_TRANSPORT.md, HttpsUrlConnectionPublicPropagationTransport.kt, PublicPropagationSourceAdapters.kt, and HttpsUrlConnectionPublicPropagationTransportTests.kt.

## CP-0008J — Propagation runtime composition

Parent durable checkpoint: CP-0008I-CONCRETE_PUBLIC_HTTPS_TRANSPORT.

FieldOps now has a platform-neutral PropagationRuntime factory/config layer composing the concrete HTTPS transport, all five verified public sources, snapshot/state storage, refresh coordination and workspace projection behind one manual refresh-and-project entry point.

The runtime requires explicit operator callsign/query configuration, uses safe provider-respecting default cadences, does not infer station geography, and retains prior checkpoint behavior for canonical Kp selection, retry/backoff, last-good snapshots and projection filtering.

Host/CI gate: runtime 67; concrete HTTPS 70; public transport 98; refresh 112; projection 79; aggregation 101; PSK Reporter 168; GloTEC 87; NOAA 53; propagation 154; production state pack 363; geometry 102; map 96; extended 96; service 64; persistence 92; awards projection 114; target 77; catalog 115; award 67; queue 47; logger 78; LoTW transaction 53; core 42,062, all PASS.

Finalization workflow run: 37808105308.

CP-0003C remains DEFERRED and CP-0004A/B/C remain incomplete hardware checkpoints.

Evidence: research/propagation/CP-0008J_PROPAGATION_RUNTIME_COMPOSITION.md, PropagationRuntime.kt and PropagationRuntimeTests.kt.


## CP-0008K — Propagation refresh-state persistence

Parent: CP-0008J-PROPAGATION_RUNTIME_COMPOSITION.

Versioned and strictly validated per-source refresh-state serialization now survives injected runtime/store recreation. A same-directory atomic replace protects the last valid file on failed writes; corrupt/duplicate/version/role mismatches fail closed. The unchanged factory injection keeps network providers, cadence, snapshots and projection separate. CI tests synthetic provider failures without real network requests.

Verification: CP-0008K focused persistence PASS; CP-0008J runtime 67 PASS; all inherited regression jobs PASS. Finalizer run: 37811486748.

Deferred hardware checkpoints CP-0003C and CP-0004A/B/C remain incomplete.

Evidence: research/propagation/CP-0008K_REFRESH_STATE_PERSISTENCE.md, FilePropagationRefreshStateStore.kt, PropagationRefreshStatePersistenceTests.kt.


## CP-0008L — Propagation source-status presentation

Parent: CP-0008K-PROPAGATION_REFRESH_STATE_PERSISTENCE.

A read-only, explicit-UTC service now projects per-source scheduler eligibility, time until next refresh, last attempt and retry/backoff details separately from last-good cached evidence ages and per-observation freshness counts. Role matching keeps NOAA Kp observed/forecast, F10.7, GloTEC and PSK Reporter separate. PropagationRuntime.sourceStatus uses the existing injected stores. No network, hardware or Android background behavior was introduced.

Focused status suite PASS; inherited CP-0008K persistence/runtime/propagation/awards/logger/LoTW/core regression jobs PASS. Finalization run: 37815535216.

Deferred hardware/account checkpoints CP-0003C and CP-0004A/B/C remain incomplete.

Evidence: research/propagation/CP-0008L_SOURCE_STATUS_PRESENTATION.md, PropagationSourceStatusService.kt, PropagationRuntime.kt and PropagationSourceStatusTests.kt.


## CP-0008M — Propagation operating-picture read model

Parent: CP-0008L-PROPAGATION_SOURCE_STATUS_PRESENTATION.

A platform-neutral read-only composite now exposes the existing propagation workspace projection and per-source status from one captured cached snapshot and source-state list at one explicit UTC. It retains existing evidence/filters/assessments, read-only persistence, status failure/backoff and provenance without introducing providers, scheduling, Android lifecycle or hardware interaction.

Focused read-model tests PASS; full inherited host/CI matrix PASS. Finalizer run: 37819591445.

A sequential single-snapshot read does not guarantee atomic cross-store state. CP-0003C and CP-0004A/B/C remain deferred/incomplete.

Evidence: research/propagation/CP-0008M_OPERATING_PICTURE_READ_MODEL.md and PropagationOperatingPictureTests.kt.


## CP-0008N — Propagation read-model consistency diagnostics

Parent: CP-0008M-PROPAGATION_OPERATING_PICTURE_READ_MODEL.

A pure read-only diagnostic projection now compares cached snapshot capture, last source success and newest source evidence retrieval at one explicit UTC, with signed time deltas, before/equal/after/unknown ordering, source-specific observation freshness counts and future-date markers. It does not read stores or call providers separately. Runtime entrypoint composes without changing the existing operating picture.

Host CI focused suite PASS; inherited propagation, awards, logger/LoTW and core suites PASS. Finalizer run: 37823404574.

Cross-store atomicity remains explicitly unverified. CP-0003C and CP-0004A/B/C remain deferred/incomplete.

Evidence: research/propagation/CP-0008N_READ_MODEL_CONSISTENCY_DIAGNOSTICS.md and PropagationReadModelConsistencyTests.kt.
