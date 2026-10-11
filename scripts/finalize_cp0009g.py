#!/usr/bin/env python3
"""CP-0009G: publish a durable handoff only after merged-main CI is green."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009G-ANDROID_ARCHIVE_PERSISTENCE_CONTRACT"
parent = "CP-0009F-ANDROID_ARCHIVE_NAVIGATION_TESTS"
next_label = "CP-0009H — persisted archive recovery UX and failure-injection hardening"

def one(content, pattern, replacement, label):
    updated, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Missing/ambiguous " + label + " (" + str(count) + ")")
    return updated

(root / "VERSION").write_text("v58-android-archive-persistence-contract\n")
readme_path = root / "README.md"
readme = readme_path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Inherited host verification ancestry missing")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v57.

CP-0009G adds explicit opt-in private-file storage for synthetic offline
propagation archives in the compiled Android APK. A versioned, bounded
canonical archive envelope stores JSON bytes in insertion order, includes
a SHA-256 corruption check, and independently reimports all canonical
report receipts on every restore. Corrupt/truncated/oversized input fails
closed; no silent fixture substitution is reported as successful restore.

Android saves only after a user enables local storage for the current
Activity session AND selects Save. Restore and Delete are separate actions.
The app-private no-backup file is not encrypted or source-authenticated.
A staged file is fsynced and replaced with a same-directory atomic move;
unsupported atomic replacement fails instead of falling back to a
non-atomic update. This is not evidence of physical power-loss durability.

Host Kotlin tests and Robolectric simulated Android private-file tests
verify codec determinism, corruption rejection, opt-in and fresh-Activity
restore. No real Galaxy S23 Ultra install, actual RF/CAT/USB, live
propagation data, TalkBack behavior, device filesystem durability,
credential/account access, or encryption is verified.

CP-0003C DEFERRED; CP-0004A/B/C hardware validation incomplete.

Host/CI gates:

- CP-0009G bounded canonical archive envelope host integrity tests: **PASS**.
- CP-0009G Robolectric explicit opt-in private-store/restore/failure tests: **PASS**.
- CP-0009G no-permission Android APK build/aapt/ZIP/artifact: **PASS**.
""" + inherited + """

Evidence:

- research/android/CP-0009G_ANDROID_ARCHIVE_PERSISTENCE_CONTRACT.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/PropagationArchiveStorageCodec.kt
- app/src/main/kotlin/dev/n0png/fieldops/app/OptInArchiveStore.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/OptInArchiveStoreTest.kt
- .github/workflows/build-android-apk.yml
- CP-0009G merged-main finalizer: """ + run_id + """

### Evidence boundary

Only host/JVM and Robolectric simulation, actual APK package, private-file
storage logic and inherited checks proven. No physical phone, RF,
account, encryption, or real-world durability testing.

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

1. Harden explicit failure notices, torn-write/error recovery and
   process-death restore testing with synthetic fixtures in Android CI.
2. Investigate atomic directory commit durability and evaluate encrypted
   on-device storage; do not claim encryption before implementation.
3. Preserve opt-in/no-backup behavior, canonical receipt validation,
   bounded wire format, debug APK build and all inherited regressions.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete. No live RF or phone tests.

"""
readme = one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
             next_section + "## README maintenance contract", "README next")
readme = re.sub(r"\n## CP-0009G implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

The CP-0009G explicit opt-in, app-private canonical archive storage gate
passed simulated Android and host tests plus a CI-built APK. Next harden
failure/recovery behavior and evaluate Android storage durability without
claiming real-device testing, encryption or authenticated source data.
""")
track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = one(track, r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
            "## Latest verified durable parent\n\n" + checkpoint +
            "\n\n## Deferred but incomplete hardware/account work", "software baseline")
track = one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
            """## Active software checkpoint

""" + next_label + """

Harden explicit private-file restore failures, fault-injected atomic commit
and preservation of prior valid data. Evaluate encryption separately.
Preserve actual APK and Robolectric/host gates. CP-0003C DEFERRED;
CP-0004A/B/C incomplete, physical RF/phone/account work unverified.

## Resume rule""", "software next")
track = re.sub(r"\n## CP-0009G implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", track)
track_path.write_text(track.rstrip() + "\n")
history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009G — opt-in bounded Android archive private-file persistence" not in history:
    history += """

## CP-0009G — opt-in bounded Android archive private-file persistence

Parent: """ + parent + """. Bounded versioned canonical archive wire with
checksum and full original receipt reimport; app-private no-backup opt-in
atomic-replace file store; user-explicit Save/Restore/Delete, fail-closed
corruption handling. Host Kotlin and Robolectric CI + real APK artifact
pass. No encryption, physical phone, power-loss durability, real radio
RF/CAT/USB, account, source authenticity or live propagation proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009G_ANDROID_ARCHIVE_PERSISTENCE_CONTRACT.md.
"""
history_path.write_text(history.rstrip() + "\n")
