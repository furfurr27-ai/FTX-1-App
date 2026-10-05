# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0003B-LOTW_TRANSACTION_SAFE**
Project version: `v13-lotw-transaction-safe`
Phase: **LoTW transaction host-verified; real Android/device/account validation next**
Test status: **GREEN host/CI: transaction=53 signer=43 core=42062 pipeline=56 LoTW=19; real LoTW/device validation not run; finalizer run 37329432853**

## What is complete in this checkpoint
- Legacy one-shot signer/upload path retired in favor of TransactionalLotwSigner sessions
- Upload transaction is sign -> TQ8 upload -> accepted-QSO report verification -> TrustedQSL commit
- HTTP upload acceptance alone remains SUBMITTED and never implies QSO acceptance
- Signing, transport, non-2xx and acceptance-verification failures roll back without local ACCEPTED state
- LoTW endpoint rejection rolls back and is retained as REJECTED without automatic retry
- Full batch acceptance commits exactly once and retains available LoTW record metadata
- Mode-neutral LotwUploadQueue carries SSB, CW and digital QSOs through the same state machine
- Focused transaction PASS 53; signer PASS 43; inherited core PASS 42062, pipeline PASS 56 and LoTW PASS 19

## Known blockers / red items
- Real LoTW network upload and accepted-QSO verification have not been executed with a real account
- Official TrustedQSL dependency stack is not yet packaged for Android arm64-v8a
- Real Callsign Certificate/private-key signing on the Galaxy S23 Ultra has not been validated
- Automatic upload remains disabled pending CP-0003C

## Continue with these exact actions
1. CP-0003C: package official TrustedQSL for Android arm64-v8a and validate on the Galaxy S23 Ultra
2. Use a controlled real Callsign Certificate/test QSO to execute sign-upload-accepted-report-commit and confirmation sync
3. Verify secret hygiene and keep automatic upload disabled unless every CP-0003C gate passes

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
