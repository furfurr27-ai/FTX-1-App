# FTX-1 FieldOps checkpoint policy

Purpose: prevent repeated work, lost integration state, and over-claiming during a long multi-turn build.

## Non-negotiable rule
A feature is not considered durably complete until it is either:
1. present in the latest verified immutable checkpoint, or
2. directly re-verified in the current turn against the working files/tests.

Chat memory is navigation only. `RESUME_HERE.md` + the latest checkpoint manifest are the authoritative continuation point.

## Checkpoint cadence
Create a checkpoint at each of these boundaries:

1. **Before a risky native/DSP/radio-control change.**
2. **After a subsystem reaches green tests.**
3. **After pinning/changing an external upstream source or license decision.**
4. **After a major product-scope change affecting 3+ modules.**
5. **After a large research tranche changes architecture or priorities.**
6. **Before replacing a previously working implementation.**
7. **At the end of any tool-heavy turn that changed project artifacts.**
8. **Before packaging an installable APK or RF-transmitting build.**

For small documentation edits, multiple edits may be grouped into one checkpoint.

## Checkpoint contents
Each immutable checkpoint records:

- checkpoint ID and creation time
- parent checkpoint
- project version
- human summary
- current phase
- exact completed items
- blockers/known red items
- next exact actions
- test status
- exact upstream source pins where relevant
- SHA-256 for every working-tree file included in the snapshot
- SHA-256 of the snapshot archive

## Files used to resume

- `checkpoints/LATEST.json` — pointer to newest verified checkpoint.
- `checkpoints/RESUME_HERE.md` — concise human continuation instructions.
- `checkpoints/CURRENT_STATE.json` — structured current status.
- `checkpoints/manifests/CP-....json` — immutable manifest.
- `checkpoints/snapshots/CP-....tar.gz` — immutable source snapshot.

## Resume protocol after interruption/freeze

1. Read `VERSION`.
2. Read `checkpoints/LATEST.json`.
3. Run `python3 scripts/verify_checkpoint.py --root . --latest`.
4. Read `checkpoints/RESUME_HERE.md`.
5. Read the latest manifest's completed/blockers/next actions.
6. Inspect only files related to the next action before changing them.
7. Never recreate work already proven by that checkpoint unless the checkpoint fails verification or a newer requirement invalidates it.

## Status language
Use these states consistently:

- `GREEN` — implementation exists and relevant available tests pass.
- `YELLOW` — implementation exists but hardware/Android/RF/live-service proof is still missing.
- `RED` — missing or known broken.
- `RESEARCH` — source/behavior investigated but no production implementation yet.
- `BACKLOG_LOCKED` — requirement captured and must not be dropped.

## Packaging rule
Never overwrite an existing checkpoint archive. New work creates a new checkpoint ID and a new top-level pack version/checkpoint ZIP.

