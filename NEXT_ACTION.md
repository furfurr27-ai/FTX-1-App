# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return to CP-0003C until the owner explicitly says resume CP-0003C.

Skip any checkpoint that requires the phone, radio, real credentials/certificates, real accounts, RF testing, or other manual hardware validation. Continue forward to the next checkpoint that can be completed entirely through GitHub/CI. A skipped checkpoint remains incomplete.

## Next software checkpoint

**CP-0008I — concrete public HTTPS transport implementation**

Implement a concrete JVM/Android-compatible HTTPS GET transport behind the CP-0008H PublicPropagationTransport interface. Enforce timeouts, bounded streaming reads, redirect refusal, exact response metadata, UTF-8 decoding, and cleanup with deterministic CI. Keep WorkManager/background scheduling and new propagation providers outside this checkpoint.
