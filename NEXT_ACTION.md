# NEXT ACTION — FTX-1 FieldOps

**CP-0002C — WSPR native RX only**

Vendor and integrate the pinned pure-C WSPR decoder from `Guenael/rtlsdr-wsprd@1ca9b83dd2562ce9ef2453aacdd5bc3aab982c7d`. Feed it from the existing 12 kHz receive branch through a complex-baseband mixer, low-pass filter and /32 decimation to 375 Hz. Prove the complete receive path with a pinned/native encoder fixture and synthesized WSPR waveform that decodes to the expected message.

Do **not** implement WSPR TX in this checkpoint.
