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
