# FTX-1 FieldOps

Private Android operating environment for **Chris / N0PNG** and the **Yaesu FTX-1 / FTX-1F**, with the **Samsung Galaxy S23 Ultra** as the primary target device.

This repository is the durable source of truth for FieldOps. Chat discussion, plans, and summaries are useful navigation aids, but they do **not** count as completed engineering work unless the relevant files and evidence exist in a verified checkpoint/commit.

## New-chat handoff — read this first

When starting a new ChatGPT/Codex chat for this project:

1. Read this `README.md` first.
2. Read `checkpoints/LATEST.json`, `checkpoints/CURRENT_STATE.json`, and `checkpoints/RESUME_HERE.md`.
3. Inspect the current Git tree and latest checkpoint before changing code.
4. Do not redo already-verified work unless verification fails or a newer requirement explicitly invalidates it.
5. Do not promote a feature from planned/researched/host-tested to verified/device-tested without evidence.
6. Update this README in the **same durable commit** whenever the current goals, explicit rules, processed sources/data, established logic, verified checkpoint, blockers, or exact next action materially change.

If this README and an older checkpoint document disagree, preserve both facts and resolve the discrepancy from the newest verified artifact/commit. Never silently choose the more convenient version.

## Primary goal

FieldOps is a one-stop operating environment for one radio family: the Yaesu FTX-1 / FTX-1F.

It is **not** intended to become a universal radio-control application. Hardware-specific complexity stays concentrated in a single FTX-1 device/radio layer. Extensibility belongs above that layer in mode providers, logging/sync providers, awards, maps, propagation, and operating workspaces.

Target operating modes include:

- FT8, FT4, FT2
- JS8
- WSPR
- APRS
- CW
- SSB
- RTTY
- PSK31 / PSK63
- Olivia / MFSK
- Contestia / DominoEX / THOR / Hellschreiber / MT63
- later SSTV and other useful modes

## Verified durable baseline

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
- CP-0008A finalization workflow run: 37582214327

### Evidence boundary

CP-0008A proves the provider-neutral propagation domain and synthetic host/CI behavior. It does not claim live NOAA/GIRO/PSK Reporter/WSPRnet integration, HFcast/VOACAP predictions, a scientifically calibrated universal path score, Android map rendering, on-device network caching, or current real-world propagation conditions.

### Inherited verified ancestry

CP-0007C-US_STATE_GEOMETRY_PACK, CP-0007B-AWARD_GEOMETRY_PROVIDERS, CP-0007A-AWARD_MAP_PROJECTION, CP-0006G-EXTENDED_AWARD_CATALOG, CP-0006F-AWARDS_APPLICATION_SERVICE, CP-0006E-AWARD_EVIDENCE_PERSISTENCE, CP-0006D-AWARDS_CENTER_PROJECTION, CP-0006C-AWARD_TARGET_ENRICHMENT, CP-0006B-OFFICIAL_AWARD_CATALOG, CP-0006A-AWARD_EVALUATION_ENGINE, CP-0005B-MANUAL_QSO_LOTW_QUEUE, CP-0005A-UNIVERSAL_QSO_LOGGER, CP-0003B-LOTW_TRANSACTION_SAFE, CP-0003A-TRUSTEDQSL_SIGNER, CP-0002E-NATIVE_MODE_REGRESSION, and prior native-mode checkpoints remain verified ancestry.

## Execution tracks and hardware-gated deferrals

FieldOps has two practical execution lanes:

- **software-only checkpoints** that can be completed and verified in host/CI without Chris's phone, radio, certificate/account interaction, or other physical setup;
- **hardware/account checkpoints** that require the Galaxy S23 Ultra, Yaesu FTX-1, real USB enumeration/RF behavior, or controlled LoTW account/certificate use.

Hardware-gated work must never be falsely marked complete. If a hardware/account checkpoint blocks progress, preserve any work-in-progress on its named branch, record the blocker here, and continue only when Chris explicitly authorizes moving to the next genuinely software-only checkpoint.

Current deferred hardware/account checkpoints:

- `CP-0003C — Real LoTW validation`: not complete. Android TrustedQSL packaging/device/account validation remains hardware/account gated. WIP is preserved on branch `cp-0003c-real-lotw-validation`.
- `CP-0004A — FTX-1 CAT USB port`: not complete; requires real Android/FTX-1 USB topology proof.
- `CP-0004B — FTX-1 USB audio`: not complete; requires actual Android endpoint/sample-rate enumeration.
- `CP-0004C — S23 + FTX-1 hardware proof`: not complete by definition.

### Owner-directed deferred checkpoint rule

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says `resume CP-0003C`.

While this rule is active, skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. Skipping a gated checkpoint never means it passed.

**Active software track:** CP-0008B — NOAA SWPC public propagation source adapter

Skipping a hardware-gated checkpoint in the execution order does **not** imply it passed. The next software checkpoint may use the latest verified durable software baseline as its parent while carrying the skipped hardware checkpoints forward as explicit blockers.

## Engineering rules

### Checkpoint first

A feature is durably complete only when it exists in a verified immutable checkpoint/commit containing enough evidence to resume without relying on chat memory.

A checkpoint should record, as applicable:

- parent checkpoint/commit
- exact source revisions and pinned upstream commits
- hashes
- tests run and results
- completed items
- blockers/red items
- exact next action
- source snapshot or reproducible source state

`RESUME_HERE.md` plus machine-readable checkpoint state are the recovery path after interruption.

### Anti-freeze / test discipline

Do not repeatedly compile the entire project when a smaller slice can prove the current change.

Preferred progression:

1. inspect latest checkpoint and working tree
2. compile the smallest affected main source slice once
3. compile tests separately against the proven main output
4. run focused suites independently
5. run broader regression only after the affected slice is green
6. checkpoint immediately when the objective is proven

A timeout is neither a pass nor a fail. Do not rerun already-proven subsystems merely because a later suite stalled.

### Radio safety

- Never PTT on startup, reconnect, or mode changes.
- Every transmission goes through one FieldOps TX ownership/arbitration path.
- RTS/DTR are not PTT.
- CAT TX commands own PTT.
- Any TX failure collapses to RX-safe state: PTT off / `TX0`, audio stopped, TX ownership released.
- No modem, helper, workspace, or UI may bypass the TX owner.
- Ambiguous CAT USB-port identification means TX stays disabled.
- Hardware-dependent claims remain RED until tested on the actual S23 Ultra + FTX-1.

### Evidence labels

Use precise labels:

- **verified/durable** — present in a verified checkpoint/commit
- **host-tested** — tested on the host but not the phone/radio
- **Android-build-ready** — source/build integration prepared, not necessarily installed
- **real-device-tested** — actually tested on the S23 Ultra / FTX-1
- **planned/researched** — design/reference work only

Do not use “working,” “native,” “built,” or “tested” without saying which evidence level applies.

## Established signal-path logic

### Shared RX

FTX-1 USB audio is treated as a common **48 kHz mono PCM** source.

Branch 1:

`48 kHz raw -> Bell 202 AFSK1200 -> HDLC -> AX.25 -> APRS`

Branch 2:

`48 kHz -> 97-tap anti-alias FIR -> decimate by 4 -> 12 kHz weak-signal/modem domain`

FT8/FT4/FT2 and WSPR use time-window logic appropriate to each protocol.

For WSPR RX, the 12 kHz branch continues through:

`12 kHz real -> 1500 Hz complex mixer -> FIR /4 -> FIR /4 -> FIR /2 -> 375 sps complex I/Q -> pinned wspr_decode()`

The resulting WSPR decoder window is exactly 45,000 complex samples over 120 seconds.

JS8 is a continuous 12 kHz stream consumer and must **not** be forced through the FT/WSPR slot-window assembler.

### TX ownership

For WSPR TX:

`message -> pinned get_wspr_channel_symbols() -> 162 symbols -> continuous-phase 12 kHz 4-FSK -> WsprTxController -> Ftx1RadioSession`

The WSPR modem layer never owns physical PTT. The current host-proven waveform is 1,327,104 samples / 110.592 seconds at 12 kHz; final device-rate handling remains hardware-gated.

The radio session owns CAT/PTT and USB TX audio. Complete-waveform modes and streaming modes may use different waveform/feed mechanics, but both must use the same ownership and fail-safe boundary.

The current FTX-1 operating preference is PRESET 1 with TX BPF SEL 300–2700 Hz. Practical FT8 audio should normally remain roughly 400–2700 Hz.

## Mode/provider architecture

The long-term common mode boundary is a `DigitalModeProvider`-style contract carrying, at minimum:

- identity and display name
- exact ADIF mode/submode identity
- streaming vs slotted timing model
- sample-rate/bandwidth needs
- RX and TX lifecycle
- AFC/tuning metadata
- waterfall metadata
- recommended FTX-1 mode/filter settings
- logging metadata
- TX timing/safety requirements
- optional conversation/quick-message capabilities

Providers never own physical PTT.

## Logging and LoTW logic

The local logbook is authoritative.

Manual SSB/CW and digital contacts use the same QSO core and sync queue. Preserve:

- exact radio mode
- ADIF parent mode/submode
- controlled award-mode grouping
- band/frequency
- UTC
- station profile/location
- sync state per external provider

LoTW state is not binary. Track at least:

- not uploaded
- pending
- submitted
- accepted
- confirmed
- retry/failure

HTTP 200 does not prove a QSO was accepted or confirmed.

The intended LoTW signing flow is:

`local QSO -> TrustedQSL sign -> .tq8 -> upload -> receipt/acceptance query -> later confirmation sync -> reconcile`

Station location must be explicit. Never infer signing location solely from callsign.

Website credentials are separate from certificate/PKCS#12 material and passwords. Never write secrets, certificates, passwords, or signing keys to logs, crash reports, support bundles, or this public repository.

## Awards logic

Awards are first-class application state, not decoration on the logbook.

Keep distinct:

- needed
- worked
- confirmed
- threshold met
- potentially claimable
- officially awarded/credited when known

Local threshold completion does not automatically equal sponsor claimability. Official rules and claim links must be verified from the issuing organization before encoding requirements.

Keep exact mode/submode and a controlled award-mode group. Do not collapse unrelated digital submodes simply because ADIF places them under a common parent family.

## Map and propagation logic

The map is a core operating view.

Layer categories include:

- heard/decoded signals
- logged QSOs
- APRS
- observed propagation
- predicted propagation
- award progress
- Maidenhead/grid geography
- DXCC/CQ/ITU/state geography where a real versioned/licensed dataset is available

Observed RF paths and modeled propagation must remain separate.

Do not fabricate geographic polygons.

Prediction results must retain model/provider provenance, generation time, validity/freshness, inputs, band/frequency, power/path assumptions, and reliability/confidence.

NOAA/SWPC products are model inputs/evidence, not observed RF paths.

The later recovery handoff identifies embedded HFcast as the intended prediction path, but that implementation must be recovered/verified before GitHub treats it as durable.

## FTX-1 USB target rules

The intended CAT path is the FTX-1 **Enhanced COM Port**.

Default CAT settings: **38400, 8N1**, configurable.

Do not assume USB interface 0 is CAT. Port resolution priority is:

1. descriptor/name identifying the Enhanced port
2. previously verified saved mapping
3. exact CP2105 topology fallback

If identification remains ambiguous, TX is disabled.

USB audio endpoint/rate selection is fail-closed until actual Android enumeration is known.

## Processed research / source pins

The CP-0001 survey processed more than 100 amateur-radio GitHub hits and pinned a smaller high-value reference set to exact commits. The authoritative pin table is:

`research/github/SOURCE_PINS.tsv`

Important references include FT8AF, JS8Call Android, rtlsdr-wsprd, fldigi, SDRangel, OpenWebRX, Dire Wolf, Hamlib, wfview, Wavelog, KLog, QLog, Cloudlog, ham2k/Polo, DVOACAP, Look4Sat, Pat, FreeDATA, FreeDV, QSSTV and others.

References are categorized as architecture/behavior/protocol/reusable implementation candidates. Do not copy code merely because it is public. Review licensing before vendoring any implementation.

Community-demand research is tracked separately from technical source pins. The 2026-10-05 /r/amateurradio discussion on modes people actually enjoy/use reinforces keyboard-conversation modes (JS8, PSK31, Olivia, Hellschreiber), practical messaging (Winlink/VARA), SSTV interest, and the need for activity-discovery UX. JTTY, VarAC and FreeDV are watch/research items rather than immediate implementation commitments. See `research/community/REDDIT_DIGITAL_MODES_2026-10-05.md`.

## Repository/import notes

The verified CP-0001 text/source tree has now been restored to GitHub `main`.

- GitHub restore commit: `722de2e7b744b67a77ffa05a25b1b70f933871ab`
- Restore workflow: **PASS**; the CP-0001 recovery workflow is now manual-only and requires explicit `RESTORE_CP0001` confirmation
- Reassembled source-transport archive SHA-256: `125026544bb75c8089b14f8fbbcdba131d755ad7599427bb7495acd717e3cbf2`
- Current Git source baseline: `CP-0008A-PROPAGATION_INTELLIGENCE_FOUNDATION`
- Original external checkpoint package verification before import: **PASS, 157 file hashes**

The Git checkout contains the recovered source/text/checkpoint metadata, including `checkpoints/LATEST.json`, `checkpoints/CURRENT_STATE.json`, `checkpoints/RESUME_HERE.md`, and `research/github/SOURCE_PINS.tsv`.

The original recovery package also contained generated build outputs and binary artifacts such as the extracted FT8AF native library, compiled classes/JARs, the checkpoint snapshot tarball, and a UI PNG. Those artifacts were intentionally not restored into this public Git source tree. See `artifacts/CP-0001-BINARY-ARTIFACTS.md` for recovery hashes/status.

Because the original immutable checkpoint snapshot is intentionally absent from the public checkout, the old snapshot-based verifier is not expected to reproduce its original PASS directly against this Git tree. The original CP-0001 ZIP remains the verified evidence package; this Git commit is the durable recovered source baseline.

The repository is currently **public**. Never commit credentials, private keys, PKCS#12 files, LoTW passwords, personal tokens, device secrets, or other sensitive material.

## Current exact next action

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

## README maintenance contract

This file is the human-readable project handoff.

Every future durable engineering pass must review and update this README when any of the following changes:

- primary/current goal
- explicit engineering or safety rule
- verified checkpoint/baseline
- processed source/data set or source pin
- established protocol/DSP/state-machine logic
- evidence status
- blocker
- exact next action

A new chat should be able to read this file and know what FieldOps is, what is proven, what is merely planned/recovered from discussion, and exactly where to resume.
