# NEXT ACTION — FTX-1 FieldOps

Latest verified durable checkpoint: CP-0009R-ANDROID_OPERATING_NAVIGATION_INTEGRATION
Version: v69-android-operating-navigation-integration
Merged-main finalizer: 39870937975

CP-0009R provides Android multi-workspace navigation plus a real
local-only QSO -> ADIF -> awards integration path with no radio
connection. The APK does NOT provide FT8/FT4/FT2 DSP, JS8/WSPR
native operations, live APRS or hardware CAT/PTT.

## Single next necessary software acceptance task

Integrate FT8/FT4/FT2 mode-specific receive and structured-QSO
workflows into Android using existing shared DSP/audio and
radio-session abstractions, with genuine golden-vector and
simulated end-to-end tests. Separate hardware-gated TX from
software-verified state. Refer to RELEASE_ACCEPTANCE_MATRIX.md.

Do not create a new numbered checkpoint for speculative hardening.
Complete original P0–P3 requirements and final software RC, then STOP.

CP-0003C is DEFERRED until the owner literally says
'resume CP-0003C'. CP-0004A/B/C remain incomplete physical proof.
No unattended TX, unapproved LoTW, false freshness or auto key retirement.
