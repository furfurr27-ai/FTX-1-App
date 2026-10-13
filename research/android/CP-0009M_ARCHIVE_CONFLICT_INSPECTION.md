# CP-0009M — Read-only encrypted archive conflict inspection (WIP)

Verified durable parent: CP-0009L-EXPLICIT_ENCRYPTED_KEY_ROTATION / v63
Active branch: `cp-0009m-archive-conflict-inspection`
Status: **UNVERIFIED until exact-head PR and push CI + main finalizer + snapshot hash**.

## Intent and trust boundary

This is an explicit, **read-only** assessment of the preexisting original
AES-GCM V2 archive and the separately keyed rotated V2 copy. The new
`EncryptedArchiveConflictInspector.inspectExplicitly()` requires both
existing session opt-ins, calls each store's authenticated read using its
corresponding existing key, decodes canonical archive receipts, and compares
canonical bytes only when both sides successfully authenticate.

Individual evidence:
- `MISSING`: no file at the candidate path when inspection started.
- `AUTHENTICATED`: successful AES-GCM verification and canonical decode.
- `KEY_UNAVAILABLE`: existing key lookup returned null; bytes retained.
- `UNREADABLE`: failed read, authentication or canonical validation.
  This status does not claim the difference between corruption, key mismatch,
  concurrent changes or environment problems.

Relationship:
- `BOTH_MISSING` when both candidates are absent.
- `MATCHING_AUTHENTICATED` when decoded canonical bytes match.
- `CONFLICTING_AUTHENTICATED` when two valid decoded contents differ.
- `INDETERMINATE` otherwise.

The result always reports `BLOCKED_NON_EXECUTING` old-key retirement and
`FRESHNESS NOT VERIFIED`. No selection, restore, write, delete, key
provisioning, alias deletion, automatic downgrade, or secure erase is
performed. The new screen action reads only; other preexisting user-triggered
create/restore/delete buttons remain separately opted-in. No file/key
retirement executor is introduced.

**Authentication is not freshness or provenance.** An older valid ciphertext
can replay. An independent trusted, nonrollbackable monotonic witness would
be required for stronger recency claims, and is not implemented. Candidate
inspection does not form a global atomic filesystem snapshot: concurrent
changes can yield conservative INDETERMINATE or UNREADABLE results.

## Automated evidence to require

New Robolectric tests: matching and conflicting contents, unchanged
source bytes, key creation counters, lost original and rotated keys,
tampered tags, missing candidates, and both session opt-ins. Activity test
checks read-only button, rejection without both opt-ins, visible unverified
freshness and nonexecuting retirement decision. The inherited authenticated
V2, legacy V1, staged rotation-copy, Android API35 debug APK tests,
complete Kotlin host regression matrix, and immutable snapshot verification
remain mandatory.

No physical Galaxy S23 Ultra/Android Keystore/hard-link/power loss, Yaesu
FTX-1 CAT/USB/RF or LoTW account/certificate evidence is claimed.
CP-0003C is DEFERRED until explicit owner instruction `resume CP-0003C`.
CP-0004A/B/C remain incomplete.

## Next after durable publication

CP-0009N: explicit choice among independently authenticated recovery
candidates and NON-EXECUTING retirement-readiness review; do not auto
retire old keys or erase files.
