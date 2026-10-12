# CP-0009J — Authenticated archive encryption (in progress, NOT checkpointed)

Verified parent: CP-0009I-ENCRYPTED_ARCHIVE_MIGRATION_CONTRACT / v60.
This branch starts the authenticated encryption **primitive**, not the full
Android archive encryption feature. Do not merge/claim a complete CP-0009J
until Keystore-backed explicit user opt-in, separate v2 file, failure recovery,
Robolectric tests, APK build, inherited matrix and durable finalizer pass.

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

## Required before checkpoint promotion

1. Implement Keystore key provisioning/selection, alias+key-id lifecycle
   and explicit per-session opt-in in Android. No automatic provisioning,
   migration or restore on startup.
2. Implement separate app-private encrypted v2 file with bounded read,
   sync+same-directory atomic commit and explicit user-triggered restore.
   Preserve the old unencrypted v1 file and keep all existing CP-0009H
   precommit failure tests.
3. Add Robolectric tests with injected software SecretKey for deterministic
   wrong-key/corrupt-file/missing-key/staging-failure behavior. Actual Android
   Keystore access may not be simulated by Robolectric; do not claim it was.
4. Add explicit Android UI actions with warnings distinguishing v1 plaintext
   from v2 encrypted. Never automatically delete old plaintext or fall back
   to v1 when authenticated restore fails.
5. Preserve no-permission Android manifest, build debug APK, inherited
   Kotlin/Robolectric/CI matrix, premerge dry-run, merged-main finalizer and
   immutable checkpoint hash verification.
6. Research threat-model boundary: authenticated encryption does not prove
   sender provenance or prevent replay of earlier genuine ciphertext.
   Android OS compromise, key availability and physical power-loss behavior
   remain unverified on target hardware.

OWNER: CP-0003C DEFERRED until explicit "resume CP-0003C"; CP-0004A/B/C
remain incomplete. No Galaxy S23 Ultra, FTX-1 CAT/USB/RF or real account test.
