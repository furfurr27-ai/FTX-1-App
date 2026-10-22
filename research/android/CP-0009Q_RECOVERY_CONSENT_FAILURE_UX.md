# CP-0009Q — recovery consent, cancel, and conservative failure UX

Parent: CP-0009P-AUDIT_LIFECYCLE_PROVENANCE_CONSISTENCY, durable
`main` 478e4009821307f8528fcbb223d4c4680ffe857d, v67.

**WIP until exact-head CI, merged-main finalizer and independently checked
immutable snapshot SHA-256 pass.** This is GitHub/host-only engineering.

## Existing verified behavior retained

- Opt-in original and rotated AES-GCM V2 archives remain distinct; original
  V1 plaintext is never automatically selected, migrated or deleted.
- Deliberate REVIEW authenticates the available candidates, SELECT is
  distinct from CONFIRM, and CONFIRM independently reinspects both
  candidates, reauthenticates the selected bytes, and verifies canonical
  digest and count before swapping only on-screen history.
- No file or key writing, Keystore alias retirement, trusted rollback
  witness, secure erase, or physical Samsung S23 Ultra/Yaesu FTX-1 testing.
- Evidence change invalidates stale review/selection. Disk deletion does
  not invalidate already displayed in-memory source labels; successful
  replacement does. Failure retains display/provenance.

## CP-0009Q change

- A source-specific `RECOVERY CONSENT` panel appears **only after SELECT**,
  not during READ-ONLY REVIEW. It explains that CONFIRM replaces CURRENT
  ON-SCREEN HISTORY in memory **only on successful authentication and
  verification**, does not change stored files/keys, and can discard
  unsaved in-memory changes.
- It prominently states `FRESHNESS NOT VERIFIED`: a valid older copy may
  be replayed, and cryptographic authenticity is not proof of an RF
  station/source. Neither source is ever auto-selected.
- `CANCEL RECOVERY SELECTION` clears only the pending candidate, retains
  the reviewed choices, does not restore/modify any bytes or keys, and
  records typed enum-only `CANCEL_SELECTION / CLEARED` with the chosen
  closed-enum candidate. Re-selection and explicit confirmation remain
  separate user taps.
- Failed CONFIRM clears the reviewed token and pending candidate, keeps
  on-screen history and existing source unchanged, and shows a conservative
  fixed rejection without interpolating an untrusted exception message.
  User must REVIEW AGAIN; no fallback to a different candidate or V1.
  Internal errors are not copied into the bounded audit.
- `RecoveryConsentUx` is presentation-only, containing no file/database,
  Android Context, clock, crypto/key access or network/logging operations.
  Session audit remains 12-entries default, max 64, no persistence.

## Evidence required and limitations

- Robolectric API35 test-only software-key Activity:
  1. consent wording for ORIGINAL_V2 and ROTATED_V2 with replay/station
     warnings, only after deliberate selection;
  2. cancel keeps ciphertext identical, retains displayed data, removes
     confirmation action and records only enum metadata;
  3. reselect+confirm succeeds with fresh inspection;
  4. corrupted ciphertext after review/select rejects, keeps previous
     displayed authenticated source and history, hides confirm and does
     not show raw storage exception text.
- Actual Android debug APK assembly, packaging contract, inherited
  Kotlin/host regression matrices and premerge immutable snapshot dry-run.
- After all exact-head push/PR workflows succeed, merge only that head;
  main-only finalizer must be 46/46 GREEN and SHA-256 of actual downloaded
  snapshot must match `LATEST.json` and manifest.

This is **not** proof of physical Keystore security, Android filesystem
power-loss behavior, live FT8/RF, CAT/USB/audio, LoTW credentials or real
device deployment. `CP-0003C` is explicitly DEFERRED; `CP-0004A/B/C`
remain incomplete.
