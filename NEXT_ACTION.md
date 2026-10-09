# NEXT ACTION — FTX-1 FieldOps

## Execution override

CP-0003C is **DEFERRED**. Do not return until the owner explicitly says resume CP-0003C.

Skip any checkpoint requiring phone, radio, real credentials/certificates, real accounts, RF testing or manual hardware validation. Skipped checkpoints remain incomplete.

## Next software checkpoint

**CP-0008Q — propagation offline report decode and validation**

Implement a strict, bounded offline decode and validation path for CP-0008P's versioned canonical JSON report contract with deterministic round-trip and corruption tests, without network, provider, credential or hardware dependencies.

## In-flight GitHub work (not a completed checkpoint)

CP-0008Q implementation is staged on `cp-0008q-propagation-offline-report-decode-validation`, PR #40. GitHub Actions exact-tip verification is required. Inspect the newest PR head and both CP-0008Q workflow runs, fix any failed tests, and only merge when all required jobs are green. The main-branch `finalize-cp0008q.yml` workflow must then pass and commit a verified immutable CP-0008Q snapshot/manifest; confirm README, NEXT_ACTION, SOFTWARE_TRACK, LATEST/CURRENT_STATE/RESUME_HERE afterward. CP-0008P remains the verified parent until those gates complete. Do not return to deferred hardware/account work.
