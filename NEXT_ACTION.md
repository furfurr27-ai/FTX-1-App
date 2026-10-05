# NEXT ACTION — FTX-1 FieldOps

**CP-0003B — Transaction-safe LoTW upload**

Migrate the upload path to the CP-0003A transactional signer session:

`sign -> upload TQ8 -> verify LoTW acceptance -> commit TrustedQSL duplicate state`

Any signing/network/rejection/verification failure must roll back the signer transaction and must not mark the QSO accepted locally. HTTP upload success alone is not LoTW acceptance.

Manual SSB/CW and digital QSOs use the same queue and transaction rules. Automatic upload remains disabled until CP-0003C real device/test-account validation.
