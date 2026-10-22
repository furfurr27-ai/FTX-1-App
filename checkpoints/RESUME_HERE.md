# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009Q-RECOVERY_CONSENT_FAILURE_UX**
Project version: `v68-recovery-consent-failure-ux`
Phase: **Explicit recovery consent and conservative failure UX host-tested; device/RF deferred**
Test status: **GREEN CI: recovery consent/failure Robolectric=PASS, Android APK=PASS, inherited=PASS; finalizer 39722428140**

## What is complete in this checkpoint
- Candidate-specific consent after REVIEW and SELECT, explicit CANCEL_SELECTION audit event
- Cryptographic reinspection, canonical digest checks and confirm-only in-memory replacement preserved
- Robolectric tests cancellation, successful consent, and tampered-ciphertext rejection
- Failed recovery retains display/provenance and suppresses untrusted exception messages
- Actual Android debug APK plus inherited synthetic Kotlin/Robolectric gates succeeded

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No persistent audit, trusted freshness witness, key retirement or physical S23 Ultra/Keystore/RF/CAT/USB proof

## Continue with these exact actions
1. CP-0009R: recovery accessibility and failure-path coverage, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
