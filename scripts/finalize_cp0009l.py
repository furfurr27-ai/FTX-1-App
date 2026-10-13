#!/usr/bin/env python3
"""CP-0009L: finalize only after exact merged-main Android and host CI succeeds."""
import os
import pathlib
import re

root = pathlib.Path(".")
run_id = os.environ["FINALIZE_RUN_ID"]
checkpoint = "CP-0009L-EXPLICIT_ENCRYPTED_KEY_ROTATION"
parent = "CP-0009K-ENCRYPTED_KEY_LIFECYCLE_REPLAY_CONTRACT"
next_label = "CP-0009M — rotated archive conflict inspection and key retirement safety"

def one(value, pattern, replacement, label):
    result, count = re.subn(pattern, lambda _: replacement, value, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Expected exactly one " + label + ": " + str(count))
    return result

(root / "VERSION").write_text("v63-explicit-encrypted-key-rotation\n")

readme_path = root / "README.md"
readme = readme_path.read_text()
m = re.search(r"Host/CI gates:\n\n(.*?)\nEvidence:", readme, re.S)
if m is None:
    raise SystemExit("Cannot find inherited verified regression ancestry")
inherited = m.group(1).strip()

baseline = """## Verified durable baseline

**Latest verified checkpoint:** """ + checkpoint + """

Parent durable checkpoint: """ + parent + """ / v62.

CP-0009L adds an **explicit, ONE-SHOT distinct-key ROTATED COPY**
of an existing authenticated Android V2 encrypted archive. The original
encrypted V2 file and Android Keystore key are NEVER deleted, overwritten,
invalidated, replaced, or automatically selected away. The old V1 file
remains UNENCRYPTED until independently deleted by an explicit user action.

An entirely separate Android Keystore AES-256-GCM alias and distinct
16-byte nonsecret key identifier protect a separate FTX1ENC2 file in
app-private no-backup storage. Source V2 must first decrypt/authenticate
and its canonical receipts validate BEFORE the new key is provisioned.
After fresh provider-nonce AES-GCM encryption, new ciphertext is staged in
the same directory and fsynced, decrypted/read-back/receipt validated under
the rotated key and published by exclusive atomic hard link (no overwrite).
Failure to support hard links fails closed rather than falling back to
an unsafe copy; physical Galaxy S23 Ultra behavior remains UNTESTED.

Separate Android rotation opt-in, create, restore and delete actions
never auto-restore, auto-migrate, downgrade, retire a key or secure-erase
the original. Robolectric software-key tests check new key, independent
ciphertext, old-file retention, bad source tag/key, corrupted rotated copy,
missing key, existing-file conflict, oversized input, and fault-injected
staging/prepublish cleanup. Existing V2/V1 and UI regressions stay green.

**Security limits:** This is an explicitly selected encrypted copy, not
automatic active-archive/key cutover. Original key remains usable; old
ciphertext and unencrypted V1 remain. AES-GCM does NOT prevent replay of
older genuine ciphertext; no trusted monotonic freshness witness exists.
No secure flash erase, on-device hard-link support, hardware-backed key,
actual Keystore interoperability/power-loss durability, station provenance,
real Galaxy S23 Ultra, FTX-1 CAT/USB/RF or account/certificate tests are
claimed. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Host/CI gates:

- CP-0009L encrypted rotation software-key Robolectric failure isolation: **PASS**.
- CP-0009L separate Android opt-in and no-downgrade UX: **PASS**.
- CP-0009L actual Android APK packaging and complete inherited host CI: **PASS**.
""" + inherited + """

Evidence:

- research/android/CP-0009L_EXPLICIT_ENCRYPTED_KEY_ROTATION.md
- app/src/main/kotlin/dev/n0png/fieldops/app/ExplicitEncryptedArchiveRotation.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/ExplicitEncryptedArchiveRotationTest.kt
- app/src/test/kotlin/dev/n0png/fieldops/app/ExplicitRotationActivityTest.kt
- scripts/test_android_package_contract.py
- .github/workflows/cp0009l-explicit-key-rotation.yml
- .github/workflows/finalize-cp0009l.yml
- CP-0009L merged-main finalizer run: """ + run_id + """

### Evidence boundary

Only JVM and Robolectric encrypted-rotation simulation/Android build proof.
Hardware-backed keys, hard-link filesystem compatibility and power-loss
safety require physical target-device validation; anti-replay and key
retirement are NOT implemented.

### Inherited verified ancestry

""" + parent + """ and all earlier immutable checkpoints remain intact.

"""
readme = one(readme, r"## Verified durable baseline\n.*?\n## Execution tracks and hardware-gated deferrals",
             baseline + "## Execution tracks and hardware-gated deferrals", "README baseline")
readme = one(readme, r"\*\*Active software track:\*\* [^\n]+",
             "**Active software track:** " + next_label, "README track")
readme = re.sub(r"- Current Git source baseline: [^\n]+",
                "- Current Git source baseline: " + checkpoint, readme, count=1)
next_section = """## Current exact next action

**""" + next_label + """.**

1. Implement explicit authenticated inspection of competing original and
   rotated archives, including key-loss and corrupt-file recovery states.
2. Define SAFE optional old-key retirement prerequisites; do not delete
   original key/ciphertext automatically or claim secure flash deletion.
3. Continue to warn that AES-GCM cannot prove recency without a trusted
   independent monotonic witness. Preserve original V1/V2 opt-ins,
   Robolectric, APK, all inherited host and immutable checkpoint gates.
4. CP-0003C DEFERRED; CP-0004A/B/C incomplete. No physical device/RF claims.

"""
readme = one(readme, r"## Current exact next action\n.*?\n## README maintenance contract",
             next_section + "## README maintenance contract", "README next action")
readme = re.sub(r"\n## CP-0009L implementation branch — not checkpointed\n[\s\S]*$",
                "\n", readme)
readme_path.write_text(readme.rstrip() + "\n")

(root / "NEXT_ACTION.md").write_text("""# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**""" + next_label + """**

CP-0009L created an opt-in distinct-key ROTATED COPY, not automatic active
key cutover or key retirement. Old V2 and its key remain intact; old V1
remains plaintext. Next expose competing archive authentication states
safely, document optional key retirement constraints and test failures.
Do not claim replay protection or physical Android Keystore proof.
""")

track_path = root / "SOFTWARE_TRACK.md"
track = track_path.read_text()
track = one(track, r"## Latest verified durable parent\n\n.*?\n\n## Deferred but incomplete hardware/account work",
            "## Latest verified durable parent\n\n" + checkpoint +
            "\n\n## Deferred but incomplete hardware/account work", "track baseline")
track = one(track, r"## Active software checkpoint\n\n.*?\n## Resume rule",
            """## Active software checkpoint

""" + next_label + """

CP-0009L added explicit first-rotation encrypted COPY using a distinct
Keystore alias, staged authenticated readback and exclusive link creation.
Original V2/key, unencrypted V1 and synthetic demo constraints remain.
Robolectric/Android APK verified by CI; no physical Android Keystore,
filesystem/power-loss, trusted anti-replay, automatic key cutover or
retirement proof. CP-0003C DEFERRED; CP-0004A/B/C incomplete.

## Resume rule""", "track next")
track = re.sub(r"\n## CP-0009L work in progress — not durable\n[\s\S]*$", "\n", track)
track_path.write_text(track.rstrip() + "\n")

history_path = root / "checkpoints/CHECKPOINT_HISTORY.md"
history = history_path.read_text()
if "## CP-0009L — explicit distinct-key encrypted rotation copy" not in history:
    history += """

## CP-0009L — explicit distinct-key encrypted rotation copy

Parent: """ + parent + """. Separate opt-in Android encrypted rotation
COPY to distinct Keystore alias/identifier and app-private file.
Original authenticated source validated first; new candidate staged/synced,
authenticated/read back and exclusively published via hard link.
Original V2/key and V1 untouched. Robolectric fault/lost-key/corrupt/conflict
tests and Android APK/inherited host regressions passed. No automatic
cutover, key retirement, trusted anti-replay, secure erase or physical
Galaxy S23 Ultra/FTX-1/Android filesystem/account/RF proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.

Finalizer: """ + run_id + """.
Evidence: research/android/CP-0009L_EXPLICIT_ENCRYPTED_KEY_ROTATION.md.
"""
history_path.write_text(history.rstrip() + "\n")
