# CP-0009K — Encrypted archive key lifecycle and replay-recovery contract

Verified parent: CP-0009J-AUTHENTICATED_ARCHIVE_ENCRYPTION, v61.
Status: **implementation branch, not a verified checkpoint**.

The CP-0009J V2 archive stores AES-GCM ciphertext under a fixed Android
Keystore alias with a stable non-secret key identifier. It can authenticate
content under that key but cannot distinguish replay of a previously genuine
ciphertext, nor can it recover a permanently lost or invalidated key.

## Key lifecycle contract

- Current key alias is not a hardware-backed security claim. Hardware/StrongBox
  availability and usability on the owner's S23 Ultra remain unverified.
- Rotation must use a DIFFERENT versioned Keystore alias/key ID, a fresh key,
  and provider-generated unique GCM nonces. Never rotate in place or overwrite
  the only valid encrypted bytes during preparation.
- Rotation is an explicitly confirmed user operation. Validate the original
  V2 AEAD tag and canonical report receipts before staging a new ciphertext.
  Provision a new key only after consent. Write to a distinct no-backup staged
  file, sync, authenticate/read back under the new key, then atomically commit.
  Retain the old ciphertext and old key through verification and recovery;
  retiring an old key is a separate explicit operation after commit.
- If key access fails, preserve original ciphertext byte-for-byte and display
  an explicit irrecoverability warning. Never create a replacement key on
  RESTORE; never silently downgrade to plaintext V1, even when V1 exists.
- After interrupted rotation, two valid ciphertext copies may coexist. Do
  not silently prefer by timestamp, file naming or unchecked metadata. Require
  deliberate user review and independent authentication of both candidates.
  Failure injection must prove preservation of original file and cleanup of
  staged files before any real rotation executor is promoted.
- There is no secure file or flash erase guarantee, and Android Keystore key
  deletion does not delete old user exports, backups or memory remnants.

## Replay and rollback detection boundary

AES-GCM authentication proves ciphertext was constructed under the selected
key, not that it is the latest ciphertext. A malicious actor with access to an
older authentic V2 file can restore those bytes; decrypt may still succeed.
Local mutable timestamps, sequence counters, filenames, or an encrypted
counter stored inside the same roll-backable file are insufficient as a
trusted monotonic witness. An external trusted monotonic witness or equivalent
nonrollbackable platform state must be designed and validated before saying
"replay-protected". Android Keystore alone does NOT universally expose a
monotonic archive counter. Any unsupported/unavailable witness produces an
explicit "freshness not verified" state, not silent acceptance as current.

## CP-0009K executable contract

The new **pure Kotlin, no-I/O** policy classifies loss of key, failed AEAD
authentication, competing encrypted candidates, missing rotation consent,
missing new distinct key, missing atomic stage, and unverified readback.
Even when every simulated prerequisite passes, the planner reports
ROTATION_EXECUTOR_NOT_IMPLEMENTED. Replay evaluation returns
NO_TRUSTED_MONOTONIC_WITNESS or TRUSTED_WITNESS_INTEGRATION_NOT_IMPLEMENTED;
it NEVER asserts replay protection from AES-GCM alone. Host tests exercise
each failure and conflict. No real rotation or key retirement executes.

## Remaining exit requirements

Complete Android-friendly policy wiring/tests without making automatic
migration or rollback decisions. Retain Android APK, Robolectric encryption
tests, real packaged APK, all inherited host regression jobs, premerge
snapshot dry-run and merged-main immutable finalizer before CP-0009K is
called durable. Later CP-0009L may implement user-confirmed key rotation once
a safe recovery transaction and device Keystore policy are specified.

OWNER: CP-0003C DEFERRED until explicit "resume CP-0003C"; CP-0004A/B/C
incomplete. No phone/radio, RF, CAT/USB, physical Keystore, LoTW credentials,
hardware rollback witness or account verification.
