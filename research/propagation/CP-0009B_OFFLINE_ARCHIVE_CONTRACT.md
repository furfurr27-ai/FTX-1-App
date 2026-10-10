# CP-0009B — Bounded offline report archive/store contract

Parent: CP-0009A-PROPAGATION_WORKSPACE_HISTORY_INTEGRATION.

## Implemented software-only scope

A caller-supplied, immutable-by-return Kotlin in-memory archive value sits above the existing CP-0009A capture and comparison bridge. Retention is by insertion order (oldest inserted first), bounded independently by a 1–128 entry cap and 1–512 MiB configurable aggregate canonical UTF-8 byte budget. Defaults: 32 entries, 64 MiB. A single artifact exceeding the configured byte budget is rejected without evicting anything. Existing SHA-256 content-key duplicates are idempotent; distinct captures with identical query timestamps are retained when their canonical bytes differ. Historical presentation sorts query UTC descending, digest ascending for deterministic tie-breaking, with explicitly bounded pagination.

Every archive operation reimports each retained original artifact with strict CP-0008R canonical validation and exact receipt matching. Selection and comparison use original artifacts, so the CP-0008Z accessibility, selected-view-only semantics and provenance warnings remain authoritative. Tampering, receipt forgery, duplicate identities and bound violations are rejected. Removal returns a new archive value. Prior values are not mutated by service operations.

## Nonclaims

This is an **in-memory archive/store contract**, not Android SQLite, filesystem persistence, authenticated provenance, cryptographic signature verification, cross-source/store atomicity, live RF truth, or physical-device verification. A Kotlin List is not a defensive copy against a malicious caller forcibly mutating a list; all entry operations therefore revalidate data. SHA-256 identifies canonical payload bytes, not their real-world source.

## Verification

- `bash scripts/test_propagation_offline_archive.sh` — host-only deterministic synthetic fixtures.
- Full inherited CP-0009A and CP-0008P/Q/R/S/T/U/V/W/X/Y/Z CI matrix.
- Premerge checkpoint dry run; final checkpoint only after merged main branch finalizer succeeds.

CP-0003C remains **DEFERRED** until the owner explicitly says "resume CP-0003C". CP-0004A/B/C remain incomplete.
