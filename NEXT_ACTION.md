# NEXT ACTION — FTX-1 FieldOps

**CP-0003C — Real LoTW validation**

Build/package official TrustedQSL for Android arm64-v8a, load it on the Galaxy S23 Ultra, import a controlled real Callsign Certificate/PKCS#12, create a real signed TQ8, and run the CP-0003B transaction against a controlled LoTW test QSO:

`sign -> upload -> verify accepted-QSO report -> commit`

Then verify confirmation sync/cursors and secret hygiene. Automatic upload stays disabled unless every CP-0003C device/test-account gate passes.
