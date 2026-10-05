# NEXT ACTION — FTX-1 FieldOps

**CP-0003A — TrustedQSL signer bridge**

Pin the official TrustedQSL source/version. Add a narrow JNI API for PKCS#12 certificate import, explicit station-location selection, and ADIF -> signed GABBI/TQ8 output. Fail closed if signing material or location is unavailable/ambiguous, and never write certificates, passwords or keys to logs or this public repository.

Keep signing separate from upload/reconciliation. CP-0003B will own the transaction-safe sign -> upload -> verify -> duplicate-state commit flow. Automatic LoTW upload remains disabled until later real-device/test-account validation.
