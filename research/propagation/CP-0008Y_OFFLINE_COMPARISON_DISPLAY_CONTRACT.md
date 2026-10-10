# CP-0008Y — Offline comparison paged display integration contract

Durable parent: CP-0008X-PROPAGATION_OFFLINE_COMPARISON_PAGINATION at main `eb64ed7be0a93f3da2daab0da9ff3db3e1fca63e`.

## Host-only integration scope

A future Android UI should consume a small, stable historical read contract, not infer provider deletion, current RF quality, or origin authentication from exported reports. `PropagationOfflineReportComparisonDisplayService.display(...)` adds such a Kotlin/platform-neutral contract above CP-0008X pagination. This checkpoint does not implement Android UI, conduct hardware validation, or read current provider state.

Every call strictly validates the original CP-0008T/U/V canonical comparison artifact through X -> W -> V -> U; invalid version, media type, byte length, SHA-256, noncanonical JSON and corrupt nested DTOs are rejected. Original report pairs are optional but always all-or-nothing; when supplied CP-0008V recomputes historical comparison content, with explicit `ORIGINAL_REPORTS_MATCH` or `ORIGINAL_REPORTS_MISMATCH` and no authentication claim.

The display model includes the unmodified CP-0008X header (original before/after report receipts, comparison receipt, original UTC query time order, global unfiltered change totals, four comparison flags and original-report reconciliation); two independently paginated display sections (sources and selected evidence); typed source/evidence before and after DTOs with metadata and full projection change flags preserved; fixed historical section titles; typed selected-view-only change labels; exact user-supplied filter queries and next/previous query values that preserve the filters, case-sensitive ID prefix and evidence kind. Only up to 100 rows per section are exposed in read-only outer lists. A host can request one page, render it, then send the returned typed continuation query to retrieve the next; no live clock, network, mutable cursor or store is involved. Identifiers and provider provenance values are untrusted text and MUST be escaped by future UI hosts; this layer does not emit HTML and does not render views itself.

The model always carries four fixed notices warning that (1) this is historical and not live RF/provider health, (2) added or removed refers only to archived selected report membership and not provider deletion, (3) unkeyed SHA-256 is not authenticated provenance, and (4) cross-store atomicity and hardware behavior remain unverified. These notices persist whether original reports are absent, match or mismatch. No device-independent proof of RF conditions is implied. Outer collections use unmodifiable wrappers; nested Kotlin DTOs are not a security-grade deep immutability boundary.

## Verification

Focused JVM tests verify canonical metadata, two receipts, original UTC time order, fixed warning codes/content, selected view change labels, unchanged global tallies, independent page navigation with exact filters, complete multi-page traversal without duplicate or missing rows, full-projection/index flags, original-report reconciliation states, empty/reversed selections, Long.MAX_VALUE overflow safety, negative/overlimit queries, unsupported versions and corrupted payload rejection, and unmodifiable list wrappers.

The complete 38-job inherited CP-0008X host CI matrix plus this focused gate must pass on the exact PR head; after merging, 39 main tests plus the durable finalizer must pass. Finalizer must verify checkpoint manifest and immutable snapshot SHA-256 before promoting the version.

CP-0003C remains **DEFERRED** until the owner explicitly says `resume CP-0003C`. CP-0004A/B/C remain incomplete. No real phone/radio/USB/CAT/audio, provider status, accounts, credentials, certificates, signatures, RF test or manual validation is claimed.
