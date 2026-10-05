# LoTW integration — FieldOps v4

## Goal
LoTW is a first-class log authority alongside the local log. A successful HTTP upload is only `SUBMITTED`; a QSO becomes `ACCEPTED` only after it appears in the LoTW QSO report, and `CONFIRMED` only after the QSL report says `QSL_RCVD=Y`.

## Verified qFT8 behavior (directly checked qFT8-v3.16.apk + current qFT8 manual)
- separate LoTW web username/password and TQSL certificate password
- .p12 certificate import
- automatic upload of newly completed QSOs
- pending/not-uploaded/uploaded/confirmed states
- retry/background worker
- immediate post-upload verification and periodic confirmation sync
- full LoTW ADIF import
- persisted APP_LoTW_* metadata/cursors
- LoTW upload endpoint and lotwreport.adi endpoint are visible in the APK

qFT8 is a behavioral reference only; its source is not public and no qFT8 implementation code is copied.

## Verified ARRL behavior
- Every upload must be digitally signed with a Callsign Certificate.
- Upload endpoint: `https://lotw.arrl.org/lotw/upload`, multipart/RFC1867, signed .tq8 payload.
- Upload response uses `.UPL.` accepted/rejected and `.UPLMESSAGE.` comments.
- Acceptance/confirmation endpoint: `https://lotw.arrl.org/lotwuser/lotwreport.adi`.
- `qso_qsl=no` + `qso_qsorxsince` verifies accepted QSOs; cursor is `APP_LoTW_LASTQSORX`.
- `qso_qsl=yes` + `qso_qslsince` retrieves confirmations; cursor is `APP_LoTW_LASTQSL`.
- Match conservatively on own/station callsign, worked call, QSO date/time and band; mode can be remapped by LoTW.

## FieldOps design
1. `QsoRecord` is the local source of truth.
2. `LotwSigner` isolates TrustedQSL/TQ8 signing.
3. `LotwHttpTransport` uploads signed payloads and queries reports.
4. `LotwSyncManager` owns upload/acceptance/confirmation/import reconciliation.
5. Credentials and certificate material must be held by Android encrypted storage/Keystore-backed secrets, never logs.
6. The .p12 password and LoTW website password are distinct.
7. Background policy: immediate queue after QSO, retry with backoff, verify acceptance after upload, confirmation sync on a configurable multi-hour cadence.

## Station location safety
Do not infer LoTW station location solely from a callsign. FieldOps must maintain explicit station profiles (station callsign, grid, DXCC, CQ/ITU zone and jurisdiction fields as applicable) and bind the selected profile to the certificate before signing. This matters for portable/international operation.

## Signing status
The network/query/reconciliation layer is implemented. The production signer should bind the official TrustedQSL/tqsllib source rather than reimplementing the TQ8 cryptography from qFT8 behavior. Until that native library is integrated and tested on Android/ARM64, automatic upload must remain disabled; import and report-sync can be exercised independently with LoTW web credentials.
