# CP-0009O — Bounded session recovery audit and provenance UX (WIP)

Verified parent: `CP-0009N-EXPLICIT_ENCRYPTED_ARCHIVE_RECOVERY_CHOICE` / v65.
Branch: `cp-0009o-session-recovery-audit`.
**Not verified/durable until exact-head GitHub push and PR CI, merged-main
finalizer and immutable snapshot hash proof all pass.**

## Scope and safety policy

Adds `SessionRecoveryAuditTrail`, an Activity-owned bounded in-memory list.
Each entry contains ONLY a sequence ordinal, a closed enum action, an
optional closed enum candidate (`ORIGINAL_V2` or `ROTATED_V2`), and a
closed enum outcome. The oldest record is evicted at fixed capacity
(default 12, enforced maximum 64). Read-only `snapshot()` returns detached
records. CLEAR erases this instance's displayed audit entries and resets
the ordinal; this is not secure RAM wiping.

The UI records explicit recovery REVIEW accepted/rejected, SELECT, CONFIRM
accepted/rejected, retirement-readiness view (BLOCKED), and disabled
session actions. No file-backed log, Kotlin/Android serializer, SavedState,
Bundle, Activity retained configuration, database, Logcat, network send,
system clock, archive contents, callsigns, file paths, cryptographic key,
error message, receipt, digest, date or persistent identifier is recorded.
The audit restarts empty on a newly created Activity, including
configuration recreation: it is explicitly not a durable/legal-grade
activity log.

Displayed provenance identifies the explicitly chosen `ORIGINAL_V2`
or `ROTATED_V2` only *after successful confirmed recovery into memory*,
and resets whenever synthetic data are captured, workspace actions are
applied or an existing standalone restoration changes on-screen history.
The provenance label says independently authenticated local V2 archive,
**not** authentic station provenance, not proof of origin beyond local key,
and **FRESHNESS NOT VERIFIED**. The audit is not a replay witness.

CP-0009N's original reinspection, selected candidate reauthentication,
canonical hash/entry-count checks, two-tap explicit confirmation and
failure isolation remain mandatory. The other original V1/V2 restore,
save and delete opt-ins remain separate; their storage behavior must not
be silently changed. Key retirement remains BLOCKED_NON_EXECUTING. No
automatic copy choice, default-key cutover, key deletion, flash erase,
downgrade to V1, CAT/USB/RF operation or authentication credentials.

## Tests and promotion

- JVM/Robolectric enum-only records, fixed bounds, FIFO eviction,
  detached immutable snapshot and fresh-instance isolation
- Robolectric UI accepted/rejected/blocked events, clear operation and
  fresh Activity recreated audit, with no sensitive payloads
- Static Android packaging/privacy guard; APK version 66
- All inherited propagation, awards, archive, key/replay, user-opt-in
  Robolectric, Kotlin host and installable Android debug APK CI gates
- Premerge immutable snapshot dry run and merged-main full finalizer
  writing VERSION/README/NEXT_ACTION/SOFTWARE_TRACK/CURRENT_STATE and
  verifiable SHA-256 manifest + snapshot

This is synthetic Android host validation only. Physical Samsung S23
Ultra Keystore, hard link storage, power-loss, FTX-1 hardware/CAT/audio/RF
and real accounts/certificates require owner validation.
CP-0003C **DEFERRED** until owner explicitly says `resume CP-0003C`;
CP-0004A/B/C incomplete.

Next planned after durable promotion: `CP-0009P` — audit lifecycle and
provenance consistency hardening (GitHub/CI-only).
