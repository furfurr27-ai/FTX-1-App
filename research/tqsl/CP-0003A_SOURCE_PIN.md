# CP-0003A — Official TrustedQSL source pin

## Build pin

FieldOps CP-0003A pins the official TrustedQSL SourceForge release:

- Project: **Trusted QSL**
- Release: **2.8.6**
- Release archive: `tqsl-2.8.6.tar.gz`
- Published on SourceForge: **2026-06-03**
- SHA-256 published by SourceForge:
  `182e5f2ac35a3db8b409b45d96505e6bd265ae4668ed064754209c4b8e7bdf37`

CI downloads that exact official release archive and rejects it unless the SHA-256 matches.

## Current-source API review pin

The official SourceForge master tree inspected while designing this bridge showed:

`78a143276e9bde53b2c9535839cc19ab45b686f0`

This master commit is an **API-review reference**, not the CP-0003A build input. The stable build/API compile gate remains release 2.8.6.

## Official APIs used by the FieldOps bridge

The bridge is intentionally narrow and uses tqsllib/tqslconvert rather than reimplementing LoTW signing.

Initialization and secret import:

- `tqsl_setDirectory()`
- `tqsl_init()`
- `tqsl_encodeBase64()`
- `tqsl_importPKCS12Base64()`

Explicit station-location verification:

- `tqsl_getStationLocation()`
- `tqsl_getLocationCallSign()`
- `tqsl_getLocationDXCCEntity()`
- `tqsl_endStationLocationCapture()`

Certificate/signing:

- `tqsl_selectCertificates()`
- `tqsl_beginSigning()`
- `tqsl_endSigning()`
- `tqsl_freeCertificateList()`

ADIF -> signed GABBI transaction:

- `tqsl_beginADIFConverter()`
- `tqsl_setConverterAllowDuplicates(..., 0)`
- `tqsl_setConverterQTHDetails(..., TQSL_LOC_REPORT)`
- `tqsl_setConverterAppName()`
- `tqsl_getConverterGABBI()`
- `tqsl_converterCommit()`
- `tqsl_converterRollBack()`
- `tqsl_endConverter()`

Official TrustedQSL documentation explicitly distinguishes converter commit from rollback because duplicate/upload-tracking records must not be committed when processing is abandoned. FieldOps preserves that transaction instead of hiding it inside a one-shot signer call.

## FieldOps safety decisions

- Station location is mandatory and explicit.
- The selected TQSL location callsign and DXCC must match the expected FieldOps station profile before signing.
- QTH handling is REPORT, never UPDATE, so signing cannot silently rewrite station-location data.
- Duplicate handling is disabled at the converter so the official duplicate tracker is active.
- CP-0003A never commits the converter automatically.
- An open signing session rolls back when closed.
- PKCS#12 and password copies are wiped after the native call.
- Native failures return FieldOps status codes only; raw tqsllib error strings are not surfaced through this boundary.
- Temporary ADIF input is deleted after conversion.
- Signed GABBI is emitted as gzip/zlib `.tq8` data.
- No certificate, password, key, or real PKCS#12 test material is stored in this public repository.

## Evidence boundary

The CP-0003A host gate compiles the production JNI bridge against the **exact official 2.8.6 headers**. Runtime transaction tests use a deterministic fake implementation of that exact API subset so CI can test the JNI lifecycle without requiring a real operator certificate.

Therefore CP-0003A can prove the FieldOps bridge/API/transaction behavior, but it does **not** claim:

- an Android ARM64 build of official TrustedQSL and its dependencies
- successful import of Chris's real PKCS#12 certificate
- a real cryptographic TQ8 signature
- LoTW server acceptance
- Galaxy S23 Ultra execution

Those claims remain device/test-account work for later checkpoints, especially CP-0003C.
