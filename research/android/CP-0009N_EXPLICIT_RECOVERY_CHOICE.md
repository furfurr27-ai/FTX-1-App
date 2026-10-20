# CP-0009N — Explicit encrypted archive recovery choice and retirement-readiness review (WIP)

Verified durable parent: `CP-0009M-ENCRYPTED_ARCHIVE_CONFLICT_INSPECTION` / v64.
Implementation branch: `cp-0009n-explicit-archive-recovery-choice`.
**Status: NOT DURABLE until GitHub push/PR tests, main-branch finalizer, and snapshot hash verification.**

## Behavior

CP-0009M already authenticates original V2 and distinct-key rotated V2
independently, classifies absent/unreadable/unavailable-key sources, and
compares canonical content. CP-0009N adds an **explicit, two-tap candidate
selection and in-memory recovery** path. It does not change existing
standalone explicit save/restore/create/delete actions.

1. Both original V2 and rotation sessions must be enabled by the user.
2. REVIEW AUTHENTICATED RECOVERY OPTIONS separately inspects both archive
   candidates. Only candidates classified AUTHENTICATED are offered.
3. SELECT ORIGINAL_V2 or SELECT ROTATED_V2 records a transient intent. This
   is not a restore and does not mutate the archive displayed on-screen.
4. CONFIRM the same selected candidate invokes `recoverToMemoryExplicitly`.
   The service re-inspects BOTH encrypted candidates, rejecting any changed
   evidence or relationship since review. It independently reloads the
   deliberately chosen candidate with the matching existing key and checks
   its canonical SHA-256 and entry count against the prior authenticated
   review. On error the on-screen history remains unchanged, no fallback.
5. Success replaces **only in-memory** displayed synthetic archive history.
   It never promotes any encrypted file to active, creates a key, or writes
   ciphertext. A new inspection/selection is required for another recovery.
6. REVIEW OLD-KEY RETIREMENT BLOCKERS is a separate read-only checklist;
   status always BLOCKED_NON_EXECUTING. No retirement executor exists.

The review token is a transient in-memory value, not a signed trust
statement or a fresh monotonic counter. This is a safety guard against
unnoticed source changes between selection and restoration, **not** a proof
of nonrollbackable freshness or immunity to a genuine older ciphertext
replacement. The re-inspection is not an atomic cross-file snapshot;
on-device power-loss behavior and races are not proven.

## Non-executing old-key retirement policy

Retirement may not proceed without ALL of: a trusted independent
rollback-resistant freshness witness, separately verified recoverable backup,
specific owner consent, production Android Keystore/filesystem/hard-link
validation, and explicit V1 plaintext/original V2/rotated-copy retention
decisions. Secure flash erase is not promised. Existing original/rotated
keys and both V2 files remain usable. V1 remains plaintext unless an
existing separately selected deletion action removes it. This checkpoint
does **not** implement key retirement and does not mark it ready.

## Evidence gates

Robolectric software-key tests must verify both candidates authenticated,
explicit named selection, stale evidence rejection, missing/altered copy,
lost keys, missing opt-ins, conflict without auto-choice, failure isolation,
unchanged ciphertext and no new keys. Robolectric screen tests require
separate inspect/select/confirm controls and visible retirement blockers.
Premerge and merged-main full Android 35 debug APK builds, official package
contract, inherited Kotlin host regression matrix, snapshot creation,
verification and SHA-256 publication are required.

No physical Galaxy S23 Ultra, real Android Keystore, hard-link/filesystem,
power-loss, FTX-1 CAT/USB/audio/RF or live account/certificate testing is
claimed. CP-0003C **DEFERRED** until explicit owner instruction. CP-0004A/B/C
remain incomplete.

Next after durable promotion: `CP-0009O` — read-only recovery audit trail
and archive choice provenance UX (GitHub/CI-only), without filesystem
writes, trusted freshness or automatic retirement.
