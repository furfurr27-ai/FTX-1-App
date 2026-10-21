# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0009P-AUDIT_LIFECYCLE_PROVENANCE_CONSISTENCY**
Project version: `v67-audit-lifecycle-provenance-consistency`
Phase: **Recovery option invalidation and display provenance consistency host-tested; device/RF deferred**
Test status: **GREEN CI: stale-review provenance Robolectric=PASS, Android APK=PASS, inherited=PASS; finalizer 39648399244**

## What is complete in this checkpoint
- Stale encrypted recovery review and selection invalidated on ciphertext operations or session disable
- Authenticated displayed source kept during paging/selection and cleared on archive replacement
- Robolectric tests source preservation, synthetic replacement, null restores and Activity recreation
- Typed bounded transient audit, V1/V2/key paths unchanged; no freshness witness or retirement
- Real Android debug APK and inherited synthetic Kotlin/Robolectric host regressions succeeded

## Known blockers / red items
- OWNER OVERRIDE: CP-0003C DEFERRED; do not resume without explicit instruction
- CP-0004A/B/C hardware checkpoints incomplete
- No persistent audit, trusted freshness witness, key retirement or physical S23 Ultra/Keystore/RF/CAT/USB proof

## Continue with these exact actions
1. CP-0009Q: recovery consent and failure UX hardening, GitHub/CI-only
2. Preserve APK artifact build plus all inherited host regression gates
3. CP-0003C remains DEFERRED; skip manual hardware/account validation

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
