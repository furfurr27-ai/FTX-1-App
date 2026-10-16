# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**CP-0009N — explicit archive recovery choice and retirement-readiness review**

CP-0009M classified authenticated, missing, unavailable-key and unreadable
original and rotated encrypted candidates without preference, modification or
key retirement. Next design explicitly chosen recovery and a NON-EXECUTING
retirement-readiness checklist with conservative evidence requirements.
No key deletion, automatic fallback, freshness witness, device/RF proof.


## Active CP-0009N WIP — not yet verified

Branch `cp-0009n-explicit-archive-recovery-choice`: implement two-step,
explicit, in-memory recovery using authenticated original/rotated V2
candidate review. Run all exact-head push and PR CI (including Android
APK, Robolectric and inherited tests), merge after green, run main
CP-0009N finalizer, validate manifest and immutable snapshot SHA-256.
Do not mark complete before that evidence. Next CP-0009O.
CP-0003C remains DEFERRED.
