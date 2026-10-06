# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0006E — Award evidence persistence and explicit ADIF enrichment import**

Add durable provider-independent award evidence storage plus explicit ADIF enrichment for remote DXCC/STATE/CONT/IOTA/POTA fields and conservative confirmation metadata. Preserve source provenance/version, make writes idempotent/conflict-safe, and never infer missing award geography from callsigns or free text.
