# Final Review Plan

**Status:** DEFERRED — OWNER-TRIGGERED; do not execute during routine checkpoint development.  
**Activation phrase:** **"let's initiate the final review"** (also accept unpunctuated `lets initiate the final review`).  
**Owner:** Chris / N0PNG. **Targets:** Yaesu FTX-1/FTX-1F + Samsung Galaxy S23 Ultra.  
**Purpose:** A one-time, bounded, late-stage Android QA/security/performance/release pass before a software release candidate. Run this after most original P0–P3 software integration is complete, only when owner explicitly invokes it. Do not create rolling micro-checkpoints or expand product scope in response to this plan.

This issue is the build-plan log entry. Its full text will also be recorded as `product/FINAL_REVIEW_PLAN.md` in a **separate documentation PR**, without changing current durable `main` or active CP-0009S PR #71.

## When triggered: prerequisites and result protocol

1. Recover live verified `main`, `VERSION`, `NEXT_ACTION.md`, `SOFTWARE_TRACK.md`, `checkpoints/LATEST.json`, `checkpoints/CURRENT_STATE.json`, `checkpoints/RESUME_HERE.md`, active PRs, production source, and `RELEASE_ACCEPTANCE_MATRIX.md`. Never replace verified code from older conversations.
2. Identify the exact branch/parent and an actual software release gap. Work on a dedicated review branch/PR (or existing review PR if active), not on an unrelated ongoing checkpoint. Record original file versions and baseline CI.
3. Perform four ordered audits below. **Fix demonstrated defects** and add focused regression tests, then full applicable host/Android CI. Keep each commit descriptive. Never treat queued CI or unexecuted tests as passes; do not merge unverified code.
4. Record per-item `PASS`, `FAIL`, `BLOCKED` or `NOT_APPLICABLE`, with source location, exact test/run SHA and remaining action. Produce a bounded report: stability, UI performance, RF safety, R8/release packaging, regression suite, binary hash, licensing, privacy, deferred hardware/account proofs.
5. Preserve CP-0003C **DEFERRED** until Chris literally says `resume CP-0003C`. CP-0004A/B/C are physical-device gates. A CI pass is not real FTX-1 RF, USB enumeration, Android Keystore, or LoTW proof. Do not run real RF/LoTW account validation during this review without their authorization.
6. Final release eligibility stays defined by the finite P0–P3 software acceptance matrix. A final review is not a substitute for completing missing operating modes/integration. After a verified software release-candidate checkpoint, **STOP** generating feature/hardening checkpoints absent a verified defect, RF/security issue, physical integration failure, or explicit owner request.

## Action Prompt 1 — Auto-Fix Stability & Exceptions (Presentation → Data)

Act as an Android QA engineer with repository edit permissions. Inspect production Android presentation, ViewModel/controller, coroutine/async, network, radio service, database/persistence, state and lifecycle code. Identify concrete crash, memory-leak, race, uncaught exception, missing result/error-state, nullability and cancellation risks.

- Fix risks **in production source**, keeping business logic and data compatibility intact. Prefer structured concurrency, lifecycle-aware collection, scoped cancellation and typed `Result`/sealed error states where appropriate. Avoid blanket try/catch that hides fatal exceptions, swallowing cancellation, or converting safety failures into false success.
- Add regression tests for each nontrivial fixed crash/race/lifecycle path.
- Run focused tests, then appropriate `./gradlew test`, Android unit/Robolectric tests and required GitHub Actions gates (including APK packaging); document any missing task rather than asserting it ran.
- Commit file-specific changes with a clear explanation and exact before/after failure coverage.

**Exit:** identified crash/lifecycle hazards fixed, no failing applicable tests, all limitations documented.

## Action Prompt 2 — Auto-Optimize Jetpack Compose Layouts (only where present)

Act as an Android UI performance engineer. Inspect the UI framework actually in use, **do not assume Jetpack Compose exists**, and do not introduce Compose as an optimization project. If Compose exists, audit expensive composable calculations, unstable parameters, recomposition churn, appropriate `remember`/`derivedStateOf`, keyed LazyColumn/LazyRow items, snapshot collection and lifecycle. Measure/profile where possible; avoid cargo-cult use of `derivedStateOf` for stable/simple values.

If the app still uses Android Views/Activities, record the Compose-specific portion **NOT_APPLICABLE**, then inspect actual rendering for avoidable main-thread computation, repeated allocations and inefficient refresh loops within the already-approved UI performance scope.

- Apply changes only when they preserve rendering/behavior, accessibility and operator state; keep business logic untouched.
- Add or update UI tests and regression assertions, run compilation and relevant Android tests, commit with exact changed files and evidence.
- Battery/frame-rate improvements on the real S23 Ultra require physical profiling; host tests cannot certify measured power savings.

**Exit:** no known avoidable UI performance bug left in reviewed software; actual device profiling explicitly blocked until available.

## Action Prompt 3 — Strict Radio Safety Hardening (CRITICAL)

Act as an RF software safety inspector. Inspect **all** FTX-1 transport, CAT-1/2 USB identification, radio session, TX arbiter/ownership, PTT, USB/audio output, background/service lifecycle, mode switching, startup, crash recovery and reconnect code.

- Enforce **no automatic PTT on startup, reconnect, workspace switch, background exception, USB enumeration or decoder change**. Central TX ownership is exclusive; a modem, UI or helper cannot bypass it. RTS/DTR are not PTT. CAT TX authorization is explicit and must be safely serialized.
- On *detected* exceptions/disconnects/timeouts, request existing RX-safe shutdown: stop TX audio, issue a guarded TX0/PTT-off where transport is available, release owner, disarm until explicitly reset. Timeouts/watchdogs must bound hang duration. **A dead process/disconnected radio cannot be guaranteed to send TX0**; physical hardware/wiring behavior and independent radio-side watchdog must be verified separately. Never promise mathematically impossible 'no circumstance' guarantees from Kotlin catch/finally alone.
- Add fault-injection and concurrency tests for competing TX owners, cancellation, crashes, missing/ambiguous USB ports, reconnection, audio route failure, app process destruction and failed TX0. Keep actual transmissions **DISABLED** pending physical CP-0004A/B/C evidence.
- Run all appropriate CI; commit verified edits with message starting `security/safety: hardened fail-closed PTT path` and name affected files in the commit body.

**Exit:** software interlocks/tests documented green or blocked with exact gaps; no fictional RF safety claim.

## Action Prompt 4 — Production Build Hardening (ProGuard/R8)

Act as Android release/DevOps engineer. Inspect **current** `build.gradle(.kts)`, plugins, dependencies, manifest, native JNI ABI, app build variants, packaging and license/provenance. Do not assume Compose, Hilt, Room or DataStore are present.

- Configure release `minifyEnabled` / `isMinifyEnabled` and `shrinkResources` / `isShrinkResources` appropriately; create/maintain `proguard-rules.pro` and attach it to the actual release variant.
- Add **minimal** required rules only for real reflectively accessed models, JNI classes/methods, serialization, service-loader/provider discovery, and other pinned dependencies; inspect consumer rules shipped with libraries. Do not add broad speculative `-keep class ** { *; }` rules, or use obfuscation as a substitute for credential protection.
- Run **release-variant** shrink/compile/package, plus Android UI/native smoke/integration tests, check manifest, ABI and `lib*.so` symbols, and verify artifact SHA-256. Ensure source/license distribution review and release-signing/key handling have explicit evidence, without committing secrets.
- Record R8 mapping-file handling policy and reproducible build instructions. Do not claim an installable verified release until CI/APK tests pass; do not distribute an APK while license review remains on hold.
- Commit clear file-specific changes after verification.

**Exit:** tested appropriately shrunk release candidate or explicit blocker; no guessed dependency rules.

## Final one-time review gate

Completion requires all four applicable audits with actual evidence; updated release acceptance matrix, full regression/Android CI green at exact verified head; safe transmitter invariants preserved; validated APK hash and source/license/privacy/installation report. Produce final software release candidate and durable snapshot only when existing acceptance prerequisites are satisfied. Keep real S23 Ultra, FTX-1, CAT/USB/audio/RF and authorized account validation separate from host/CI verification.

**Trigger wording is a user decision, not a scheduled automation:** Do **nothing now** beyond saving this plan.

## Record of plan registration

- GitHub tracker: https://github.com/furfurr27-ai/FTX-1-App/issues/73
- Source plan index: `product/FEATURE_BACKLOG.tsv` / `product/ONE_STOP_SHOP_REQUIREMENTS.md`
- Entered October 26, 2026, before the final software release candidate; NOT STARTED.
- This planning document does not assert new CI evidence, modify `VERSION`, or promote a checkpoint.
