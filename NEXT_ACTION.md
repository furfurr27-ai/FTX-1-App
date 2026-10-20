# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**CP-0009O — read-only recovery audit trail and provenance UX**

CP-0009N added explicitly inspected, named and confirmed in-memory
recovery choices using the original/rotated existing AES-GCM key.
Re-inspection and canonical reauthentication reject stale or lost-key
choices; neither old file nor key is ever retired or auto-selected.
Next implement a nonpersistent, non-sensitive read-only audit trail of
explicit choices and failure outcomes. No trusted freshness witness.
CP-0003C stays DEFERRED; physical phone/radio checkpoints stay incomplete.


## CP-0009O WIP implementation — not verified

Branch `cp-0009o-session-recovery-audit`. Implemented bounded typed-only,
nonpersistent session audit of recovery actions and results with explicit
current source label. PR must first pass exact-head push and PR complete
Android APK, Robolectric and inherited host CI. Then merge and require
merged-main finalize-cp0009o.yml 46/46 and immutable manifest/snapshot
SHA-256 matching LATEST.json. CP-0009P next only AFTER completion.
CP-0003C remains DEFERRED and CP-0004A/B/C incomplete.
