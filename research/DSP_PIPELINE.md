# Shared DSP/audio pipeline — v3

## Signal path

```
FTX-1 USB audio IN (48 kHz mono PCM)
        |
        +--> 48 kHz raw branch --> Bell-202 AFSK1200 --> HDLC/AX.25 --> APRS
        |
        +--> 97-tap anti-alias FIR / 4
                  |
               12 kHz
                  |
                  +--> waterfall / spectrum
                  +--> UTC slot assembler --> FT8 / FT4 / FT2
                  +--> UTC slot assembler --> JS8
                  +--> UTC slot assembler --> WSPR
```

TX uses the inverse ownership model: each modem generates a normalized float waveform, but all waveforms leave through one `Ftx1RadioSession`. That session is the only component allowed to coordinate phone-side PTT and TX audio.

## Why 48 kHz capture
- FTX-1 USB audio is handled as one continuous Android endpoint.
- Bell-202 at 1200/2200 Hz has an exact 40 samples/bit at 48 kHz.
- 48 -> 12 kHz is an exact factor of four for the weak-signal engines.
- The DSP branch can carry its known FIR group delay into UTC timestamps.

## Implemented in this layer
- one capture/fanout model
- hardware-frame-based Android AudioTimestamp timing when available
- Blackman-windowed 97-tap low-pass decimator
- UTC/monotonic disciplined clock mapping
- exact slot-window assembler
- common windowed DSP interfaces
- FT8/FT4/FT2 native adapter and ARM64 binary ABI shim
- Bell-202 AFSK modulator + demodulator, HDLC NRZI/bit stuffing and AX.25 FCS
- JS8 and WSPR adapter boundaries
- serialized common CAT command/reply transport
- common guarded CAT/PTT/TX-audio session
- S23 Ultra Android AudioRecord/AudioTrack adapters
- JVM tests for filtering, fanout, timing, APRS DSP and fail-safe PTT

## Still intentionally external
The JS8 and WSPR codecs are not reimplemented from memory. The current JS8Call-improved Android port already provides a platform-independent core + Android JNI/AAR layer; WSPR `wsprd` remains GPLv3 code. Their adapters are present, but their upstream source must be vendored/built in an Android NDK environment before those two adapters become executable.

This is preferable to silently shipping guessed or partially compatible RF protocols.
