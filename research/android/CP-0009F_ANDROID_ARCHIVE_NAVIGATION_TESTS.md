# CP-0009F — Android archive navigation and transient storage-boundary contract

Parent verified durable checkpoint: CP-0009E-ANDROID_ARCHIVE_UI_INTEGRATION (v56).

## Scope
- Android ArchiveHistoryActivity supports in-memory session retention across a configuration change, using onRetainNonConfigurationInstance and lastNonConfigurationInstance. This does not serialize reports to an Android Bundle, preferences, files, SQLite/Room, a backend, or any other durable store.
- On fresh launch/process restoration without a nonconfiguration instance, reconstruct only three fixed synthetic demo reports. Do NOT claim real station observations or silent restoration of historical report data.
- Robolectric API 35 JVM Android Activity tests exercise actual clickable Android View buttons: paging, selection, canonical before/after comparison, comparison clear, deletion, synthetic capture, launcher navigation and configuration recreation. Robolectric is simulated Android framework execution on GitHub CI, not physical Galaxy S23 Ultra testing.
- A static AndroidManifest/no-permission/no-radio/storage check rejects requests for network, USB/audio or durable storage from this screen.
- Full inherited 42 host regression matrix and real Android APK compile/aapt/ZIP artifact upload remain gates. CP-0009F cannot become a durable checkpoint until exact-head CI is green and main finalizer passes.

## Intentional limits
No persisted user report data. Host CP-0009B canonical archive remains caller-owned memory. onRetainNonConfigurationInstance is a configuration-recreation convenience only, NOT process-death save, backup, cross-session persistence or verified file-store semantics. Synthetic button actions are not real RF/USB/CAT/PTT/TX/RX or live propagation.

Do not claim physical Android interaction, on-device TalkBack access, human UI evaluation, radio/hardware tests, real LoTW credentials, or actual signed APK distribution.

## Future checkpoint CP-0009G
Plan an explicit opt-in Android local store and deterministic canonical import/export/restore transaction tests with bounded data and clear failure semantics, still GitHub/CI-only until hardware validations permitted.

CP-0003C is DEFERRED until explicitly resumed by owner; CP-0004A/B/C remain incomplete.
