# CP-0009H — Fail-closed archive recovery UX and file-commit fault testing

Parent verified immutable checkpoint: CP-0009G-ANDROID_ARCHIVE_PERSISTENCE_CONTRACT / v58, main cc5e734404028a794f2b7ed57c2b37a648bfd7ca.

## GitHub/CI deliverables

1. Introduce internal, test-only precommit fault points in OptInArchiveStore: AFTER_STAGED_FILE_SYNC and ATOMIC_RENAME_UNAVAILABLE. No production user control can inject these failures. Simulated exceptions occur before atomic replacement, and temporary staging is deleted in a finally block. Each fault test compares exact previous saved bytes, canonical reimport/receipt and absence of stale temp files.
2. Bound the actual streamed read rather than trusting a file length checked before opening it. Limit byte accumulation to PropagationArchiveStorageCodec.MAX_ENVELOPE_BYTES even if the private file grows between checks. On overflow or malformed input, throw and return NO imported archive.
3. Robolectric corruption scenario: write malformed saved bytes in app-private no-backup storage, attempt explicit Restore, verify previous on-screen synthetic reports remain unchanged and source file not silently rewritten. Show actionable fail-closed notice explaining that the user must explicitly delete the invalid file. Test subsequent explicit Delete and missing-store result.
4. Preserve explicit opt-in Save/Restore/Delete, absence of Android permissions, canonical report checksum and full typed reimport, deterministic host tests, APK build/aapt/ZIP/sha256 upload, and all inherited 42 host regressions.

## Failure semantics and limits

- A failure injected *before* atomic rename preserves the previously committed file. The tests do not establish that all possible real-world exceptions, OS crashes or power failures are recoverable.
- Atomic replacement is supported only if the platform/filesystem offers ATOMIC_MOVE; there is intentionally no non-atomic fallback.
- FileOutputStream.fd.sync() synchronizes the staged file, but there is no separately proven parent directory fsync or stable-media ordering guarantee. No claim of crash-consistent power-loss durability on a Galaxy S23 Ultra.
- An invalid report's digest, envelope checksum or canonical type must fail closed rather than repair the file silently. A checksum is not encryption, authentication or a source signature.
- The saved file remains unencrypted. Android noBackupFilesDir and allowBackup=false are controls against ordinary backup; they are not cryptographic protection. Encryption requires a separately scoped implementation and secret/key management review.
- No real radio, USB CAT/audio/PTT, LoTW account, user credentials, physical device interaction, or RF testing. Robolectric is JVM simulated Android UI.

## Forward path

CP-0009I — design and CI test a bounded local persistence recovery protocol (journal or proven atomic durability alternative), including recovery of abandoned staging and crash consistency limits, while retaining explicit user consent and no automatic restore. Encryption may be researched but must not be described as implemented until actual code and tests exist.

Owner exception: CP-0003C DEFERRED until explicitly told "resume CP-0003C"; CP-0004A/B/C incomplete.
