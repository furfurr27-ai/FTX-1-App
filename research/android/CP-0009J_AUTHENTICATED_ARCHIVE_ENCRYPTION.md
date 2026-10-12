# CP-0009J — Authenticated archive encryption (in progress, NOT checkpointed)

Verified parent: CP-0009I-ENCRYPTED_ARCHIVE_MIGRATION_CONTRACT / v60.
This branch implements a separate opt-in Android encrypted V2 store and UI,
but is NOT a verified checkpoint until full Robolectric/host/APK inherited
CI and merged-main immutable finalizer succeed. Existing plaintext V1 files
are preserved; physical Keystore and radio tests remain out of scope.

## Implemented first-stage pure Kotlin cipher

- New v2 magic FTX1ENC2 and big-endian schema:
  8-byte magic, 4-byte version 2, 4-byte AES-256-GCM suite 1,
  16-byte nonsecret key identifier, 12-byte random nonce,
  4-byte ciphertext length including 16-byte authentication tag, ciphertext.
- All **48 bytes** of clear header are authenticated as AES-GCM AAD.
  Canonical plaintext v1 is independently decoded and receipt-validated
  before encryption and again only after successful tag verification.
- Bounded total payload using CP-0009I reserved cap. Wrong key/identifier,
  modified version/suite/nonce/AAD/tag, trailing data, invalid format and
  truncated input fail without a plaintext fallback.
- Fresh SecureRandom-generated nonce per encryption; nonce reuse with the
  same key is forbidden. CI test compares consecutive envelopes.
- Only an inspectable 256-bit AES key is accepted, or an opaque provider
  AES key whose size cannot be queried. A future Android-specific provider
  must explicitly request 256-bit Keystore keys; this pure JVM codec alone
  cannot prove the key is hardware-backed or that an opaque key is 256 bits.

## Implementation and verification requirements

1. Implemented: Android Keystore key provisioning uses AES-256-GCM fixed
   alias only after explicit save. Missing keys on restore never regenerate.
   A stable non-secret key identifier is derived from the alias; rotation
   remains a future CP-0009K concern. Hardware-backed keys are not asserted.
2. Implemented: separate app-private V2 file with bounded streaming read,
   fsync + same-directory ATOMIC_MOVE and explicit restore; V1 plaintext
   remains untouched. Stage and corruption tests are added.
3. Added: Robolectric tests inject software SecretKeys for wrong-key,
   corrupt-file, missing-key, oversized archive and staged failure.
   Real Android Keystore access is NOT proven by Robolectric.
4. Implemented: separate Android encrypted enable/save, validated V1
   migration-to-V2 COPY, authenticated restore and delete buttons. Explicit
   warnings identify the retained unencrypted V1 and no plaintext fallback.
5. Pending verification: zero-permission manifest, host and Robolectric,
   actual APK/aapt/build artifact, all inherited tests, premerge dry-run,
   merged-main finalizer and immutable checkpoint hash.
6. Research threat-model boundary: authenticated encryption does not prove
   sender provenance or prevent replay of earlier genuine ciphertext.
   Android OS compromise, key availability and physical power-loss behavior
   remain unverified on target hardware.

OWNER: CP-0003C DEFERRED until explicit "resume CP-0003C"; CP-0004A/B/C
remain incomplete. No Galaxy S23 Ultra, FTX-1 CAT/USB/RF or real account test.
