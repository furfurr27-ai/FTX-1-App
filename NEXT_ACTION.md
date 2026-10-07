# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0008G — propagation source refresh coordinator**

Build a platform-neutral refresh coordinator over the existing public-source adapters, aggregation/cache, and projection layers. Track source cadence, success/failure, bounded retry/backoff, canonical NOAA observed/forecast selection, partial-source failure, and last-good cached snapshots using deterministic fake-source CI. Keep concrete Android scheduling/network transport and new providers outside this checkpoint.
