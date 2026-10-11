# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**CP-0009H — persisted archive recovery UX and failure-injection hardening**

The CP-0009G explicit opt-in, app-private canonical archive storage gate
passed simulated Android and host tests plus a CI-built APK. Next harden
failure/recovery behavior and evaluate Android storage durability without
claiming real-device testing, encryption or authenticated source data.

## Active in-progress PR — do not duplicate

CP-0009H implementation is already on PR #58, branch `cp-0009h-archive-recovery-failure-injection`.
Includes fail-closed corrupt overwrite, deterministic precommit failure tests,
Robolectric recovery UI and now a bounded streaming read with an oversized-file
regression. PR #57 is an older competing version; do not merge both.
New exact-head push + PR checks must finish GREEN before merge; main finalizer
and immutable snapshot are still required. This is not CP-0009H completion.
