# CP-0009C — Archive workspace selection and Android build-readiness boundary

Parent verified durable checkpoint: CP-0009B-PROPAGATION_OFFLINE_REPORT_ARCHIVE (v53).

## Software-only integration delivered
- A pure Kotlin app-host-facing archive workspace state/view contract over the CP-0009B canonical archive.
- Explicit capture from a caller-supplied operating picture, select, delete, compare by two retained canonical identities, history paging, page-size controls and clear comparison.
- Read-time strict report/receipt integrity and paired CP-0009A historical comparison; deterministic FIFO eviction reconciles dangling selections/comparisons.
- Accessible plain-text summary labels and warnings that historical reports are NOT live RF, TX, CAT or propagation state.
- Synthetic host validation of normal navigation, stale-state rejection, tampered receipts, bounds, eviction and previous-value immutability.

## Exact Android packaging gap
The repository's current Android folder contains bridge/source files but NO checked-in Gradle wrapper, Gradle settings, Android application module, application AndroidManifest, or installable APK GitHub Actions workflow. The script scripts/check_android_build_readiness.py emits an explicit machine-readable NOT_READY gap audit. The CP-0009C smoke check verifies that this deficit is observed; it is NOT a successful APK build, installed Activity, Android UI wiring, TalkBack evaluation or deployment to a Samsung S23 Ultra.

Next unblocked checkpoint CP-0009D must establish and CI-build an actual Android application packaging foundation or provide a separately verified build-ready alternative before any APK claim. The CP-0009C view contract is available to a future Android host, not bound to a current Android Activity.

## Safety and deferrals
- Archive is caller-owned in-memory only: no Android database, durable save, transactional storage or authenticated origin.
- SHA-256 content identity is not cryptographic source authentication.
- No radio/phone hardware, USB CAT/audio, RF, LoTW real accounts/certificates or APK installation was tested.
- **CP-0003C remains DEFERRED** until the owner explicitly says 'resume CP-0003C'.
- CP-0004A/B/C hardware work remains incomplete; no deferred work is declared done.

## Focused and inherited verification
- scripts/test_propagation_archive_workspace.sh — Kotlin host contract + explicit static packaging gap.
- scripts/check_android_build_readiness.py --expect-not-ready — machine-readable NOT_READY.
- Full CP-0009B/CP-0009A and older inherited host regression gates.
- Immutable checkpoint creation and verification only after exact-head green GitHub Actions and merged-main finalizer.
