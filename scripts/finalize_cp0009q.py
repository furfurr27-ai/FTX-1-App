#!/usr/bin/env python3
"""CP-0009Q main-only checkpoint promotion after the exact merged-main CI passes."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009Q-RECOVERY_CONSENT_FAILURE_UX"
parent = "CP-0009P-AUDIT_LIFECYCLE_PROVENANCE_CONSISTENCY"
next_label = "CP-0009R — recovery accessibility and failure-path coverage"

def replace_once(value, pattern, replacement, name):
    new, count = re.subn(pattern, lambda _: replacement, value, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Expected one " + name + ", got " + str(count))
    return new

(root / "VERSION").write_text("v68-recovery-consent-failure-ux\n")

readme_path = root / "README.md"
readme = readme_path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if match is None:
    raise SystemExit("Missing inherited host/CI regression evidence")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v67.

CP-0009Q improves explicit consent and conservative failure handling
on a separately selected authenticated encrypted recovery candidate.
Only a distinct CONFIRM action re-inspects and reauthenticates both
candidate views and validates the canonical selected digest before
replacing on-screen memory. A new CANCEL RECOVERY SELECTION action
clears the pending choice without disk writes, key changes or an
implicit fallback. The selected source is displayed in the consent
message, alongside the warning that unsaved in-memory changes can
be lost. No automatic candidate choice is made.

Rejected confirmation never replaces the displayed archive/source,
never reports raw exception text, and requires deliberate REVIEW AGAIN.
Explicit CANCEL_SELECTION/CLEARED is a fixed enum-only audit event.
The bounded session-only audit still contains no archive payload,
path, hash, key, account, timestamp, error strings, storage, logcat
or network persistence. Original V1/V2 files and keys are unchanged.
FRESHNESS NOT VERIFIED; authenticated older ciphertext may be replayed.
No station/RF provenance, key retirement or secure erase is implied.

Host/CI gates:

- CP-0009Q explicit source-specific consent/cancel Robolectric: **PASS**.
- CP-0009Q changed-ciphertext rejection and view retention: **PASS**.
- CP-0009Q Android APK and inherited Kotlin/host regressions: **PASS**.
""" + inherited + """

Evidence:

- app/src/main/kotlin/dev/n0png/fieldops/app/ArchiveHistoryActivity.kt
- app/src/main/kotlin/dev/n0png/fieldops/app/SessionRecoveryAuditTrail.kt
- app/src/main/kotlin/dev/n0png/fieldops/app/RecoveryConsentUx.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/RecoveryConsentUxTest.kt
- scripts/test_android_package_contract.py
- research/android/CP-0009Q_RECOVERY_CONSENT_FAILURE_UX.md
- .github/workflows/cp0009q-audit-lifecycle-consistency.yml
- .github/workflows/finalize-cp0009q.yml
- CP-0009Q merged-main finalizer: """ + run_id + """

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
    r"## Current exact next action\n.*?\n## README maintenance contract",
"""## Current exact next action

**""" + next_label + """.**

1. Improve accessibility of explicit recovery warnings and consent
   presentation without weakening authentication or altering stored keys.
2. Exercise failed recovery paths and cancel/review state with more UI
   tests, preserving typed-only audit and FRESHNESS NOT VERIFIED.
3. Preserve APK/Robolectric/inherited CI and immutable snapshot proof.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## README maintenance contract""", "README exact next action")
readme = re.sub(r"\n## CP-0009Q implementation branch — not checkpointed\n[\s\S]*$",
    "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009Q adds a source-specific consent notice only after deliberate
REVIEW and SELECT, warns about loss of unsaved on-screen changes and
valid older ciphertext replay, and offers explicit CANCEL without
restoring or changing files/keys. Failed confirmation displays a safe
non-diagnostic rejection, retains on-screen data/source, prevents
automatic fallback and requires REVIEW AGAIN. Typed bounded audit
adds CANCEL_SELECTION only; no secrets, paths, hashes, errors or
timestamps persist. Next address accessibility and additional
failure-path tests. CP-0003C DEFERRED; CP-0004A/B/C incomplete.
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

CP-0009Q verified deliberate source-specific consent and cancel,
safe generic rejection, retained display/provenance on failure, typed
session-only CANCEL_SELECTION and no automatic fallback. Host
Robolectric and Android APK/inherited Kotlin CI pass. No physical S23,
Keystore, CAT/RF, freshness witness or key retirement proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule""", "SOFTWARE_TRACK active checkpoint")
track = re.sub(r"\n## CP-0009Q work in progress — not durable\n[\s\S]*$",
    "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009Q — recovery consent and failure UX" not in history:
    history += """

## CP-0009Q — recovery consent and failure UX

Parent: """ + parent + """. Explicit consent is shown only after
review and selection of one authenticated original/rotated V2 candidate.
The notice warns about replacement of in-memory history and loss of
unsaved changes, lack of trusted freshness, and no station provenance.
An explicit CANCEL_SELECTION clears choice with fixed typed-only
audit metadata; failed CONFIRM retains data/source, removes stale
choices and shows a sanitized recovery rejection without fallback.
Robolectric tests cover both consent sources, cancel without file
writes, independent confirmation and tampered ciphertext rejection.
Android APK and inherited host CI GREEN; no trusted freshness,
key retirement, secure erase or physical hardware proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009Q_RECOVERY_CONSENT_FAILURE_UX.md.
"""
history_path.write_text(history.rstrip() + "\n")
