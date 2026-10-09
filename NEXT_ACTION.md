# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return until the owner explicitly says resume CP-0003C.

Skip any checkpoint requiring phone, radio, real credentials/certificates, real accounts, RF testing or manual hardware validation. Skipped checkpoints remain incomplete.

## Next software checkpoint

**CP-0008T — offline propagation report comparison export contract**

Build a platform-neutral, versioned canonical export contract for CP-0008S offline comparison results. Preserve both report receipts, selected-view caveats, original timestamps, source provenance and all inherited regression tests.

## In-flight CP-0008T implementation — not verified

Branch: `cp-0008t-propagation-offline-comparison-export-contract`. GitHub/CI-only canonical comparison export, independent V1 media type and SHA-256 receipt, deterministic payload, focused synthetic tests, 34-job CI gate and postmerge finalizer staged. Do not merge unless exact-tip CI is green and do not advance CP-0008S as durable parent until CP-0008T main finalizer verifies the immutable checkpoint.
