# NEXT ACTION — FTX-1 FieldOps

## Owner execution override

CP-0003C is **DEFERRED** until the owner explicitly says 'resume CP-0003C'.
CP-0004A/B/C remain incomplete. Skip phone/radio, USB CAT/audio, live RF,
real accounts/certificates/credentials and manual hardware testing.

## Next software checkpoint

**CP-0009I — encrypted archive threat-model and migration contract**

CP-0009H now guards corruption and simulated interrupted writes in private
archive storage, with explicit user recovery notices. Next design a migration
and threat-model contract for optional encrypted storage without pretending
current reports are encrypted or source-authenticated.

## CP-0009I in progress — not verified

Branch: cp-0009i-encrypted-archive-migration-contract. New pure Kotlin non-executing migration preflight,
threat model, CI workflows and checkpoint finalizer pending validation.
Next after CP-0009I: CP-0009J authenticated encrypted archive implementation.
NO encryption/decryption/migration is implemented by CP-0009I.
CP-0003C remains DEFERRED.
