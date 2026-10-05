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
