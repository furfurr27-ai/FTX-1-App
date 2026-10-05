# CP-0002D — WSPR native TX integration evidence

Checkpoint objective: use the exact pinned upstream WSPR channel-symbol encoder, synthesize the complete 12 kHz 4-FSK waveform in FieldOps, and prove that every physical WSPR transmission path goes through the common FieldOps radio arbiter/PTT/audio owner.

## Pinned upstream source actually checked

- Repository: `Guenael/rtlsdr-wsprd`
- Commit: `1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`
- Upstream license: GNU GPL v3
- Exact vendored source blob identities remain recorded in `native/wspr/UPSTREAM_BLOBS.tsv`.

The pinned upstream `decoderSelfTest()` was checked directly. It calls `get_wspr_channel_symbols()` and establishes the modulation relationship used by FieldOps:

`tone = center + (symbol - 1.5) * (375 / 256) Hz`

The same checked self-test uses 162 channel symbols and 256 samples/symbol at 375 sps, which scales exactly to 8192 samples/symbol at the FieldOps 12 kHz modem rate.

FieldOps does not reimplement WSPR message packing/coding. Production message-to-channel-symbol conversion calls the pinned upstream `get_wspr_channel_symbols()` through `fieldops_wspr_encode_symbols()` / JNI. FieldOps adds input-shape validation before that native call so malformed nonempty text cannot reach the upstream parser's unchecked token assumptions.

## Production TX path

`WSPR message -> pinned native channel-symbol encoder -> 162 symbols -> FieldOps continuous-phase 12 kHz 4-FSK -> WsprTxController -> Ftx1RadioSession -> CAT PTT + TX audio`

Production constants in `WsprTxWaveformSynthesizer`:

- sample rate: **12,000 samples/s**
- channel symbols: **162**
- samples per symbol: **8,192**
- adjacent tone spacing: **1.46484375 Hz**
- total waveform samples: **1,327,104**
- encoded waveform duration: **110.592 seconds**
- normalized waveform amplitude: **0.8**
- tone mapping: `centerAudioHz + (symbol - 1.5) * 1.46484375 Hz`

One phase accumulator is retained across all 162 symbols, so symbol changes alter frequency without resetting phase.

## Pinned-symbol production round-trip

The production TX test uses the pinned native encoder for:

`K1JT FN20QI 20`

The generated production 12 kHz waveform is inserted two seconds into a 120-second WSPR receive window, passed through the already-verified FieldOps 12 kHz real -> 375 sps complex-I/Q front end, and decoded by the exact pinned native decoder.

The required recovered values are:

- call: `K1JT`
- locator: `FN20`
- power: `20`
- decoded audio center: within 2 Hz of the requested 1550 Hz fixture center

This proves the production symbol encoder + waveform synthesizer against the independent pinned decoder path rather than only checking generated array sizes.

## Radio ownership / fail-safe path

`WsprTxController` generates the complete waveform before PTT. It then calls `Ftx1RadioSession.beginStreamingTx(Owner.WSPR, 12000, ...)`.

The controller has no direct CAT or USB-audio handle.

The focused tests prove:

- WSPR obtains `Owner.WSPR` before audio can leave the app.
- CAT `TX1;` is issued only by `Ftx1RadioSession`.
- no WSPR audio is written before the common PTT lead completes.
- the full successful transmission sends all 1,327,104 samples through the guarded audio stream.
- normal completion attempts `TX0;`, closes audio and returns the arbiter to unowned RX.
- a competing JS8 owner already in TX cannot be stolen or disturbed; WSPR issues no CAT command and opens no audio endpoint.
- cancellation after partial audio immediately attempts `TX0;`, closes audio and releases WSPR ownership.
- an injected audio-write failure collapses to the same RX-safe state.
- malformed nonempty WSPR text fails before CAT PTT, audio open or radio ownership.

Audio is pumped in bounded chunks so the eventual scheduler/service can cancel between writes rather than committing one uninterruptible 110.592-second application-level write.

## Verified branch evidence

Initial complete green branch run `37312820618`:

- exact pinned WSPR blob verification: PASS
- core regression: **42,062 PASS**
- pipeline assertions: **56 PASS**
- LoTW assertions: **19 PASS**
- WSPR RX: **21/21 PASS**
- WSPR TX: **56/56 PASS**
- JS8 RX: **19/19 PASS**
- JS8 TX: **39/39 PASS**
- focused CP-0002D gate: **PASS**

Subsequent branch runs re-check the additional production input-validation/provenance changes before merge.

## Evidence boundary

Verified/host-tested:

- pinned upstream WSPR channel-symbol encoder is the production message codec boundary
- exact 162-symbol / 8192-sample-per-symbol / 1.46484375-Hz / 1,327,104-sample / 110.592-second waveform invariants
- production WSPR TX waveform decodes through the pinned native RX path
- common FieldOps WSPR radio ownership and PTT/audio path
- competing-owner denial
- cancellation and audio-failure RX-safe cleanup
- malformed-input no-PTT behavior
- inherited WSPR RX and JS8 RX/TX host regressions

Not yet verified:

- Android arm64-v8a packaging of the WSPR/FFTW3 native module
- actual Galaxy S23 Ultra execution
- whether the FTX-1 Android USB playback endpoint accepts the 12 kHz stream directly or requires a final device-rate adapter
- actual FTX-1 TX audio routing/level
- RF output level, ALC, spectral purity and frequency accuracy
- off-air WSPR transmission/reception
- WSPR even-minute beacon scheduling/timing in the eventual Android service/workspace

CP-0002D therefore proves the codec, waveform and guarded software TX path. It does not claim real RF transmission.
