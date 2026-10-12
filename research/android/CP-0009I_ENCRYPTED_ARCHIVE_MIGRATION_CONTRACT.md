# CP-0009I — Encrypted archive threat model and versioned migration contract

Verified parent: CP-0009H-ARCHIVE_RECOVERY_FAILURE_INJECTION, version v59.
Scope is **non-executing software contract and host/CI evidence**, NOT encryption.
The Android application continues to save **unencrypted** canonical v1 files.

## Threats and protection boundaries

Assets: archived offline reports containing time/location/callsign information, checksum receipts, eventual encrypted archive bytes, keys and deletion consent. Threats include local byte copying, lost devices, malicious file replacement, truncated storage, replay/downgrade, key invalidation, process interruption and unauthorized plaintext retention.

Proposed goal: encryption at rest against simple copying of a protected app-private file while the device is locked. An unlocked device with malware or access to the running app may still decrypt. Encryption cannot authenticate the radio or the origin of upstream reports. Android OS compromise, RAM, UI screenshots, file-system snapshots, RF output, exported plaintext, cloud backups and physically secure deletion remain out of scope. A genuine old AEAD ciphertext can still be replayed: authenticated encryption alone does **not** provide rollback prevention.

## Version and canonical data contract

- Existing **v1**, magic FTX1ARCH and big-endian version 1, uses CP-0009G canonical report receipts plus SHA-256 error detection. It is readable plaintext. Never change its wire format or describe its digest as an authentication tag.
- **v2 reserved**, magic FTX1ENC2 and big-endian version 2, cannot be loaded by current code. A recognizable header does **not** prove that data is an authentic encrypted archive. A future spec must fix exact offsets and bounds for version, AEAD suite, key identifier, nonce, ciphertext length and authentication tag. The entire clear header must be authenticated using AEAD additional authenticated data; the plaintext must be the complete canonical v1 envelope, independently receipt-validated after decryption.
- Proposed AEAD is **AES-256-GCM** with a new unique 96-bit nonce per encryption for each key. A future Android Keystore key must be non-exportable where supported; document unlock policy, software-keystore fallback, backup portability and permanent key loss before implementation. Hardware-backed key availability cannot be presumed.
- Future encrypted envelope maximum is the v1 cap plus 4096 bytes; version, nonce policy, cipher suite and metadata field lengths must be frozen and tested in CP-0009J before encryption is enabled. Reject unsupported versions, malformed lengths, failed tags, missing keys and unknown suites. No downgrade to plaintext when encrypted restore fails.

## Ordered migration and failure policy

1. No automatic migration at startup, on Save, on Restore, on rotation or in background. Require separate informed user consent and plainly warn that current archive v1 is unencrypted.
2. Validate and bound the **existing** v1 file with the real CP-0009G canonical decoder, before any staging. Corrupt/oversized input is retained unchanged for explicit recovery; never overwrite or auto-delete it.
3. Future executor must confirm Keystore access (a simulated Boolean in CP-0009I is not proof), allocate unique nonce, encrypt an authenticated header, independently decrypt and validate all canonical receipts before attempting persistence. Never log keys or plaintext.
4. Write v2 to its own app-private no-backup filename; sync staged bytes, request same-directory atomic replacement, and read back/decrypt/validate. No non-atomic fallback. Keep v1 intact through all failed attempts; never claim proven crash/power-loss durability without physical testing, including directory fsync behavior.
5. If both v1 and v2 exist after interruption, never silently restore plaintext or choose an older version. Present explicit conflict and recovery choices after validation. Deleting the old plaintext file must be a **separate, explicit action** and is not a secure erase. Old copies may persist in backups, device snapshots and flash wear-leveling remnants.
6. Rotation must authenticate/decrypt current v2, provision a new key and nonce, stage/read-back-verify new ciphertext, then separately retire the old key only after commit. Key loss or invalidation rejects rather than silently resetting, overwriting or downgrading archives.
7. Downgrade/export to unencrypted v1 is **not** a failure fallback; if ever added, it requires its own explicit user action and warnings. AEAD does not prevent replay of earlier valid encrypted files without an independent trusted monotonic version/counter.

## CP-0009I executable evidence

The pure Kotlin EncryptedArchiveMigrationContract classifies **validated** legacy-clear v1, corrupt v1, unknown/truncated input, bounded oversized input and reserved-v2-unreadable. No crypto APIs, Android I/O or key writes are present. The planner requires explicit opt-in, simulated key availability and atomic replace capability, but deliberately ends with ENCRYPTED_EXECUTOR_NOT_IMPLEMENTED even when all simulated prerequisites are true.

Deterministic tests cover v1 round-trip, checksum tamper, forged version, unknown format, reserved future marker, oversize, consent, key loss, atomic support, automatic rotation/downgrade lockout and unchanged original bytes. Tests provide a design boundary, **not actual encryption or origin authentication**.

Preserve CP-0009H Robolectric fault/recovery tests, live APK package build/manifest/artifact verification, all inherited host CI, premerge immutable snapshot dry-run and merged-main checkpoint finalizer. No Galaxy S23 Ultra, FTX-1, CAT/USB/audio, RF or real credentials testing has been performed.

## Next GitHub/CI-only checkpoint

CP-0009J — Android Keystore-backed authenticated encrypted archive implementation and deterministic bad-tag/key-rotation/failure-recovery tests. This requires a fixed v2 byte layout with test vectors and explicit opt-in; no device security claim without real physical tests.

OWNER OVERRIDE: CP-0003C DEFERRED until explicit "resume CP-0003C". CP-0004A/B/C remain incomplete.
