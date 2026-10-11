# CP-0009E — Android offline archive/history UI integration

Parent durable checkpoint: CP-0009D-ANDROID_APK_BUILD_FOUNDATION (v55).

## Implemented in this checkpoint
- Compile platform-neutral Kotlin propagation core directly into the Android debug APK using the Kotlin Android plugin. No fork or parallel fake archive data service.
- Add a private, non-exported ArchiveHistoryActivity launched explicitly from FieldOps MainActivity.
- Use CP-0009C PropagationArchiveWorkspaceService for canonical capture, page navigation, selection, deletion, paired original-report comparison and active comparison clear.
- Build three fixed synthetic PropagationOperatingPicture instances using InMemoryPropagationSnapshotStore and InMemoryPropagationRefreshStateStore. Explicit "ADD SYNTHETIC REPORT" adds only synthetic input. No network refresh, radio CAT, USB audio, PTT, microphone, disk/database, LoTW or background operations.
- Show all canonical CP-0009C provenance notices and historical/non-live warnings as Android TextViews, untrusted report IDs rendered as text, and accessible text labels/contentDescription for Android elements.
- Retain the verified APK launcher and zero requested permissions. No live mode or transmit controls. Navigation uses system Activity Back.
- Verify CP9E static Android UI/model wiring, APK package/launcher, and all CP9D + CP9C host regressions in GitHub CI.

## Evidence boundary
An Android APK compilation and synthetic UI integration do NOT prove that the app launches successfully on a Galaxy S23 Ultra, that TalkBack correctly traverses every control, or that real storage/data/network/RF is functional. Android Activity reports are rebuilt from in-memory synthetic host models each time the Activity is created. No Android storage, persistence, actual radio operation or origin authentication. SHA-256 is content identity only.

## Next checkpoint
CP-0009F — Android synthetic archive screen automated navigation/retained-state tests and storage-boundary contract, GitHub/CI-only. Do not add physical-device requirements prematurely.

CP-0003C remains DEFERRED until explicit "resume CP-0003C"; CP-0004A/B/C hardware work remains incomplete.
