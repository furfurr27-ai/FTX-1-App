#!/usr/bin/env python3
"""CP-0009I: only publish an immutable handoff AFTER merged-main CI is green."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009I-ENCRYPTED_ARCHIVE_MIGRATION_CONTRACT"
parent = "CP-0009H-ARCHIVE_RECOVERY_FAILURE_INJECTION"
next_label = "CP-0009J — authenticated encrypted archive implementation"

def one(content, pattern, replacement, label):
    updated, count = re.subn(pattern, lambda _: replacement, content,
                             count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Missing " + label + ": " + str(count))
    return updated

(root / "VERSION").write_text("v60-encrypted-archive-migration-contract\n")

readme_path = root / "README.md"
readme = readme_path.read_text()
match = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if match is None:
    raise SystemExit("Missing inherited verified host CI ancestry")
inherited = match.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v59.

CP-0009I defines a **NON-EXECUTING** versioned migration and threat-model
contract for optional future Android app-private encrypted archives. Valid
plaintext v1 is accepted only after canonical receipt-validated decode;
malformed, oversized and reserved v2 inputs fail closed. Simulated migration
preflight requires explicit user opt-in, key availability and atomic file
replacement but ALWAYS ends in ENCRYPTED_EXECUTOR_NOT_IMPLEMENTED.

The production Android archive is STILL UNENCRYPTED. No encryption key
is provisioned, no AES-GCM ciphertext is written, no migration occurs, no
fallback/downgrade or key rotation executes. AES-GCM/Keystore integration,
v2 exact wire fields and tamper/rotation tests are future CP-0009J work.

CI proves these pure Kotlin decisions, legacy canonical safety, Android
Robolectric archived-store recovery, APK packaging and inherited host
regressions. It does not prove confidentiality, hardware-backed keys,
origin authenticity, crash/power-loss durability, physical Galaxy S23
Ultra behavior or live FTX-1 radio CAT/USB/RF/account operation.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Host/CI gates:

- CP-0009I fail-closed non-executing migration planner host tests: **PASS**.
- CP-0009I original canonical archive integrity and bounds: **PASS**.
- CP-0009I Android APK/Robolectric and inherited host matrix: **PASS**.
""" + inherited + """

Evidence:

- research/android/CP-0009I_ENCRYPTED_ARCHIVE_MIGRATION_CONTRACT.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/EncryptedArchiveMigrationContract.kt
- core/src/test/kotlin/dev/n0png/fieldops/core/EncryptedArchiveMigrationContractTests.kt
- scripts/test_encrypted_archive_migration_contract.sh
- .github/workflows/cp0009i-archive-encryption-contract.yml
- CP-0009I merged-main finalizer: """ + run_id + """

### Evidence boundary

Pure Kotlin host classification and design, real APK packaging and
Robolectric simulations; **NOT** implemented or verified encryption.

### Inherited verified ancestry

""" + parent + """ and earlier immutable checkpoints remain ancestry.

"""
readme = one(readme, r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
             baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = one(readme, r"\*\*Active software track:\*\* [^\n]+",
             "**Active software track:** " + next_label, "README track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
                "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + next_label + """.**

1. Freeze an independently testable authenticated v2 wire schema including
   header AAD, key identifiers, nonce uniqueness and deterministic vectors.
2. Implement a separate explicit opt-in Android Keystore/AES-256-GCM path,
   staged atomic save, decrypt/receipt validate, bad-key/tag recovery.
3. Preserve plaintext v1 if migration fails; never automatically downgrade
   encrypted files. Retain APK, Robolectric, all inherited host checks.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete. No device/radio claims.

"""
readme = one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
             next_section + "## README maintenance contract", "README next")
readme = re.sub(r"\n## CP-0009I implementation branch \(not checkpointed\)\n[\s\S]*$",
                "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009I reserved an encrypted v2 header and an always-denying migration
preflight, but DID NOT implement encryption. Next specify authenticated
v2 wire, Android Keystore lifetime, nonce allocation, stage/rollback
and key-loss UX, and implement in GitHub/CI with tamper tests. Never
assert hardware backing or encryption until the real code and CI prove it.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = one(track, r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
            "## Latest verified durable parent\n\n" + checkpoint +
            "\n\n## Deferred but incomplete hardware/account work", "track baseline")
track = one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
            """## Active software checkpoint

""" + next_label + """

CP-0009I verified NON-EXECUTING pure Kotlin migration checks only. Build
real opt-in authenticated encrypted storage in CP-0009J with Keystore
key lifecycle and robust failure/decryption tests. Current file remains
plaintext. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule""", "track next")
track = re.sub(r"\n## CP-0009I implementation branch \(not checkpointed\)\n[\s\S]*$",
               "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009I — encrypted archive threat and migration contract" not in history:
    history += """

## CP-0009I — encrypted archive threat and migration contract

Parent: """ + parent + """. Host Kotlin recognizes validated plaintext
v1 and rejects corrupt, oversized, unknown and reserved-v2 candidates.
All simulated migration preflight paths forbid actual encrypted writes;
consent, key and atomic-replace checks are design gates, not crypto proof.
Threat model documents future AEAD, Keystore, rotation, lost keys, old
plaintext retention, replay and downgrade boundaries. Android persistence
remains unencrypted. No physical Galaxy S23 Ultra, FTX-1, CAT/USB, RF,
real account/certificate or hardware key evidence.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009I_ENCRYPTED_ARCHIVE_MIGRATION_CONTRACT.md.
"""
history_path.write_text(history.rstrip() + "\n")
