# CP-0008T — Offline Propagation Report Comparison Export Contract

Parent durable state: CP-0008S-PROPAGATION_OFFLINE_REPORT_INSPECTION_COMPARISON at main commit 53877043349afbeacbda39d94e6cc0f8e9d4d301.

## Export V1

\`PropagationOfflineReportComparisonSerialization.serialize(beforeArtifact, afterArtifact)\` accepts **two complete CP-0008P canonical serialized report records**, validates and imports both via CP-0008Q/R, compares via CP-0008S and serializes a deterministic export V1. An alternative \`serialize(comparison)\` exports an already computed CP-0008S comparison with structural and receipt checks; arbitrary caller-produced comparisons cannot prove original report authenticity or independent origin.

The separately versioned content type is \`application/vnd.fieldops.propagation-comparison+json;version=1\` and the format string is \`fieldops.propagation.offline-comparison\`. The canonical UTF-8 wire is exactly \`{"format":"fieldops.propagation.offline-comparison","payload":<canonical CP-0008S comparison DTO>,"wireVersion":1}\`. The existing sorted-field, sorted-map/set, ordered-list, escaping/Unicode, cycle/depth/collection bounds from CP-0008P are reused by calling a narrow internal canonical JSON value writer, **without changing the original report V1 serialization or golden fixture**. The resulting record includes content type, wire version, JSON text, exact byte count and lowercase SHA-256 digest with a 16 MiB maximum document size. Future wire-breaking changes require a new version.

The payload retains both original import receipts, original query UTC ordering and snapshot, summary and assessment change flags. It retains sorted before/after source status and timing rows and selected before/after evidence index/provenance records with added/removed/changed/unchanged classifications and metadata vs full-projection change flags.

**Important scope limit:** this is a compact *comparison result* artifact. It does not embed both full original report payloads or every changed full projection object. A true independent recalculation of the projection-content-change flag requires the two original canonical V1 report artifacts identified by their receipts. A digest is not proof of origin; a third party could forge input reports and recompute hashes.

A selected evidence removal means only absent from one selected report view; it does NOT prove provider deletion or reduced RF propagation. No provider fetches, clocks, source-store writes, filesystem/network transport, Android UI, hardware, credential/account, or RF tests are part of this checkpoint.

## Validations and tests

Serialization checks the original unkeyed receipt metadata, trust and atomicity flags, original query-time order, canonical report identity-change flag, deterministic source/evidence sorting, unique keys, before/after counts and ID correlation, source change classification, selected-index drift flag, projection-change applicability and selected change classification. It rejects invalid enum-linked states, impossible flags, malformed Unicode and oversize data as applicable. Tests cover repeatable byte-for-byte serialization, direct artifact/DTO equivalence, source IDs, non-ASCII strings, both receipts, real computed SHA/UTF-8 counts, reverse comparison, missing selected reports, integrity caveats, altered DTO/flags, and the inherited CP-0008P/Q/R/S test matrix. A premerge checkpoint dry-run verifies a temporary immutable snapshot before merge; final promotion requires full main-branch CI and a verified committed immutable checkpoint.

CP-0003C remains **DEFERRED** until the owner explicitly says \`resume CP-0003C\`. CP-0004A/B/C remain incomplete.
