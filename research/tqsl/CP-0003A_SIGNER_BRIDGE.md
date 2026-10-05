# CP-0003A — TrustedQSL signer bridge evidence

Checkpoint objective: pin official TrustedQSL, add a narrow JNI boundary for certificate import + explicit station location + ADIF -> signed GABBI/TQ8, preserve TrustedQSL duplicate-database transaction semantics, and fail closed when signing is unavailable.

Parent checkpoint: `CP-0002E-NATIVE_MODE_REGRESSION`.

## Official build pin

The focused gate downloads the official SourceForge release:

- TrustedQSL **2.8.6**
- `tqsl-2.8.6.tar.gz`
- SHA-256: `182e5f2ac35a3db8b409b45d96505e6bd265ae4668ed064754209c4b8e7bdf37`

The SHA-256 is checked before extraction. The production JNI source is then compiled directly against the exact `tqsllib.h` and `tqslconvert.h` from that verified archive.

Current official master was also reviewed at:

`78a143276e9bde53b2c9535839cc19ab45b686f0`

That commit is an API-review reference only; CP-0003A's reproducible build pin is release 2.8.6.

## Production bridge

`native/tqsl/fieldops_tqsl_jni.cpp` calls the official API rather than reimplementing TrustedQSL signing.

The bridge implements:

- explicit TrustedQSL data-directory initialization
- in-memory PKCS#12 import via official Base64/import APIs
- explicit station-location lookup
- callsign and DXCC comparison against the selected FieldOps profile
- certificate selection by that callsign/DXCC
- private-key signing initialization
- temporary ADIF conversion through `tqsl_beginADIFConverter`
- duplicate tracking enabled by `tqsl_setConverterAllowDuplicates(..., 0)`
- QTH mismatch behavior fixed to REPORT, not UPDATE
- application identity `FTX1_FieldOps`
- signed GABBI retrieval through `tqsl_getConverterGABBI`
- compressed `.tq8` payload creation
- explicit converter commit
- explicit converter rollback
- cleanup/end-signing/location teardown

Signing does **not** perform LoTW HTTP upload in this checkpoint.

## Transaction boundary retained for CP-0003B

Core now exposes `TransactionalLotwSigner` and `LotwSigningSession`.

A successful signing operation returns an **OPEN** signing session containing the TQ8 payload. It does not silently commit TrustedQSL's duplicate database.

The caller must later choose:

- `commit()` after the higher-level LoTW transaction succeeds; or
- `rollback()` when the upload/verification transaction fails.

Closing an OPEN session automatically rolls back.

The existing `LotwSyncManager.upload()` is deliberately not wired to this new transactional signer yet. This prevents CP-0003A from prematurely changing upload semantics; CP-0003B owns that migration.

## Station-location safety

The JNI layer refuses signing before converter creation if:

- the named TrustedQSL station location does not exist
- the location callsign differs from the expected FieldOps station callsign
- the location DXCC differs from the expected FieldOps DXCC
- no matching certificate is available

FieldOps does not infer a station location solely from a callsign.

## Secret handling

The production Kotlin/native path is designed so:

- real PKCS#12 material is never committed to this repository
- synthetic bytes only are used in tests
- Kotlin creates transient UTF-8 password byte arrays without intentionally creating a password String
- transient PKCS#12/password copies are zero-filled after native use
- native password/base64 buffers are zero-filled after use
- native errors are surfaced through FieldOps status codes rather than raw tqsllib error strings
- temporary ADIF input is deleted after converter processing
- an unavailable native library fails closed

## Focused host gate

Branch workflow run **37321053511** completed successfully.

Focused signer bridge:

- official 2.8.6 release archive SHA-256: PASS
- production JNI compile against exact official 2.8.6 headers: PASS
- required FieldOps JNI exports: PASS
- deterministic JNI transaction fixture: **43/43 assertions PASS**
- CP-0003A signer/upload separation check: PASS

Inherited regression, independently:

- core: **42,062 PASS**
- pipeline: **56 PASS**
- LoTW: **19 PASS**

The deterministic runtime fixture is intentionally not a fake cryptographic implementation of LoTW. Its purpose is to make the JNI lifecycle, exact API calls, station-profile guards, compression, commit/rollback behavior and secret-safe error boundary executable on x86_64 CI.

## Evidence boundary

Verified/host-tested:

- official release/version/archive hash pin
- production JNI source compiles against official TrustedQSL 2.8.6 headers
- PKCS#12 import JNI path
- explicit station-location/callsign/DXCC checks
- certificate-selection and signing-init call path
- ADIF converter/GABBI call path
- TQ8 compression path
- open/commit/rollback/close transaction state
- unavailable signer fails closed
- existing LoTW/core regression remains green

Not yet verified:

- building/linking the complete official TrustedQSL dependency stack for Android arm64-v8a
- runtime loading of that official library on the Galaxy S23 Ultra
- import of Chris's real Callsign Certificate
- real cryptographic signing with a real private key
- real LoTW upload/acceptance
- transaction-safe sign -> upload -> verify -> commit integration
- automatic upload

Those remain later checkpoints. CP-0003B is the next queue item; CP-0003C owns real device/test-account validation.
