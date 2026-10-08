# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0008K — propagation refresh-state persistence**

Add a platform-neutral file-backed PropagationRefreshStateStore with deterministic versioned serialization and atomic replacement. Preserve cadence/failure state across runtime recreation and prove restart/corruption/write-failure behavior with deterministic filesystem CI. Keep Android background scheduling and new propagation providers outside this checkpoint.
