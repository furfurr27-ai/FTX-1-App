# CP-0008X — Bounded Historical Comparison Presentation Filters and Pagination

Parent: **CP-0008W-PROPAGATION_OFFLINE_COMPARISON_PRESENTATION**, verified durable main `b7095c55fdf1786402169d572500e7081deeea59`. This is a pure Kotlin/GitHub/CI extension to the already verified canonical report/comparison export, import, and historical presentation stack (CP-0008P/Q/R/S/T/U/V/W).

## Contract

`PropagationOfflineReportComparisonPaginationService.page(...)` always rebuilds the validated CP-0008W historical read view from the full CP-0008T/U/V canonical comparison artifact, then applies independent source and selected-evidence row filters in canonical deterministic source-key/evidence-ID order. Each independent page has zero-based **Long** offset, bounded limit 1..100 (default 25), total filtered match count, only the requested rows as an unmodifiable outer list, and nullable monotonic `nextOffset`. A future offset including `Long.MAX_VALUE` returns empty rows and no next offset; no offset arithmetic wraps. Iterating pages does not lose or repeat selected rows.

Sources support an optional exact-case source-key prefix (1..128 UTF-16 units) and change classification. Selected evidence supports exact-case evidence ID prefix, evidence kind matching before **or** after selected entries, and change classification. No locale-dependent case folding, fuzzy matching, source invention, provider deletion inference, or reordering is performed.

The header carries the canonical comparison receipt (unkeyed SHA-256 integrity only), both original report receipts, original query UTC order, four original artifact/global comparison flags, global unfiltered change tallies and optional original report reconciliation state. Global counts are deliberately separate from the filtered matching counts. The paged output does not expose the unbounded original row arrays; Kotlin nested DTOs are not claimed to be deeply immutable against unsafe casts.

If both original CP-0008P reports are supplied, CP-0008W/CP-0008V independently verifies the derivative's self-consistency against those report bytes; a mismatch does not imply provider status or a new RF observation. Supplying only one original is rejected.

## Test plan and boundaries

Host-only tests create more than 100 selected-evidence rows and more than 100 source rows, verify default and 100-row maximum limits, no skipped/duplicated row across pages, independent offsets, Long.MAX_VALUE exhaustion, boundary limits, exact prefixes, negative offsets, invalid prefixes, classification and kind filters, original receipts and unchanged global tallies, optional matching/mismatching original report states, checksum/length/JSON corruption and read-only outer page lists. All 37 inherited host CI jobs from CP-0008W remain mandatory in addition to this focused gate.

Neither successful checksum verification nor original-report consistency authenticates provider origin, source-store atomicity, live propagation, RF behavior, phone/radio, accounts, credentials or certificates. The pagination operation never controls a transmitter, hardware, storage or network.

CP-0003C remains **DEFERRED** until explicit `resume CP-0003C`. CP-0004A/B/C remain incomplete.
