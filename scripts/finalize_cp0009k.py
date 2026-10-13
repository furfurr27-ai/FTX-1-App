#!/usr/bin/env python3
"""CP-0009K: promote non-executing key lifecycle/replay recovery AFTER green main CI."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009K-ENCRYPTED_KEY_LIFECYCLE_REPLAY_CONTRACT"
parent = "CP-0009J-AUTHENTICATED_ARCHIVE_ENCRYPTION"
next_label = "CP-0009L — explicit encrypted key-rotation transaction design and implementation"

def one(value, pattern, replacement, name):
    result, count = re.subn(pattern, lambda _: replacement, value, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Missing unique " + name + ": " + str(count))
    return result

(root / "VERSION").write_text("v62-encrypted-key-lifecycle-replay-contract\n")

readme_path = root / "README.md"
readme = readme_path.read_text()
m = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if m is None:
    raise SystemExit("Cannot recover inherited verified regression ancestry")
inherited = m.group(1).strip()

baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v61.

CP-0009K is a **NON-EXECUTING** encrypted key lifecycle and ciphertext
replay recovery policy. Pure Kotlin decisions explicitly reject damaged
ciphertext, unavailable keys, conflicting authenticated copies, absent
consent, non-distinct replacement keys, non-atomic staging and missing
authenticated read-back. Every simulated eligible rotation still returns
ROTATION_EXECUTOR_NOT_IMPLEMENTED. No key-rotation writer or key retirement
is implemented.

AES-GCM authenticity is not evidence of recency: an older genuine
ciphertext may decrypt successfully. The policy reports
NO_TRUSTED_MONOTONIC_WITNESS, not replay protection; even a synthetic
witness flag yields TRUSTED_WITNESS_INTEGRATION_NOT_IMPLEMENTED.
Android offline archive UI explicitly warns FRESHNESS NOT VERIFIED. An
encrypted restore labels the same limitation after canonical validation.

Inherited CP-0009J encrypted V2 opt-in save/restore/migration COPY remains
unchanged. V1 archives remain UNENCRYPTED until separately deleted.
No automatic migration/downgrade, key rotation, or secure erase occurs.
Host tests and Robolectric/API35 prove the simulated decisions and visible
warnings, not key hardware, device power-loss durability, trusted monotonic
counter, archival origin, real S23 Ultra behavior, FTX-1/CAT/USB/RF or
account/certificate operation.

CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Host/CI gates:

- CP-0009K no-write key lifecycle/replay classification tests: **PASS**.
- CP-0009K Android freshness warning and no-rotation static checks: **PASS**.
- CP-0009K real Android debug APK and Robolectric regressions: **PASS**.
""" + inherited + """

Evidence:

- research/android/CP-0009K_KEY_LIFECYCLE_REPLAY_CONTRACT.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/EncryptedArchiveKeyLifecycleContract.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/EncryptedArchiveKeyLifecycleContractTests.kt
- scripts/test_encrypted_archive_key_lifecycle_contract.sh
- app/src/main/kotlin/dev/n0png/fieldops/app/ArchiveHistoryActivity.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/EncryptedArchiveActivityTest.kt
- .github/workflows/cp0009k-archive-lifecycle-contract.yml
- CP-0009K merged-main finalizer run: """ + run_id + """

### Evidence boundary

No executable key rotation/retirement or verified anti-rollback mechanism;
physical device and radio/security properties remain unverified.

### Inherited verified ancestry

""" + parent + """ and all earlier immutable checkpoints remain intact.

"""
readme = one(readme, r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
             baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = one(readme, r"\*\*Active software track:\*\* [^\n]+",
             "**Active software track:** " + next_label, "README software track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
                "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + next_label + """.**

1. Design and test a distinct new Keystore alias/ID and a retained
   recoverable old ciphertext/key through staged authenticated readback.
2. Implement explicit user-confirmed rotation without automatic downgrade
   or deletion, and fault-inject every stage; never imply AES-GCM itself
   protects against replay without independent trusted monotonic witness.
3. Preserve separate encrypted V2 and unencrypted V1 opt-in, APK,
   Robolectric, prior host regressions, CI and immutable checkpoint proof.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete. No device/radio claims.

"""
readme = one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
             next_section + "## README maintenance contract", "README next")
readme = re.sub(r"\n## CP-0009K implementation branch \(NOT checkpointed\)\n[\s\S]*$", "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009K is a non-executing key lifecycle and replay-risk contract, NOT
implemented rotation or replay protection. Plan a recoverable new-key
transaction and explicit user choices with fully verified ciphertext
readback and original retention. Never imply a local AES-GCM tag or
mutable local version prevents replay. Keep V1 plaintext and V2 separate.
CP-0003C remains deferred.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = one(track, r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
            "## Latest verified durable parent\n\n" + checkpoint +
            "\n\n## Deferred but incomplete hardware/account work", "track parent")
track = one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
            """## Active software checkpoint

""" + next_label + """

CP-0009K proved no-write key lifecycle and replay-risk classification,
with Android warning, inherited V2 encryption, Android package and
Robolectric/regression CI. No key rotation, anti-replay witness,
on-device Keystore hardware or RF proof. CP-0003C DEFERRED; CP-0004A/B/C
incomplete.

## Resume rule""", "track next")
track = re.sub(r"\n## CP-0009K branch in progress — not durable\n[\s\S]*$", "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009K — encrypted key lifecycle and replay-recovery contract" not in history:
    history += """

## CP-0009K — encrypted key lifecycle and replay-recovery contract

Parent: """ + parent + """. Pure Kotlin rejects unverified source, lost
keys, competing copies, absent consent, absent distinct new key, unavailable
atomic staging and missing authenticated readback before key rotation.
Even simulated readiness cannot trigger a writer. AES-GCM authenticity
does not prove freshness or preclude old valid ciphertext replay; Android
UI warns FRESHNESS NOT VERIFIED. No rotation or trusted anti-replay
witness is present. No physical phone/radio, Android hardware Keystore,
flash crash-consistency, account or RF validation is claimed.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009K_KEY_LIFECYCLE_REPLAY_CONTRACT.md.
"""
history_path.write_text(history.rstrip() + "\n")
