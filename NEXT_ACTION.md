# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0008D — PSK Reporter public heard-path adapter**

Pin the official no-credential PSK Reporter retrieval/query schema and a deterministic XML fixture, then normalize only reports with explicit transmitter and receiver Maidenhead locators into HeardPathObservation. Preserve provider frequency/time/mode/SNR/provenance, never infer geography from callsigns, never promote a reception report into a QSO, and keep live polling out of deterministic CI.
