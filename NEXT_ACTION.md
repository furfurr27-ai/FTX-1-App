# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0008H — public propagation transport adapters**

Add a platform-neutral request/response transport boundary and source adapters that feed the existing NOAA SWPC, GloTEC, and PSK Reporter parsers into the CP-0008G refresh coordinator. Prove exact endpoint handling, GloTEC latest-artifact selection, response/error/size handling, and PSK Reporter provenance restrictions with deterministic fake transport CI. Keep concrete Android networking/scheduling and new providers outside this checkpoint.
