# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**CP-0009J — authenticated encrypted archive implementation**

CP-0009I reserved an encrypted v2 header and an always-denying migration
preflight, but DID NOT implement encryption. Next specify authenticated
v2 wire, Android Keystore lifetime, nonce allocation, stage/rollback
and key-loss UX, and implement in GitHub/CI with tamper tests. Never
assert hardware backing or encryption until the real code and CI prove it.

## CP-0009J active unverified branch

cp-0009j-authenticated-archive-encryption has an initial pure Kotlin authenticated v2 envelope and test
suite. Do NOT merge or promote it as CP-0009J until Android Keystore opt-in,
encrypted app-private v2 persistence, key-loss/restore/fault-injection UX,
Robolectric/APK/full inherited CI and main finalizer/snapshot all pass.
Current Android storage is still UNENCRYPTED. CP-0003C DEFERRED.
