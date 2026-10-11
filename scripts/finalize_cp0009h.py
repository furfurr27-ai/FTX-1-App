#!/usr/bin/env python3
"""CP-0009H: durable recovery handoff only after merged-main CI PASS."""
import os
import pathlib
import re

root = pathlib.Path(".")
run = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009H-ARCHIVE_RECOVERY_HARDENING"
parent = "CP-0009G-ANDROID_ARCHIVE_PERSISTENCE_CONTRACT"
nxt = "CP-0009I — bounded local archive recovery protocol and crash-consistency research"

def replace_once(text, pattern, result, name):
    new, count = re.subn(pattern, lambda _: result, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Cannot safely replace " + name)
    return new

(root / "VERSION").write_text("v59-archive-recovery-hardening\n")
readme_file = root / "README.md"
readme = readme_file.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if not match:
    raise SystemExit("Verified inherited regression list absent")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent: """ + parent + """ / v58.

CP-0009H hardens the explicitly opted-in app-private synthetic archive
against corrupted files and precommit save failures. Robolectric tests
inject staged-sync failure and unavailable atomic rename, requiring exact
prior saved bytes and canonical receipts to remain intact and no leftover
temporary files. Bounded streamed reads reject changed or oversized files.
Malformed stored data fails closed and the Android screen preserves all
current synthetic history, with explicit recovery/delete instructions.

The successful staged-write/atomic rename checks are JVM Android tests,
NOT physical-device crash consistency, verified directory fsync, encryption
or any promise that every hardware failure preserves data. No Android
permissions, live propagation, CAT/PTT/RF, LoTW accounts or device tests.

CP-0003C remains DEFERRED. CP-0004A/B/C incomplete.

Host/CI gates:

- CP-0009H Robolectric staged-save and rename fault-injection tests: **PASS**.
- CP-0009H corrupted private archive recovery UI and bounded-read checks: **PASS**.
- CP-0009H real debug APK plus private/no-permission static safety audit: **PASS**.
""" + inherited + """

Evidence:

- research/android/CP-0009H_ARCHIVE_RECOVERY_HARDENING.md
- app/src/main/kotlin/dev/n0png/fieldops/app/OptInArchiveStore.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/OptInArchiveStoreTest.kt
- app/src/test/java/dev/n0png/fieldops/app/ArchiveHistoryActivityTest.java
- .github/workflows/build-android-apk.yml
- CP-0009H main finalizer run: """ + run + """

### Evidence boundary

Robolectric/JVM synthetic data, Android APK packaging and inherited regression
evidence only. No real filesystem power-loss guarantee, encryption, physical
phone, USB, RF or account verification.

### Inherited verified ancestry

""" + parent + """ and older verified snapshots retained.

"""
readme = replace_once(readme, r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_once(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + nxt, "README next track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
                "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + nxt + """.**

1. Design and CI-test a bounded, opt-in local archive recovery protocol
   for interrupted writes and orphaned staging without silent data adoption.
2. Distinguish integrity validation from OS/device power-loss durability.
   Encryption is research-only until actual key-managed code is verified.
3. Retain full real APK, Robolectric and inherited host regression gates.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete; skip physical/account work.

"""
readme = replace_once(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next action")
readme = re.sub(r"\n## CP-0009H implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", readme)
readme_file.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C incomplete. Skip phone/radio/USB/RF, actual accounts,
certificates and all physical or manual hardware validation.

## Next software checkpoint

**""" + nxt + """**

Harden interrupted archive writes and orphan-stage recovery without silent
restore; investigate crash consistency limits and separately scoped encryption.
Keep opt-in requirement, original canonical receipts, bounded private storage,
Robolectric UI tests, actual APK artifact and inherited host regressions.
No physical device or real radio/account proof.
""")

track_file = root / "SOFTWARE_TRACK.md"
track = track_file.read_text()
track = replace_once(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "software baseline")
track = replace_once(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + nxt + """

Next: CI-test staged/orphan recovery, bounded rollback and explicit consent.
Separate checksums from source authentication, encryption and power durability.
Continue debug APK/host regressions. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule""", "software next")
track = re.sub(r"\n## CP-0009H implementation branch \(not checkpointed\)\n[\s\S]*$", "\n", track)
track_file.write_text(track.rstrip() + "\n")

hist_file = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = hist_file.read_text()
if "## CP-0009H — fail-closed archive recovery and fault injection" not in history:
    history += """

## CP-0009H — fail-closed archive recovery and fault injection

Parent: """ + parent + """. Robolectric simulated Android pre-rename faults
preserve prior canonical saved bytes and receipts. Streamed reads are bounded,
corrupt saved data is rejected without screen-state mutation, and user-explicit
delete/retry guidance is shown. Real APK and inherited host CI passed.
No power-loss directory durability, encryption, on-device testing, real
radio CAT/USB/RF or external accounts were verified. CP-0003C DEFERRED;
CP-0004A/B/C incomplete.

Finalizer run """ + run + """.
Evidence: research/android/CP-0009H_ARCHIVE_RECOVERY_HARDENING.md.
"""
hist_file.write_text(history.rstrip() + "\n")
