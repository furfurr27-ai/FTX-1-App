# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**CP-0009P — audit lifecycle and provenance consistency hardening**

CP-0009O added a bounded, instance-owned, metadata-only recovery audit,
clear-on-request and clear-on-Activity-recreation; it labels only selected
original/rotated V2 after explicitly confirmed in-memory recovery.
No archive text/hashes/keys or time is logged; FRESHNESS NOT VERIFIED.
Next strengthen lifecycle/provenance consistency across screen changes,
without persistent audit records or key retirement. Preserve every
inherited Android/host gate and immutable checkpoint proof.
CP-0003C DEFERRED; CP-0004A/B/C incomplete.


## CP-0009P WIP — do not promote until all gates pass

Branch `cp-0009p-audit-lifecycle-consistency`. Implemented UI lifecycle
invalidation, enum-only audit labels and Robolectric simulation tests.
Complete exact-head GitHub push and PR full Android APK/Kotlin 46-job CI,
including premerge finalizer/immutable snapshot dry run. Correct failures
before merge. On green, merge exact head, await main
`finalize-cp0009p.yml` 46/46 and independently SHA256 verify snapshot
against LATEST.json and manifest, then proceed to CP-0009Q.
CP-0003C DEFERRED; CP-0004A/B/C hardware work remains incomplete.
