# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**CP-0009K — encrypted archive key lifecycle, rotation and replay recovery**

CP-0009J provides verified *host/Android-simulated* AES-GCM V2 storage with
user opt-in, but no physical Android Keystore proof. Old V1 plaintext still
exists until independently deleted. Next implement explicit key rotation
and lost-key/replay recovery safeguards. Preserve all APK/CI regression gates.
