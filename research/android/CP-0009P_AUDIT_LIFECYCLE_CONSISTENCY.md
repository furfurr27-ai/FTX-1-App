# CP-0009P — Recovery audit lifecycle and provenance consistency

Parent: CP-0009O-SESSION_RECOVERY_AUDIT_PROVENANCE (v66).
Implementation branch: `cp-0009p-audit-lifecycle-consistency`.
**Work in progress, NOT a verified checkpoint before exact-head CI, merged-main
finalizer and independently checked immutable snapshot SHA-256 all pass.**

## Problem addressed

CP-0009O offered a bounded typed in-memory UI audit and labeled the
explicitly authenticated recovered display source. It also cleared that
source on every workspace dispatch, including **pagination, selection and
comparison**, although those actions do not change the underlying archive.
Furthermore, an explicitly inspected or selected recovery choice could
remain visible following separate encrypted-file save, migration, rotation
copy creation, file deletion or other archive changes. CP-0009N's
reinspection still rejected stale evidence at confirmation, but stale
choice buttons should not remain available.

## Verified-by-CI-only design intent

- `invalidateRecoveryOptions()` clears previous review and pending
  candidate if storage evidence **might** have changed, even if file
  operation subsequently rejects after a partial write. Emits only typed
  `OPTIONS_INVALIDATED | CLEARED` if there was a pending review or
  selection. The user must REVIEW AGAIN before any new SELECT/CONFIRM.
  Does not erase a still-displayed authenticated source: disk evidence
  and current in-memory display are distinct.
- `invalidateDisplayedRecoveryProvenance()` clears only after a new
  report, actual archive-changing workspace action, or another successful
  local/standalone V2 restore replaces on-screen history. Also clears
  pending review/selection, and records typed `DISPLAY_REPLACED` if
  source was previously explicitly known. Failures/null reads do not
  replace display or silently change source provenance.
- Pagination, selecting a report, setting before/after, comparing,
  focus changes and similar archive-preserving workspace operations
  **retain** the explicit previously recovered source label. UI retains
  a conservative label on Android Activity/configuration recreation:
  the audit and source claim do not persist; no implicit proof of source
  is constructed from retained bytes.
- Disable encrypted/rotation session clears pending review/choice, logs
  a fixed typed `SESSION_DISABLED`, and does not automatically erase
  the visible archive source, copy, key or ciphertext. Clearing the
  session-only audit does not alter current view or pending recovery.
- Review/select/confirm remain distinct user actions; source
  reinspection, reauthentication and canonical digest checks remain
  mandatory. Failed recovery remains nonfallback and does not replace
  displayed in-memory bytes or source. FRESHNESS NOT VERIFIED.
- No free-text payload, error string, file path, secret, hash, date,
  account identity, RF data, timestamp or durable identifier in audit.
  Audit always in-memory and bounded (12 default, max 64); never
  written to Android storage, logcat, network, saved-state or bundle.
- Retirement still BLOCKED_NON_EXECUTING. No key wipe, new trust witness,
  default plaintext fallback, ciphertext age proof or station provenance.

## Planned evidence

- Robolectric: review+select invalidated by new encrypted write; disabled
  rotation invalidates candidate; no phantom confirmation button.
- Robolectric: explicitly confirmed in-memory recovery source survives
  page navigation; synthetic capture clears it; second recovery followed
  by Activity recreation clears ephemeral audit/source but retains bytes.
- Robolectric: deleting V2 ciphertext invalidates pending review but
  does not erase already-displayed source; null standalone restore
  preserves on-screen data; no silent fallback.
- Static app packaging/privacy contract versionCode 67.
- Full inherited Kotlin shell regressions, opt-in archive key tests,
  actual Android debug APK compile/manifest check and Robolectric suite.
- Exact-head push and PR premerge checkpoint dry runs, followed by
  successful merged-main finalizer (46/46) and SHA256 verification.

No physical Galaxy S23 Ultra Keystore, hard-link/power-loss, FTX-1 CAT,
USB audio, RF, LoTW account/certificate or trustworthy freshness witness
is tested. CP-0003C is **DEFERRED** until owner explicitly requests
`resume CP-0003C`; CP-0004A/B/C remain incomplete.

## CI failure diagnosis and repair — 2026-10-21

Exact-head `a95989bbcd83aaee4239e4204bbf82ac412bcbd4`
had 44 successful inherited jobs but Android Robolectric failures:
three CP-0009P lifecycle tests failed at setup/save line 49, and the
CP-0009O audit test failed on post-clear re-review line 60. On host
Robolectric, a real Android Keystore-backed key is not a valid assumption
for a test that explicitly saves ciphertext. Production must continue
to use AndroidKeyStore, not software-generated fixed keys.

Fix: `ArchiveHistoryActivity` exposes two protected overridable
provider accessors with production AndroidKeystoreArchiveKeyProvider and
AndroidKeystoreRotatedArchiveKeyProvider defaults, no intent/UI setting.
A subclass in `app/src/test/` alone supplies software SecretKeySpec AES
keys, lazily created ONLY by explicit save for offline Robolectric
archive-flow tests. No such fixture is compiled into the installable
APK. Test subclass covers successful save, review, confirmed recovery,
storage-evidence mutation, deletion and Android recreation using
software-only cryptography. This does not demonstrate Android hardware
Keystore availability on Galaxy S23.

Repeated deliberate REVIEW now supersedes prior in-memory selection
without spuriously emitting an OPTIONS_INVALIDATED storage-mutation event;
the single new REVIEW event is audited. A real disk evidence mutation
still emits OPTIONS_INVALIDATED and requires a new review/selection.

Neither repair proves device key hardware, actual RF or trusted
cryptographic recency. Re-run exact-head Android, push/PR CI, full main
finalizer and immutable snapshot SHA-256 before promotion.
