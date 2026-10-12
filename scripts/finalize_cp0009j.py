#!/usr/bin/env python3
"""Promote CP-0009J only after merged-main Android/crypto/inherited CI succeeds."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009J-AUTHENTICATED_ARCHIVE_ENCRYPTION"
parent = "CP-0009I-ENCRYPTED_ARCHIVE_MIGRATION_CONTRACT"
next_label = "CP-0009K — encrypted archive key lifecycle, rotation and replay recovery"

def replace_one(content, pattern, replacement, name):
    result, count = re.subn(pattern, lambda _: replacement, content, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Cannot locate exactly one " + name + ": " + str(count))
    return result

(root / "VERSION").write_text("v61-authenticated-archive-encryption\n")
readme_path = root / "README.md"
readme = readme_path.read_text()
m = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if m is None:
    raise SystemExit("Inherited verified CI gate ancestry missing")
inherited = m.group(1).strip()
baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Verified parent: """ + parent + """ / v60.

CP-0009J implements a **separate opt-in** encrypted Android app-private
archive (FTX1ENC2). A 48-byte V2 header is authenticated as AES-256-GCM AAD,
a fresh random 96-bit nonce is generated for every encryption, and the
decrypted canonical V1 envelope is receipt-validated before use.
New AES-256 keys are provisioned through Android Keystore only on explicit
save; encrypted RESTORE NEVER creates a replacement key.

A separate V2 file is atomically staged/synced and written under
noBackupFilesDir. Existing plaintext V1 files are retained and remain
**UNENCRYPTED** until the user separately chooses deletion (not secure wipe).
A separate user-controlled V1-to-V2 COPY validates V1 before encrypting;
wrong-key, malformed ciphertext and lost-key failures never silently fall
back to V1. Save and restore are user actions requiring per-session opt-in.

Kotlin JVM host crypto tests cover wrong keys, GCM tag/header tamper,
version/suite errors, length bounds and canonical validation; Robolectric
API35 tests inject software keys and exercise staged-write rollback,
corrupt/oversized files, missing keys and UI controls.

**Evidence boundaries:** JVM crypto tests and Robolectric simulations do
not prove on-device Android Keystore behavior, hardware-backed key storage,
Android physical flash/directory-fsync power-loss durability, device
compromise resistance, unique-nonce guarantees over unlimited writes,
anti-rollback against older valid ciphertext, genuine source provenance,
radio, CAT/USB/audio, PTT/RF, accounts or real station operation. Existing
local plaintext V1 copies remain unprotected; key rotation is future work.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Host/CI gates:

- CP-0009J AES-GCM authenticated V2 envelope and corruption tests: **PASS**.
- CP-0009J Robolectric explicit encrypted store and failure-isolation: **PASS**.
- CP-0009J Android opt-in encrypted/legacy UI and actual APK artifact: **PASS**.
""" + inherited + """

Evidence:

- research/android/CP-0009J_AUTHENTICATED_ARCHIVE_ENCRYPTION.md
- core/src/main/kotlin/dev/n0png/fieldops/core/propagation/AuthenticatedArchiveEnvelopeV2.kt
- app/src/main/kotlin/dev/n0png/fieldops/app/OptInEncryptedArchiveStore.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/OptInEncryptedArchiveStoreTest.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/EncryptedArchiveActivityTest.kt
- scripts/test_authenticated_archive_envelope_v2.sh
- CP-0009J merged-main finalizer: """ + run_id + """

### Evidence boundary

No on-device cryptography, radio, CAT/USB, power-loss or provenance
authenticity proof. Existing V1 archive storage remains plaintext.

### Inherited verified ancestry

""" + parent + """ and all earlier immutable checkpoints remain intact.

"""
readme = replace_one(readme,
    r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
    baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = replace_one(readme, r"\*\*Active software track:\*\* [^\n]+",
    "**Active software track:** " + next_label, "README active software track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
    "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + next_label + """.**

1. Implement explicit key rotation/recovery policy with fresh Keystore key,
   read-back authenticated verification and retention of original ciphertext
   until safe user-acknowledged deletion. No automatic downgrade.
2. Add deterministic tests for missing/wrong/invalidated keys, repeated
   encryption, old valid ciphertext replay risk and interrupted rotation.
3. Preserve encrypted and plaintext archive separate opt-in, Android APK,
   Robolectric, canonical receipts, inherited host gates and checkpoint proof.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete; no device/radio claims.

"""
readme = replace_one(readme,
    r"## Current exact next action\n.*?\n## README maintenance contract",
    next_section + "## README maintenance contract", "README next action")
readme = re.sub(
    r"\n## CP-0009J implementation branch \(not checkpointed\)\n[\s\S]*$",
    "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009J provides verified *host/Android-simulated* AES-GCM V2 storage with
user opt-in, but no physical Android Keystore proof. Old V1 plaintext still
exists until independently deleted. Next implement explicit key rotation
and lost-key/replay recovery safeguards. Preserve all APK/CI regression gates.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = replace_one(track,
    r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
    "## Latest verified durable parent\n\n" + checkpoint +
    "\n\n## Deferred but incomplete hardware/account work", "track baseline")
track = replace_one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
    """## Active software checkpoint

""" + next_label + """

CP-0009J added per-session opt-in AES-GCM V2 app-private storage, distinct
from retained plaintext V1. Android Keystore provider exists, with
Robolectric software-key tests only. Next test explicit key rotation, lost
key and replay/downgrade recovery on GitHub/CI. Hardware remains unproven.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule""", "track next software checkpoint")
track = re.sub(
    r"\n## CP-0009J implementation branch — not checkpointed\n[\s\S]*$",
    "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009J — Android authenticated encrypted archive" not in history:
    history += """

## CP-0009J — Android authenticated encrypted archive

Parent: """ + parent + """. AES-256-GCM V2 encrypts and verifies canonical
V1 archive receipts. Android Keystore is invoked only from explicit
encrypted-save actions, while restore never generates keys. Separate V2
app-private bytes leave legacy V1 plaintext unchanged. Robolectric tests
cover lost/wrong key, corruption, size bounds and precommit injection.
No hardware-backed key claim, physical device crash/RF/CAT/USB tests,
account validation, secure plaintext erase or replay resistance.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009J_AUTHENTICATED_ARCHIVE_ENCRYPTION.md.
"""
history_path.write_text(history.rstrip() + "\n")
