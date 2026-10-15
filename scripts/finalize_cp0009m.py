#!/usr/bin/env python3
"""CP-0009M durable finalizer; only main CI may promote the checkpoint."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009M-ENCRYPTED_ARCHIVE_CONFLICT_INSPECTION"
parent = "CP-0009L-EXPLICIT_ENCRYPTED_KEY_ROTATION"
next_label = "CP-0009N — explicit archive recovery choice and retirement-readiness review"

def replace_once(text, pattern, replacement, label):
    result, count = re.subn(pattern, lambda _m: replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Expected one " + label + ", found " + str(count))
    return result

(root / "VERSION").write_text("v64-encrypted-archive-conflict-inspection\n")
readme_path = root / "README.md"
readme = readme_path.read_text()
m = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not m:
    raise SystemExit("Missing inherited regression evidence in README")
inherited = m.group(1).strip()

baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v63.

CP-0009M adds opt-in READ-ONLY authenticated inspection of original encrypted
V2 and separate rotated V2 archive candidates. Existing distinct-key archive
stores authenticate independently; existing canonical receipts are decoded.
Candidates are classified as MISSING, AUTHENTICATED, KEY_UNAVAILABLE, or
UNREADABLE, without mistaking authentication failure for proven corruption.
Two authenticated canonical archives are checked for equality, yielding
MATCHING_AUTHENTICATED or CONFLICTING_AUTHENTICATED; otherwise status remains
INDETERMINATE or BOTH_MISSING.

Android provides an explicit inspection button requiring BOTH encrypted and
rotation sessions enabled. No inspection action creates keys, writes files,
restores archive UI state, selects a winner, deletes the original V2/V1,
deletes the rotated copy, retires aliases, or attempts secure erase.
A missing/unavailable key does not trigger replacement. All results continue
to say FRESHNESS NOT VERIFIED and BLOCKED_NON_EXECUTING for old-key
retirement. Even authenticated matching ciphertext content cannot establish
recency without an independent trusted monotonic witness. This is a
non-executing retirement safety policy, not a key-retirement implementation.

Robolectric tests cover equal copies, conflicting canonical archives,
missing files, lost old/new keys, tampered candidate tags, absent opt-ins,
no automatic fallback, and unchanged ciphertext. The Android demo remains
synthetic-only. Real Galaxy S23 Ultra Keystore, filesystem hard links,
power-loss durability, CAT/USB/audio/RF, FTX-1 hardware, LoTW accounts and
certificates remain UNTESTED. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Host/CI gates:

- CP-0009M read-only archive inspection and conflict Robolectric tests: **PASS**.
- CP-0009M Android opt-in/no retirement UX and APK packaging: **PASS**.
- CP-0009M complete inherited GitHub Actions software matrix: **PASS**.
""" + inherited + """

Evidence:

- app/src/main/kotlin/dev/n0png/fieldops/app/EncryptedArchiveConflictInspector.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/EncryptedArchiveConflictInspectorTest.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/EncryptedArchiveConflictActivityTest.kt
- research/android/CP-0009M_ARCHIVE_CONFLICT_INSPECTION.md
- .github/workflows/cp0009m-archive-conflict-inspection.yml
- .github/workflows/finalize-cp0009m.yml
- CP-0009M merged-main finalizer run: """ + run_id + """

### Evidence boundary

Only software-key Robolectric/JVM/Android build evidence. Authentication is not
freshness, station provenance, device security, secure erase, or RF validation.
No key retirement executor, file deletion, automatic copy preference, or
trusted replay witness has been implemented by CP-0009M.

### Inherited verified ancestry

""" + parent + """ and every earlier immutable checkpoint remain intact.

"""
readme = replace_once(
    readme, r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_once(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_label, "README active track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
readme = replace_once(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
"""## Current exact next action

**""" + next_label + """.**

1. Design safe, EXPLICIT user selection for independently authenticated
   recovery candidates without silently preferring older/rotated content.
2. Provide a non-executing retirement-readiness checklist; old key, V1/V2
   and rotated ciphertext may not be destroyed without separate evidence
   and authorization. Do not implement automatic key retirement or erase.
3. Preserve full host/Android APK and Robolectric regression CI, immutable
   snapshot proof, and visible FRESHNESS NOT VERIFIED warning.
4. CP-0003C remains DEFERRED; CP-0004A/B/C incomplete.

## README maintenance contract""", "README next action")
readme = re.sub(r"\n## CP-0009M implementation branch — not checkpointed\n[\s\S]*$",
    "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009M classified authenticated, missing, unavailable-key and unreadable
original and rotated encrypted candidates without preference, modification or
key retirement. Next design explicitly chosen recovery and a NON-EXECUTING
retirement-readiness checklist with conservative evidence requirements.
No key deletion, automatic fallback, freshness witness, device/RF proof.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_once(track, r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "software track baseline")
track = replace_once(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
"""## Active software checkpoint

""" + next_label + """

CP-0009M implements read-only separately authenticated archive comparison
and explicit UI; no source is automatically chosen. Missing-key states
are not mislabeled as tampering. Both Keystore aliases and all files remain.
Robolectric, APK and inherited CI have been verified; no phone/Keystore,
filesystem, trusted replay witness, secure erase or radio/account proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule""", "software track next")
track = re.sub(r"\n## CP-0009M work in progress — not durable\n[\s\S]*$",
    "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009M — read-only encrypted archive candidate inspection" not in history:
    history += """

## CP-0009M — read-only encrypted archive candidate inspection

Parent: """ + parent + """. Original and rotated encrypted V2 files are
independently authenticated and canonical-receipt validated on explicit
inspection only. Missing files, lost keys, unreadable ciphertext, matching and
conflicting authenticated canonical archives have explicit states.
No automatic winner/restore, key retirement, erase, downgrade or replay proof.
Robolectric cases, Android APK and inherited CI verified. Hardware remains
untested; CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009M_ARCHIVE_CONFLICT_INSPECTION.md.
"""
history_path.write_text(history.rstrip() + "\n")
