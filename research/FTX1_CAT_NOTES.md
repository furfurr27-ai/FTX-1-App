# FTX-1 CAT implementation notes

Verified against Yaesu's English FTX-1 CAT manual family (US product page currently lists 2508C; command layouts checked in the accessible manual PDF).

## Core commands
- Set main frequency: `FA` + 9-digit Hz + `;` — example 14.250 MHz: `FA014250000;`
- Read main frequency: `FA;`
- Set operating mode: `MD` + side + mode + `;`
  - MAIN = `0`
  - DATA-U = `C`
  - therefore MAIN DATA-U = `MD0C;`
- Read MAIN operating mode: `MD0;`
- CAT transmit on: `TX1;`
- transmit off: `TX0;`
- read TX state: `TX;`

## EX menu format
- Set: `EX` + P1(2d) + P2(2d) + P3(2d) + P4 + `;`
- Read: `EX` + P1(2d) + P2(2d) + P3(2d) + `;`

## APRS controls exposed by CAT
Examples from Table 3:
- `06 / 01 / 01` modem select: OFF/AUTO/MAIN/SUB
- `06 / 01 / 02` modem type: 1200/9600
- `06 / 01 / 04` APRS TX delay
- `06 / 01 / 05` APRS callsign
- `06 / 04 / 01` digipeater path
- `07 / 01 / 01` beacon type OFF/AUTO/SMART
- `07 / 02` auto-beacon settings
- `07 / 03` SmartBeaconing settings
- `07 / 04` beacon text settings
- `08` APRS filters/popups/ringers/message filters

## PRESET controls useful to FT8
PRESET1 includes CAT rate/timeout, USB OUT LEVEL, TX BPF selection, modulation source, USB modulation gain, and RPTT selection. TX BPF selection `3` corresponds to 300-2700 Hz in the checked table, making it a strong default validation target for this FTX-1-specific build.

## Engineering rule
Read menu values on connection and show a non-destructive "radio profile differs" banner before changing them. Never silently rewrite a user's FTX-1 presets at startup.
