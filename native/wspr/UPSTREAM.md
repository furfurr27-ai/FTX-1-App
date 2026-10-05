# Vendored WSPR decoder provenance

FieldOps CP-0002C vendors the decoder-side source files required from:

- Repository: `Guenael/rtlsdr-wsprd`
- Commit: `1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`
- Upstream license: GNU GPL v3
- Vendoring date: 2026-10-05

The files under `native/wspr/upstream/` are copied from that exact commit without FieldOps edits. FieldOps-specific JNI/adaptation code lives one directory above them.

Upstream source files copied:

- `wsprd.c/.h`
- `wsprsim_utils.c/.h`
- `wsprd_utils.c/.h`
- `fano.c/.h`
- `nhash.c/.h`
- `tab.c`
- `metric_tables.h`
- upstream `LICENSE`

Important integration fact: the pinned `wsprd.c` is C but uses the FFTW3 single-precision C API for its 512-point FFT. CP-0002C validates that exact decoder on Linux/CI with `libfftw3f`. Android/NDK packaging of this dependency is **not** claimed complete by CP-0002C and remains a later Android build task.

FieldOps receive path:

`12 kHz real PCM -> mix at 1500 Hz -> FIR /4 -> FIR /4 -> FIR /2 -> 375 sps complex I/Q -> pinned wspr_decode()`

No WSPR RF transmit waveform is implemented in CP-0002C. `get_wspr_channel_symbols()` is used only to generate deterministic receive fixtures and by the upstream decoder's own subtraction logic.


## CP-0002D TX use

CP-0002D promotes the already-vendored upstream `get_wspr_channel_symbols()` call from a receive-fixture helper to the production WSPR message-to-channel-symbol boundary. FieldOps still does not modify the pinned upstream files. FieldOps' own Kotlin layer performs the 12 kHz continuous-phase 4-FSK audio synthesis and the guarded radio session owns all CAT/PTT/USB-audio transmission.
