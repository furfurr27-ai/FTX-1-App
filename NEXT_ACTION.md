# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0008E — propagation evidence aggregation and offline cache service**

Build a transport-neutral orchestration layer that combines already-normalized NOAA solar/geomagnetic, GloTEC ionospheric and PSK Reporter heard-path evidence into deterministic PropagationSnapshot instances, with source-specific freshness rules, deduplication, bounded offline persistence/reload, and strict separation between observed context and modeled path predictions.
