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
