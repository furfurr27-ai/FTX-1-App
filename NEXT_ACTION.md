# NEXT ACTION — FTX-1 FieldOps

**CP-0002D — WSPR native TX only**

Use the pinned WSPR channel-symbol encoder and synthesize the complete 12 kHz 4-FSK waveform: 162 symbols, 8192 samples per symbol, 1.46484375 Hz tone spacing, 1,327,104 samples total, 110.592 seconds. Route that waveform only through the common FieldOps TX arbiter/PTT/audio path and prove no competing owner can transmit and all error/cancel paths collapse to RX with `TX0`, audio stopped and ownership released.

Do **not** start the broader CP-0002E native-mode regression until CP-0002D is green. Actual RF output/ALC/spectrum remains hardware-gated.
