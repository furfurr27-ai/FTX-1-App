# CP-0009H — Private archive recovery and fault injection

Verified parent: CP-0009G-ANDROID_ARCHIVE_PERSISTENCE_CONTRACT (v58).

## Changes
- An already-stored archive must pass canonical decode and integrity checks before a later Save can overwrite it. Corrupt or oversized retained files are left untouched and require a separate explicit Delete action before a replacement Save.
- Two pre-commit fault-injection points in the Android private-file store, AFTER_STAGED_SYNC and BEFORE_ATOMIC_REPLACE, permit deterministic simulated I/O failures. The previous valid committed file must remain byte-for-byte unchanged, and staged temp files must be cleaned.
- Existing archive validation and restore use bounded streaming FileInputStream reads, enforcing the envelope cap even if the file grows after a length pre-check. Robolectric verifies oversized files are neither loaded nor overwritten.
- Restore failures preserve the current in-memory screen and retained bytes, with explicit Android error messaging explaining the separate deletion/discard action.
- Robolectric API 35 tests simulate both injected faults and corrupt-file overwrite attempts, and click the Android Save/Restore/Delete controls against a corrupted private archive.
- AndroidManifest remains zero-permission; no network, radio, CAT, USB, microphone, PTT, transmit, or external/cloud archive access is introduced.
- Keep inherited CP-0009G canonical wire tamper tests, CP-0009F navigation, 42 inherited host jobs and real Android APK/aapt/package and artifact verification.

## Limitations
- Tests are Robolectric simulated JVM Android tests and Kotlin host tests, not physical device, process crash, power loss, nor directory fsync guarantees.
- Rename atomicity is requested from the filesystem. The precommit fault hooks prove the prior file survives those injected stages; they do NOT prove power-loss atomicity across all devices/filesystems.
- Saved bytes are NOT encrypted, digitally signed or source-authenticated. Deleting a file is not a secure physical wipe.
- Real station data, FTX-1 radio operation, USB CAT/audio/RF, actual Galaxy S23 Ultra testing and account credentials remain unverified.

## Next unblocked software checkpoint
CP-0009I — encrypted app-private archive design and versioned migration/threat-model contract, GitHub/CI-only. Do not claim encryption exists until implemented and validated.

Owner deferrals: CP-0003C DEFERRED unless explicitly resumed, CP-0004A/B/C incomplete.
