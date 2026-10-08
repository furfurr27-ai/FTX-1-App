# CP-0008K — Propagation refresh-state persistence

Parent durable checkpoint: `CP-0008J-PROPAGATION_RUNTIME_COMPOSITION`.

Evidence level: **host/CI software only**, deterministic filesystem and synthetic public transport; no phone, radio, account, RF or live provider validation.

## Scope and contract

`FilePropagationRefreshStateStore` implements the existing `PropagationRefreshStateStore` interface. Callers explicitly create it with a local directory and inject it into `PropagationRuntimeFactory.create(refreshStateStore = ...)`. The default runtime remains in-memory; no implicit Android storage path or lifecycle is introduced.

The file uses a named V1 binary format with fixed magic, version, bounded record count and UTF-8 byte lengths; source keys are serialized in sorted order. Every record stores source key and role, last attempt UTC milliseconds (nullable), last success UTC milliseconds (nullable), consecutive failures, next eligible refresh UTC milliseconds, last failure message (nullable), and retryability (nullable).

The decoder rejects bad magic, unknown versions, truncation, excessive sizes, malformed UTF-8, invalid null tags, invalid state, duplicates, extra bytes, unknown built-in roles and role/source mismatches. It throws instead of replacing a corrupt file with fresh state.

## Atomic replacement and durability

A save serializes a proposed copy before touching the filesystem. It writes a fresh temporary file in the same directory and replaces the current file using `ATOMIC_MOVE + REPLACE_EXISTING` only. If atomic replacement is unsupported or an I/O failure occurs, the operation fails closed; there is **no non-atomic fallback**. Temporary files are deleted on failure and internal state is committed only after a successful replacement. A test-injectable atomic-replace function deterministically demonstrates that a failed write leaves both in-memory state and the previous valid bytes unchanged.

Scope limit: these tests prove atomic *replacement* and write-failure behavior on the host, not fsync-based power-loss durability. This is a single-writer store; concurrent processes using the same path are not supported. A separate snapshot store is still required to preserve propagation evidence across processes.

## Runtime recreation/cadence tests

The original `PropagationRuntimeFactory` injection point is reused without changing provider refresh policies, parsers, cache, projection, or evidence semantics. Deterministic fake HTTP 503 transport proves provider network availability is not required. With persisted successful states, 4-minute refresh attempts remain skipped, 5-minute refresh attempts apply only to PSK Reporter, and 10-minute refresh attempts apply only to GloTEC and PSK Reporter across distinct runtime instances. Persisted failure counts, retryability, message and retry/backoff timestamps remain intact.

## Out of scope

No WorkManager, background scheduling, Android lifecycle/network permission, WSPRnet/WSPR.live, GIRO, HFcast/VOACAP, credentials/accounts/certificates, Yaesu USB/radio/RF operation, QSO/logbook writes, LoTW changes or manual hardware validation.

CP-0003C remains **DEFERRED** until the owner explicitly says `resume CP-0003C`; CP-0004A/B/C remain incomplete.
