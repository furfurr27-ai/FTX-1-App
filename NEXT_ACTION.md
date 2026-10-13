# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**CP-0009L — explicit encrypted key-rotation transaction design and implementation**

CP-0009K is a non-executing key lifecycle and replay-risk contract, NOT
implemented rotation or replay protection. Plan a recoverable new-key
transaction and explicit user choices with fully verified ciphertext
readback and original retention. Never imply a local AES-GCM tag or
mutable local version prevents replay. Keep V1 plaintext and V2 separate.
CP-0003C remains deferred.
