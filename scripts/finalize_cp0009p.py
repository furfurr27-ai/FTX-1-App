#!/usr/bin/env python3
"""CP-0009P main-only checkpoint promotion after the exact merged-main CI passes."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009P-AUDIT_LIFECYCLE_PROVENANCE_CONSISTENCY"
parent = "CP-0009O-SESSION_RECOVERY_AUDIT_PROVENANCE"
next_label = "CP-0009Q — recovery consent and failure UX hardening"

def replace_once(value, pattern, replacement, name):
    new, count = re.subn(pattern, lambda _: replacement, value, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Expected one " + name + ", got " + str(count))
    return new

(root / "VERSION").write_text("v67-audit-lifecycle-provenance-consistency\n")

readme_path = root / "README.md"
readme = readme_path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if match is None:
    raise SystemExit("Missing inherited host/CI regression evidence")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v66.

CP-0009P hardens explicit encrypted archive choice and display-source
consistency. File evidence mutation, rotated-copy changes, delete, or
encrypted/rotation session disable invalidates previously reviewed
candidates and forces another explicit review. No stale CONFIRM remains
visible after those user actions. Canonical reinspection/authentication
and two distinct SELECT and CONFIRM steps are preserved.

Successfully authenticated in-memory source labels survive paging and
selection/comparison that do not change the archive. Synthetic capture,
workspace archive mutation or separate V1/V2 restore replaces in-memory
bytes and invalidates that source. Failed/null reads leave existing
on-screen data and source unchanged. Activity recreation clears the
session audit and source claim, never asserting unverified provenance.
Disk writes or deletes do not erase already displayed in-memory history.

The bounded typed audit adds OPTIONS_INVALIDATED/CLEARED and
DISPLAY_REPLACED/ACCEPTED without logging payloads, callsigns, error
messages, key aliases, file paths, digests, dates, tokens, passwords,
session IDs or clock values. No persistent file/DB/Bundle/log/network
audit, key retirement, implicit fallback, anti-replay counter or secure
flash wipe. FRESHNESS NOT VERIFIED; older authenticated V2 replay is
still possible. Old-key retirement remains BLOCKED_NON_EXECUTING.

Host/CI gates:

- CP-0009P review invalidation after source evidence changes: **PASS**.
- CP-0009P Activity navigation/replacement/recreation tests: **PASS**.
- CP-0009P Android APK, inherited Kotlin/host regressions: **PASS**.
""" + inherited + """

Evidence:

- app/src/main/kotlin/dev/n0png/fieldops/app/ArchiveHistoryActivity.kt
- app/src/main/kotlin/dev/n0png/fieldops/app/SessionRecoveryAuditTrail.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/RecoveryAuditLifecycleConsistencyTest.kt
- scripts/test_android_package_contract.py
- research/android/CP-0009P_AUDIT_LIFECYCLE_CONSISTENCY.md
- .github/workflows/cp0009p-audit-lifecycle-consistency.yml
- .github/workflows/finalize-cp0009p.yml
- CP-0009P merged-main finalizer: """ + run_id + """

### Evidence boundary

Host Android API35 synthetic/Robolectric/software-key and built APK
only. No real S23 Ultra/Keystore/storage, FTX-1 CAT/USB/audio/RF,
LoTW credentials, trusted recency or key retirement proof.
CP-0003C DEFERRED; CP-0004A/B/C hardware checkpoints incomplete.

### Inherited verified ancestry

""" + parent + """ and preceding immutable checkpoints remain intact.

"""
readme = replace_once(readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_once(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_label, "README active track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
readme = replace_once(readme,
    r"## Current exact next action\\n.*?\\n## README maintenance contract",
"""## Current exact next action

**""" + next_label + """.**

1. Improve transparent recovery consent and failure UX without claiming
   cryptographic freshness or physical radio provenance.
2. Preserve bounded typed-only audit, two-tap reauthentication and
   BLOCKED_NON_EXECUTING key retirement.
3. Preserve Android APK/Robolectric, inherited CI and immutable SHA-256.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## README maintenance contract""", "README exact next action")
readme = re.sub(r"\\n## CP-0009P implementation branch — not checkpointed\\n[\\s\\S]*$",
    "\\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009P clears pending recovery candidates if original/rotated
ciphertext evidence might change or an encrypted session is disabled.
Successful reviewed recovery source survives UI paging/selection but
not replacement by synthetic or separately restored archive bytes.
Null/failed load leaves prior view untouched. Audit remains in memory,
typed-only, bounded, with no timestamp, key, path or payload.
Next improve consent/failure presentation; keep FRESHNESS NOT VERIFIED,
the two-step original/rotated reauthentication and blocked retirement.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_once(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work",
    "SOFTWARE_TRACK baseline")
track = replace_once(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
"""## Active software checkpoint

""" + next_label + """

CP-0009P verified audit choice invalidation on disk changes, review
clearing on disable, authenticated source kept across navigation but
cleared after archive replacement, and failed-load preservation.
Robolectric and Android APK/Kotlin CI pass. Session audit remains
nonpersistent and typed. No physical S23, Keystore, CAT/RF, freshness
witness or key retirement proof. CP-0003C DEFERRED, CP-0004A/B/C
incomplete.

## Resume rule""", "SOFTWARE_TRACK active checkpoint")
track = re.sub(r"\n## CP-0009P work in progress — not durable\n[\s\S]*$",
    "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009P — recovery audit lifecycle consistency" not in history:
    history += """

## CP-0009P — recovery audit lifecycle consistency

Parent: """ + parent + """. Encrypted file copy save/migrate/delete/
rotation and disabled sessions invalidate stale reviewed candidate
choices. Navigation preserving archive contents retains current
recovered source label; synthetic/new archive bytes invalidate it.
Failed or null restores preserve on-screen history and provenance.
Audit remains bounded, typed-only, session-only and cleared on
Activity recreation. FRESHNESS NOT VERIFIED; no automatic fallback,
key retirement, secure erase, durable witness or physical hardware
tests. All Android APK/Robolectric/inherited Kotlin CI GREEN.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009P_AUDIT_LIFECYCLE_CONSISTENCY.md.
"""
history_path.write_text(history.rstrip() + "\n")
