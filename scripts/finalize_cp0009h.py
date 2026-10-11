#!/usr/bin/env python3
"""CP-0009H: publish a durable handoff only after merged-main CI is green."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009H-ARCHIVE_RECOVERY_FAILURE_INJECTION"
parent = "CP-0009G-ANDROID_ARCHIVE_PERSISTENCE_CONTRACT"
next_label = "CP-0009I — encrypted archive threat-model and migration contract"

def one(content, pattern, replacement, label):
    updated, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Missing/ambiguous " + label + " (" + str(count) + ")")
    return updated

(root / "VERSION").write_text("v59-archive-recovery-failure-injection\n")
readme_path = root / "README.md"
readme = readme_path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Inherited host verification ancestry missing")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v58.

CP-0009H hardens the Android app-private opt-in offline report archive.
Before replacing an existing save, the store rechecks its bounded, canonical
receipt-validated contents and refuses to overwrite corrupt evidence.
A separate explicit DELETE action is required to discard unreadable files.

Robolectric API 35 tests inject failures after staged-file sync and
immediately before atomic replacement. In both cases the previously
committed bytes remain unchanged and staged temporary files are cleaned.
Android view-click tests verify invalid Restore and later Save leave corrupt
bytes and existing on-screen history untouched, with explicit recovery text.

These are simulated JVM Android failures. The app-private storage is NOT
encrypted, digitally signed, origin-authenticated, physically power-loss
durable, or proven on Galaxy S23 Ultra hardware. No live FTX-1, USB CAT,
RF, microphone/PTT, account or source-data validation is claimed.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Host/CI gates:

- CP-0009H simulated precommit failure and corrupt overwrite protection: **PASS**.
- CP-0009H Robolectric archive recovery UX: **PASS**.
- CP-0009H Android debug APK/aapt/ZIP/artifact: **PASS**.
""" + inherited + """

Evidence:

- research/android/CP-0009H_ARCHIVE_RECOVERY_FAILURE_INJECTION.md
- app/src/main/kotlin/dev/n0png/fieldops/app/OptInArchiveStore.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/OptInArchiveStoreTest.kt
- app/src/test/java/dev/n0png/fieldops/app/ArchiveHistoryActivityTest.java
- .github/workflows/build-android-apk.yml
- CP-0009H merged-main finalizer: """ + run_id + """

### Evidence boundary

Host and Robolectric simulation, APK package and inherited tests only.
No physical device, RF, encryption, account or power-loss proof.

### Inherited verified ancestry

""" + parent + """ and older immutable checkpoints remain ancestry.

"""
readme = one(readme, r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
             baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = one(readme, r"\*\*Active software track:\*\* [^\n]+",
             "**Active software track:** " + next_label, "README track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
                "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + next_label + """.**

1. Specify a threat model for optional encrypted app-private archive storage
   without asserting existing data is encrypted or authenticated.
2. Design versioned migration from CP-0009G canonical unencrypted envelopes,
   including explicit rollback, rotation and bad-key rejection scenarios.
3. Preserve the no-backup opt-in gate, all failure-injection/restore tests,
   actual APK artifact and inherited host regressions.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete. No device/radio claims.

"""
readme = one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
             next_section + "## README maintenance contract", "README next")
readme = re.sub(r"\n## CP-0009H implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009H now guards corruption and simulated interrupted writes in private
archive storage, with explicit user recovery notices. Next design a migration
and threat-model contract for optional encrypted storage without pretending
current reports are encrypted or source-authenticated.
""")
track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = one(track, r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
            "## Latest verified durable parent\n\n" + checkpoint +
            "\n\n## Deferred but incomplete hardware/account work", "software baseline")
track = one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
            """## Active software checkpoint

""" + next_label + """

Design versioned encrypted archive threat model and migration contract.
Retain fault-injected atomic save, explicit opt-in, APK/Robolectric and host
regressions. CP-0003C DEFERRED; CP-0004A/B/C incomplete. No RF/device proof.

## Resume rule""", "software next")
track = re.sub(r"\n## CP-0009H implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", track)
track_path.write_text(track.rstrip() + "\n")
history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009H — archive corrupt-file recovery and failure injection" not in history:
    history += """

## CP-0009H — archive corrupt-file recovery and failure injection

Parent: """ + parent + """. An existing archive is validated before
Save can overwrite it. Injected failures after staging sync and before
atomic replacement preserve previous committed bytes, and staged files
are cleaned. Robolectric checks explicit invalid-restore and corrupt-save
recovery messages. No encryption, physical device, power-loss guarantee,
radio CAT/USB/RF, real account or provider authenticity proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009H_ARCHIVE_RECOVERY_FAILURE_INJECTION.md.
"""
history_path.write_text(history.rstrip() + "\n")
