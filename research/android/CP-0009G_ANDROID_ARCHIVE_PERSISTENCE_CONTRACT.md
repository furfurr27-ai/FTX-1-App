# CP-0009G — Explicit opt-in bounded Android offline archive persistence

Verified parent: CP-0009F-ANDROID_ARCHIVE_NAVIGATION_TESTS / v57, main 2d868016dd4e1830c10a2673210c4969c61cb0a3.

## Concrete deliverables
- Canonical, versioned binary storage envelope (PropagationArchiveStorageCodec) for archived offline JSON reports, preserving insertion order and bounded retention policy.
- 4 MiB maximum canonical report corpus and 4 MiB + 8192 bytes maximum envelope. Up to 32 archived records, max 64 MiB retention-policy ceiling. Reject malformed UTF-8, unsupported version, malformed lengths, oversized count/budget, trailing bytes, truncated envelope, checksum changes, corrupt canonical reports, duplicate IDs and unexpected retention eviction.
- SHA-256 verifies storage bytes but does NOT prove authenticity, trust, encryption or provider provenance. Decode independently reimports and revalidates each canonical report receipt using the CP-0008Q/CP-0009B core.
- OptInArchiveStore uses context.noBackupFilesDir only. Explicit per-session enable required for every save, load or delete operation. Writes use fsync on staged file and Files.move(ATOMIC_MOVE, REPLACE_EXISTING) to commit. If atomic move unavailable, reject without non-atomic fallback. No automatic load/save, external storage, backup, network, or account token behavior.
- Android Activity shows the opt-in warning, opt-in action and distinct Save, Restore, Delete and Disable controls. Fresh Activity does not assume a persisted session; user must explicitly enable and restore. Restores clear volatile comparison/selection state. Corruption produces a visible rejection message without silently overwriting or deleting the retained file.
- App-private files are NOT encrypted. Explicit deletion is not a secure physical wipe. Staging sync and same-volume atomic rename do not independently prove power-loss-directory durability on a physical Android filesystem.
- CI: host pure-Kotlin deterministic encoding/tamper/bounds tests, Robolectric API35 app-private file save/restore/corruption, lifecycle/navigation controls, no-permission AndroidManifest static checks, actual APK compile/aapt/ZIP/artifact, full inherited host matrix. Exact PR-head and merged-main finalizer both required.

## Out of scope
No user data import beyond the synthetic test captures; no live RF, CAT/USB, TX/PTT, propagation network retrieval, persistent station-profile or account/certificate data. No physical Galaxy S23 Ultra or real radio testing. No encryption or origin authentication. Robolectric JVM tests are not real-device durability or instrumentation evidence.

## Next software checkpoint
CP-0009H — Android persisted archive recovery UX and failure-injection hardening: explicit rollback/error notices, atomicity gap study, lifecycle/restore integration, and bounded encrypted-store design, GitHub/CI only. Do not claim encryption until implemented and tested.

OWNER DEFERRALS: CP-0003C DEFERRED until explicit "resume CP-0003C". CP-0004A/B/C incomplete.
