# RESUME HERE — FTX-1 FieldOps

Latest verified checkpoint: **CP-0005B-MANUAL_QSO_LOTW_QUEUE**
Project version: `v15-manual-qso-lotw-queue`
Phase: **Universal logger plus local LoTW auto-queue host-verified; award evaluation engine next; hardware/account checkpoints remain deferred**
Test status: **GREEN host/CI: queue=47 logger=78 LoTW_transaction=53 core=42062 pipeline=56 LoTW=19; real automatic LoTW upload remains disabled; finalizer run 37451754863**

## What is complete in this checkpoint
- Explicit LotwLoggerPolicy controls disabled versus automatic manual/digital local queue insertion
- Authoritative repository save occurs before any local LoTW queue action
- Successful local enqueue mirrors QUEUED state to the stored QSO; queue failure leaves saved QSO NOT_UPLOADED and reports separately
- Manual SSB, manual CW and eligible completed digital QSOs enter one shared LotwUploadQueue when enabled
- LotwUploadQueue enqueue is idempotent by immutable local QSO id and preserves retry/state on repeated enqueue
- Conflicting reuse of immutable QSO id or station profile fails closed
- Station profile, operating session, exact mode/submode and digital provider identity survive queue insertion
- Logger-save path contains no LoTW HTTP/signing transaction ownership
- Focused queue PASS 47; universal logger PASS 78; LoTW transaction PASS 53; core PASS 42062; pipeline PASS 56; LoTW PASS 19

## Known blockers / red items
- CP-0003C real LoTW certificate/account/device validation remains deferred and incomplete
- CP-0004A/B/C FTX-1 Android hardware checkpoints remain deferred and incomplete
- Room persistence and Compose queue UI remain later app-shell work
- Real automatic LoTW network upload remains disabled until CP-0003C passes

## Continue with these exact actions
1. CP-0006A: implement provider-independent award evaluation states WORKED, CONFIRMED, THRESHOLD_MET and OFFICIALLY_CLAIMABLE
2. Add controlled award-mode grouping without losing exact QSO mode/submode and support band/all-band evaluation
3. Use synthetic/generic award definitions only; verify official rules and claim URLs separately in CP-0006B

## Verification before continuing
Run:

```bash
python3 scripts/verify_checkpoint.py --root . --latest
```

Do not redo completed work unless verification fails or a newer requirement explicitly invalidates it.
