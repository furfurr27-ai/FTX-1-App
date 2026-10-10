# CP-0009D — Android application packaging and CI-built debug APK

Parent durable checkpoint: CP-0009C-ARCHIVE_WORKSPACE_BUILD_READINESS / v54.

## Deliverables
- Gradle 8.13 wrapper from Gradle upstream tag v8.13.0; Gradle Git blob identity 9bbc975c742b298b441bfb90dbc124400a3751b9 is checked in CI to guard wrapper tampering.
- Android Gradle Plugin 8.13.2, Java 17, compile/target SDK 35, application ID dev.n0png.fieldops, min SDK 29, versionCode 55.
- A real AndroidManifest with exported launcher Activity, Android resources and pure-Java MainActivity, limited to an offline non-transmitting FieldOps developer shell.
- No permissions, services, radio transports, microphone, internet, JNI native modes or actual archive-UI store integration in this milestone.
- build-android-apk.yml with actual Android SDK installation, Gradle assembleDebug, aapt package/launcher validation, APK ZIP integrity verification, SHA-256 output and uploaded debug APK artifact.

## Verification contract
CP-0009D is GREEN only after exact-head PR and branch CI verifies:
- An actual non-placeholder generated app/build/outputs/apk/debug/app-debug.apk
- aapt package dev.n0png.fieldops and launchable Activity dev.n0png.fieldops.app.MainActivity
- wrapper Git object identity unchanged from pinned Gradle source
- no Android runtime permissions in manifest and explicit offline/no-radio UI wording
- all inherited 43 host regression gates, including CP-0009C archive workspace, remain green
- merged-main finalizer builds again and publishes an immutable verified checkpoint snapshot

The static readiness check now asserts the packaging prerequisites exist but explicitly does NOT imply an APK build succeeded. Actual debug artifact proof is independent CI evidence, not physical install proof.

## Next software work after this checkpoint
CP-0009E: wire the canonical offline archive/history view model into Android app navigation with synthetic offline artifacts, minimal persisted state contract, and CI-based Android tests where feasible. Do not imply real live radio/USB/RF or actual archive on-device storage before validated.

## Boundary
The APK is a **developer shell**, not yet a functional ham radio companion. Android Activity launching on the Galaxy S23 Ultra, real FTX-1, radio CAT/PTT/audio, propagation live sources, RF, LoTW real accounts/certificates and physical USB are NOT tested. Keep CP-0003C DEFERRED, CP-0004A/B/C incomplete.
