# NEXT ACTION — FTX-1 FieldOps

## Verified durable baseline

- Checkpoint: `CP-0009Q-RECOVERY_CONSENT_FAILURE_UX`
- Version: `v68-recovery-consent-failure-ux`
- Durable `main`: `6c660d087fcdea4ae149b6dfe1b5b956d6edbf97`
- Merged-main finalizer: [39722428140](https://github.com/furfurr27-ai/FTX-1-App/actions/runs/39722428140), **46/46 successful**
- Independently calculated immutable snapshot SHA-256: `06a3d160b082ad3cea19dce2caf7af84c73630e4b362bec6614474dbd1fd463f`, matching the manifest and `LATEST.json`.

## Owner completion directive

Use `RELEASE_ACCEPTANCE_MATRIX.md` as the finite scope/acceptance tracker for the originally agreed P0–P3 product. No P4 or endlessly repeated archival UX micro-checkpoints. The app's current `MainActivity` is still a packaging-only shell and major operating services are not connected to the Android APK. A built APK is not a working radio app.

## Single next necessary software acceptance checkpoint

**CP-0009R — integrated Android operating navigation and shared-service foundation** (acceptance target; not started or verified by this document).

Implement a true, initially disconnected/non-transmitting FieldOps operating environment using existing source: multi-workspace navigation (OPS, DIGI, APRS, MAP, LOG, AWARDS, RADIO, propagation/archive), shared selection/lifecycle state, visible disabled hardware-dependent paths, truthful offline/synthetic labels, and at least one production-wired host-tested service such as logbook/awards or propagation read models. Add Android/Robolectric navigation, state/recreation, and simulated shared-service tests. Keep all inherited core, APK, privacy and safety checks. Do not claim mode/RF operation until the required engines and hardware have been validated. Avoid a purely decorative navigation mockup.

Use a PR from the current durable baseline and follow all exact-head push+PR CI, snapshot dry-run, merge, main-only finalizer and independent snapshot hash gates. After that, select further checkpoints only from actual release-matrix gaps. Finish the original modes and full integrated flows, then deliver the final software RC and **STOP**.

## Owner deferrals

- `CP-0003C` remains **DEFERRED** until Chris explicitly says `resume CP-0003C`. No real LoTW certificate/account validation now.
- `CP-0004A/B/C` remain incomplete. No real S23 Ultra, FTX-1 CAT/USB/audio/Keystore or RF evidence exists from CI.
- Preserve exclusive TX, watchdog/safe RX, opt-in archive recovery, no automatic crypto fallback, and `FRESHNESS NOT VERIFIED`.
