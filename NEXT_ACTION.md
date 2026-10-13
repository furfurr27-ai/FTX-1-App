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

Branch cp-0009j-authenticated-archive-encryption contains working-source AES-GCM V2, Android Keystore
provider, explicit separate V2 storage/migration/restore UI, fault injection
and Robolectric/host tests. PR and full CI/main finalizer are not yet verified.
Do NOT promote to CP-0009J/v61 before complete CI and immutable checkpoint.
Plaintext V1 is retained without secure erase. Next after CP-0009J:
**CP-0009K — encrypted archive key lifecycle, rotation and replay recovery**.
CP-0003C remains DEFERRED; CP-0004A/B/C remain incomplete.
