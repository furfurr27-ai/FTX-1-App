# CP-0009L — Explicit encrypted archive key-rotation copy (in progress)

**Verified parent:** CP-0009K-ENCRYPTED_KEY_LIFECYCLE_REPLAY_CONTRACT / v62.
**Branch:** `cp-0009l-explicit-archive-key-rotation`.
**Checkpoint status:** WIP. Do not claim durable or on-device validation until all Android,
Robolectric, host, APK, inherited, merged-main finalizer and immutable hash checks pass.

## Implemented scope

CP-0009L introduces **one-shot user-confirmed re-encryption to a distinct
key and a distinct file**. This does not automatically switch the default
encrypted V2 save path, invalidate the old key, delete the old ciphertext,
or touch the unencrypted V1 file.

The original key alias remains:
`dev.n0png.fieldops.synthetic-archive-aes-v2`.
The distinct new Android Keystore alias is:
`dev.n0png.fieldops.synthetic-archive-aes-v2-rotated-k1`.
Its SHA-256-derived 16-byte nonsecret key identifier is different from the
original V2 identifier. Both files use the existing authenticated FTX1ENC2
wire codec with independently provider-generated AES-256-GCM nonces.

Original encrypted V2:
`noBackupFilesDir/fieldops-synthetic-archive-v2.enc`.

First rotated immutable candidate:
`noBackupFilesDir/fieldops-synthetic-archive-v2-rotated-k1.enc`.

### Explicit transaction

1. User enables the original encrypted archive session separately, and
   enables a **rotation copy** session separately. Actual operation needs
   a distinct CREATE tap.
2. Refuse to proceed if the rotated destination already exists. Its bytes
   are evidence and are never silently overwritten or auto-preferred.
3. Authenticate the original encrypted V2 file with the original key and
   revalidate canonical V1 receipts **before** creating a new key.
   Source missing/corrupt/lost-key errors preserve all original bytes.
4. Only on explicit action, provision or recover the separate rotated
   Android Keystore AES-256-GCM key. Seal canonical content using its
   separate key identifier and a cryptographic-provider-generated nonce.
5. Write the new ciphertext to a distinct temp file in the same app-private
   no-backup directory. `fd.sync()`; inject optional synthetic failure.
6. Authenticate/decode the staged V2 bytes under the rotated key, assert
   canonical archive equality, inject a separate precommit failure.
7. Publish an **exclusive hard link** from staged inode to a new rotated
   destination. `Files.createLink` is an atomic name publication which
   fails if destination exists, avoiding `ATOMIC_MOVE` overwrite ambiguity.
   On Android/filesystems without hard-link support, fail closed; DO NOT
   fall back to a non-atomic copy or overwrite.
8. Authenticated read-back of published copy; always remove stage pathname
   in `finally`. Retain original V2 ciphertext and original Keystore key.
9. Later, only on explicit restore, read authenticated rotated ciphertext.
   A restore **never provisions a new key**, auto selects, or downgrades to
   the old V2 or unencrypted V1.
10. A separate explicit DELETE ROTATED COPY action deletes only the rotated
    file; it does not securely erase its bytes or retire any key.

### Test and failure coverage

Robolectric/injected software keys test successful re-encryption, distinct
ciphertext, V2 preservation, missing opt-ins, missing source, bad original
tag, key loss, wrong rotated key, bad rotated tag, oversized file, refusal
to overwrite existing rotated copy, stage-sync and prepublish injected
failures, and stage-path cleanup. Activity tests assert explicit controls
and failure UX. CI static checks assert no key deletion or replacement
overwrite. Host and Robolectric DO NOT prove that physical Galaxy S23
Ultra Android Keystore and its app-private filesystem permit atomic hard
links. If linking fails on-device, no rotated file should be published.

### Security boundaries

- **Freshness and anti-replay remain unverified.** A malicious older
  genuinely authenticated ciphertext can still replay. Rotation to a new
  key does not itself establish a nonrollbackable monotonic witness.
- A previously provisioned rotated key may be reused when a rotated copy
  was explicitly deleted, with a fresh provider-generated nonce each time.
  This is the fixed first rotation slot, not arbitrary multi-generation
  rotation or automatic deletion of old keys.
- The code cannot guarantee flash/directory power-loss durability, secure
  deletion, hardware-backed keys, Android Keystore behavior on a real S23
  Ultra, or security against device compromise.
- No encrypted source is treated as authentic **report provenance** merely
  because the AES-GCM tag is valid. Archive content remains synthetic.
- CP-0003C is DEFERRED until owner explicitly says "resume CP-0003C".
  CP-0004A/B/C require actual hardware and remain incomplete. No USB CAT,
  RF, real accounts/certificates, physical radio or phone validation occurs.

## Promotion gates

CI must run original V1 and V2 archive regressions; new Android rotation
Robolectric, UI and APK packaged validation; all inherited software test
matrix; premerge immutable CP-0009L snapshot dry-run; and merged-main
CP-0009L finalizer. Verify snapshot SHA-256, VERSION, LATEST, CURRENT_STATE,
checkpoint manifest, README, NEXT_ACTION and RESUME_HERE before calling
CP-0009L durable.
