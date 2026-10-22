#!/usr/bin/env python3
"""Main-only CP-0009R acceptance integration promotion after green CI."""
from pathlib import Path
import os
import re

root = Path(".")
checkpoint = "CP-0009R-ANDROID_OPERATING_NAVIGATION_INTEGRATION"
parent = "CP-0009Q-RECOVERY_CONSENT_FAILURE_UX"
run_id = os.environ["FINALIZE_RUN_ID"]

def replace_exactly_once(src: str, pattern: str, replacement: str, label: str) -> str:
    result, count = re.subn(pattern, lambda _: replacement, src, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one {label}; got {count}")
    return result

matrix = root / "RELEASE_ACCEPTANCE_MATRIX.md"
if not matrix.is_file():
    raise SystemExit("Finite release acceptance matrix must be merged before CP-0009R finalization")

(root / "VERSION").write_text("v69-android-operating-navigation-integration\n")
readme = (root / "README.md").read_text()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** CP-0009R-ANDROID_OPERATING_NAVIGATION_INTEGRATION

Parent durable checkpoint: CP-0009Q-RECOVERY_CONSENT_FAILURE_UX / v68.

CP-0009R replaces the launcher-only developer presentation with
integrated, non-transmitting OPS/DIGI/APRS/MAP/LOG/AWARDS/RADIO
navigation. The canonical DigitalModes profiles, FastQsoLogger,
AdifCodec, AwardsCenterApplicationService and award map projection
now feed the installed Android Activity. Users can manually log
CW/SSB contacts in volatile memory and see the same records in ADIF
and local award projections. No local QSO disk persistence, LoTW
submissions, USB/CAT/PTT, receiver decoding or RF operation.

Android Robolectric verifies navigation, core-backed QSOs, rejected
invalid input, disabled TX, shared awards/ADIF views and
configuration-change retention versus fresh-process loss.
Existing archive/recovery and inherited core regression tests
remain in the verified software matrix. The actual Android v69
APK is packaged in Actions artifacts.

Host/CI gates:

- CP-0009R integrated Android operating navigation/real local QSO workflow: **PASS**.
- CP-0009R Android disabled CAT/PTT/LoTW and lifecycle tests: **PASS**.
- CP-0009R Android APK and inherited Kotlin/host regressions: **PASS**.

Evidence:

- app/src/main/kotlin/dev/n0png/fieldops/app/FieldOpsOperatingActivity.kt
- app/src/main/java/dev/n0png/fieldops/app/MainActivity.java
- app/src/test/kotlin/dev/n0png/fieldops/app/FieldOpsOperatingActivityTest.kt
- scripts/test_android_package_contract.py
- research/android/CP-0009R_OPERATING_INTEGRATION.md
- RELEASE_ACCEPTANCE_MATRIX.md
- .github/workflows/cp0009r-operating-integration.yml
- .github/workflows/finalize-cp0009r.yml
- CP-0009R merged-main finalizer: """ + run_id + """

### Evidence boundary

This checkpoint connects offline-safe Android navigation and local
in-memory logging/awards, not native mode providers or physical
S23 Ultra/FTX-1 USB/CAT/audio/RF. CP-0003C DEFERRED,
CP-0004A/B/C incomplete. FRESHNESS NOT VERIFIED; no archive
key retirement, auto fallback or LoTW account/certificate proof.

### Inherited verified ancestry

CP-0009Q and preceding immutable checkpoints remain intact.

"""
readme = replace_exactly_once(
    readme, r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline"
)
readme = replace_exactly_once(
    readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** finite P0–P3 integration, next FT-family RX software acceptance gap",
    "README active software track"
)
readme = replace_exactly_once(
    readme, r"## Current exact next action\n.*?\n## README maintenance contract",
    """## Current exact next action

**FT8/FT4/FT2 software operating integration** is the next
acceptance gap, with native DSP vectors, safe audio/decoder session
and UI integration. Use RELEASE_ACCEPTANCE_MATRIX.md rather than
generating a recovery hardening or optional mode checkpoint.
Build software-only simulated workflows, prohibit live RF until
CP-0004A/B/C physical validation, and preserve CP-0003C DEFERRED.
Close every original P0–P3 gap before one final software release
candidate. Once verified, STOP optional checkpoint creation.

## README maintenance contract""", "README next action"
)
(root / "README.md").write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

Latest verified durable checkpoint: CP-0009R-ANDROID_OPERATING_NAVIGATION_INTEGRATION
Version: v69-android-operating-navigation-integration
Merged-main finalizer: """ + run_id + """

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
""")

p = root / "SOFTWARE_TRACK.md"
track = p.read_text()
track = replace_exactly_once(
    track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work",
    "software parent"
)
track = replace_exactly_once(
    track,
    r"## (?:Finite software release track|Active software checkpoint)\n.*?\n## Resume rule",
    """## Finite software release track

CP-0009R is complete as integrated, non-transmitting Android
navigation with manual in-memory QSO/ADIF/awards shared state,
disabled hardware-dependent actions and passing Android tests.
It does not close digital RX/TX, real CAT/USB audio, persistent
logbook, full APRS, advanced digital modes or physical validation.
Follow RELEASE_ACCEPTANCE_MATRIX.md and first close the
FT8/FT4/FT2 DSP-to-Android operating workflow.

Stop generating software checkpoints once the original
P0–P3 integrated software release candidate is durably verified.
CP-0003C DEFERRED, CP-0004A/B/C incomplete.

## Resume rule""",
    "software active track"
)
p.write_text(track.rstrip() + "\n")

history = root / "checkpoints/CHECKPOINT_HISTORY.md"
h = history.read_text()
if "## CP-0009R — integrated Android operating navigation" not in h:
    h += """

## CP-0009R — integrated Android operating navigation

Parent CP-0009Q/v68. Launcher uses shared canonical mode metadata,
in-memory FastQsoLogger/ADIF/AwardsCenter and map projection across
OPS/DIGI/APRS/MAP/LOG/AWARDS/RADIO workspaces. Manual CW/SSB
QSOs are an actual local-only operating workflow; not RF proof.
Android Robolectric checks source-backed navigation, log records,
invalid input and rotation/fresh process boundary; APK/inherited
CI green. No new permissions, USB/CAT/audio/PTT, LoTW upload,
device proof, or persistent QSO claims.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009R_OPERATING_INTEGRATION.md.
"""
    history.write_text(h.rstrip() + "\n")

m = matrix.read_text()
anchor = "## Primary integration evidence"
if anchor not in m:
    raise SystemExit("Release matrix missing expected primary evidence heading")
note = """## CP-0009R integrated evidence

CP-0009R / v69 finalizer """ + run_id + """ integrates
`app/src/main/kotlin/dev/n0png/fieldops/app/FieldOpsOperatingActivity.kt`
into the launcher. Robolectric tests
`FieldOpsOperatingActivityTest.kt` verify real user-entered
CW/SSB QSO creation through `FastQsoLogger`, ADIF export preview,
shared Awards Center projection, mode profiles, navigation, fresh
session loss versus rotation, and locked transmitter controls.
This changes the following acceptance dispositions **only**:

- Android home/navigation: PARTIALLY INTEGRATED — OPS/DIGI/APRS/MAP/
  LOG/AWARDS/RADIO navigable; actual live operating engines absent.
- CW/SSB logbook: PARTIALLY INTEGRATED — manual QSO/ADIF works
  in RAM; device/radio and durable DB remain unfinished.
- Awards: PARTIALLY INTEGRATED — the actual projection consumes the
  shared RAM logbook; imported confirmation/persistence gaps remain.
- Digital mode UI: PARTIALLY INTEGRATED — mode timing metadata only,
  with RX/TX disabled and no linked providers.
- Map: PARTIALLY INTEGRATED — awardMapLayers() uses real service;
  no interactive geospatial renderer or live source.

No previously marked unmet P0–P3 software requirement is
considered fully accepted by this checkpoint.

"""
m = m.replace(anchor, note + anchor, 1)
matrix.write_text(m)
