# CP-0002E — Native-mode regression evidence

Checkpoint objective: run FT8, FT4, FT2, JS8, WSPR and APRS regression gates independently, then verify that the shared audio/timing/TX-ownership architecture still composes correctly before advancing to TrustedQSL work.

## Authority

The verified work queue requires:

- FT8 / FT4 / FT2 regression
- JS8 regression
- WSPR regression
- APRS regression
- one immutable native-modes checkpoint only after all required mode gates are green

Parent checkpoint: `CP-0002D-WSPR_NATIVE_TX`.

## Independent mode gates

Final pre-merge branch workflow run: **37316265651**

All eight independent jobs completed successfully. The composition job also dry-ran the CP-0002E durable handoff generator, verified the next-action transition to CP-0003A, and passed `git diff --check`.

Earlier complete regression run `37315659564` produced the same mode-family assertion counts before the finalizer/dry-run wiring was added.

### FT8

`scripts/test_ft_family_mode.sh FT8`

Result:

- production Java ABI declarations compile
- production `FtFamilyNativeEngine` compiles against the real ABI declarations
- host-only deterministic FT8AF fixture exercises the production Kotlin adapter
- decode entry-point selection/mapping: PASS
- FT8 encoder selection: PASS
- 79-tone choice: PASS
- 0.160 s symbol period: PASS
- BT=2.0: PASS
- 12 kHz waveform metadata/length path: PASS
- **20/20 assertions PASS**

### FT4

`scripts/test_ft_family_mode.sh FT4`

Result:

- production ABI compile: PASS
- production adapter compile: PASS
- FT4 decoder path selection/mapping: PASS
- FT4 encoder selection: PASS
- 105-tone choice: PASS
- 0.048 s symbol period: PASS
- BT=1.0: PASS
- **20/20 assertions PASS**

### FT2

`scripts/test_ft_family_mode.sh FT2`

Result:

- production ABI compile: PASS
- production adapter compile: PASS
- FT2-specific decoder entry points: PASS
- FT4-family 105-tone channel encoder selection: PASS
- 0.024 s symbol period: PASS
- BT=1.0: PASS
- **20/20 assertions PASS**

### JS8

`scripts/test_js8_native.sh`

Result:

- JS8 RX: **19/19 PASS**
- JS8 TX: **39/39 PASS**
- inherited core: **42,062 PASS**
- pipeline: **56 PASS**
- LoTW: **19 PASS**

The JS8 evidence remains tied to:

`JS8Call-improved/Android-port@9996202f355569c5ee7b97fae539f3b763081dc2`

Its exact successful upstream ARM64 artifact/JNI evidence remains recorded in the CP-0002A/CP-0002B evidence documents. CP-0002E re-runs the FieldOps host adapter/safety regressions; it does not claim actual S23 + FTX-1 JS8 RF operation.

### WSPR

`scripts/test_wspr_tx.sh`

Result:

- exact pinned vendored WSPR blobs verified
- WSPR RX: **21/21 PASS**
- WSPR TX: **56/56 PASS**
- inherited core: **42,062 PASS**
- pipeline: **56 PASS**
- LoTW: **19 PASS**

Pinned WSPR source remains:

`Guenael/rtlsdr-wsprd@1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`

This gate executes the host native WSPR codec and the production FieldOps RX/TX waveform paths.

### APRS

`scripts/test_aprs_regression.sh`

Result: **2,048/2,048 assertions PASS**

The isolated APRS gate covers:

- KISS reserved-byte framing plus 2,000 deterministic fuzz round trips
- AX.25 UI/address/FCS behavior
- APRS position encoding
- Bell-202 AFSK1200 clean round trip
- 24 deterministic noisy Bell-202 round trips
- streaming APRS decode across arbitrary 48 kHz callback boundaries
- duplicate suppression
- SmartBeaconing timing/bearing behavior
- APRS denial while another radio owner is actively transmitting

## Shared composition gate

`scripts/test_native_mode_composition.sh`

Result: **130/130 assertions PASS**

The composition gate proves:

- one shared 48 kHz capture block reaches the raw branch unchanged
- the same capture produces the expected 12 kHz weak-signal stream
- JS8 runs as a continuous 12 kHz consumer
- selecting FT4 stops JS8 streaming and switches to a complete UTC-aligned window
- FIR group-delay correction preserves the expected FT4 slot UTC
- a complete FT4 slot reaches the windowed engine as exactly 90,000 samples at 12 kHz
- FT8/FT4/FT2/WSPR timing metadata remains stable
- for each of FT8, FT4, FT2, JS8, WSPR and APRS, an active TX owner cannot be stolen by another mode
- the common `Ftx1RadioSession` produces the same `TX1 -> audio -> TX0 -> release` lifecycle for every mode owner

## Inherited core gate

`scripts/test_core_regression.sh`

Result:

- core: **42,062 PASS**
- pipeline: **56 PASS**
- LoTW: **19 PASS**

The core suite is compiled separately against the proven main source output, preserving the checkpoint-first test discipline.

## FT-family evidence boundary

The public Git repository intentionally does **not** contain the extracted ARM64 `libft8af.so`. The recovery record identifies the omitted binary as SHA-256:

`858a6ab58bb89bbc3e9f9e81effb899a03c121c9cccdc81b2803440e97d348a1`

Therefore CP-0002E does **not** claim native FT8/FT4/FT2 DSP execution on the GitHub x86_64 runner.

Instead the FT regression gates deliberately separate two things:

1. production FT8AF Java ABI declarations and `FtFamilyNativeEngine` must compile unchanged;
2. the production Kotlin adapter is then executed against host-only deterministic ABI test doubles to verify the FT8/FT4/FT2 decoder/encoder routing, mapping, symbol/timing choices and waveform sizing logic.

The exact historical ARM64 export evidence from the recovered FT8AF APK remains prior evidence, not newly re-executed native DSP evidence in CP-0002E.

## Device/RF boundary

Still not verified by CP-0002E:

- actual S23 Ultra runtime
- actual FTX-1 CAT/USB audio enumeration and routing
- FT8/FT4/FT2 native decoding against known-good WAVs on the phone
- actual JS8 native execution on the phone
- Android arm64 WSPR/FFTW3 packaging
- real APRS off-air reception
- actual RF TX, levels, ALC, spectral purity or watchdog behavior

These remain later hardware checkpoints and must not be inferred from green host/CI results.

## Exit decision

CP-0002E is eligible for immutable checkpoint finalization only if the same independent gates pass again on the merged `main` commit.

After that, the exact next work-queue item is **CP-0003A — TrustedQSL signer bridge**.
