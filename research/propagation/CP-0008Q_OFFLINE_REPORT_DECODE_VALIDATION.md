# CP-0008Q — Propagation offline report decode and validation

Parent: CP-0008P-PROPAGATION_OFFLINE_REPORT_SERIALIZATION_CONTRACT, main `52453fad9e4c18504394f469fcb8302b5b8f8a49`.

## Contract

`PropagationOfflineReportDecoder.decode(PropagationOfflineSerializedReport)` rejects media-type and wire-version mismatches, byte-count or SHA-256 mismatches, malformed or truncated JSON, duplicate keys, unknown/missing fields, unknown enum values, unsupported coverage shapes, invalid UTF-16 and non-finite/overflow numbers. `decodeCanonical(json)` performs the same typed, canonical and semantic validation without artifact checksum metadata.

The V1 envelope, all typed fields, original source-status timing, nested projections, selected assessment, nullable timestamps, observed/retrieved provenance, future markers, quality sets, enum-keyed freshness maps and evidence index are retained. Canonical re-encoding must match input bytes exactly. A completed domain object is only returned after construction/validation succeeds.

Boundaries match CP-0008P: 16 MiB UTF-8, 48 nesting levels, 100,000 items per collection. GeoCoordinate/GeoBounds and global singleton coverage are now permitted as previously unsupported nested CP-0008P source DTO values without altering previously encodable V1 bytes.

Correlations checked: source/status/diagnostic identities and freshness counts, readiness/wait values, precise age/future markers, diagnostic timestamp relations and differences, evidence retrieval timing, snapshot future/age, aggregate source counts and exact selected evidence-index provenance. Source and cache stores remain independent; no atomicity claim is made.

This uses only JVM/Kotlin core reflection on statically declared safe DTO field types; input cannot choose class names or invoke provider functionality. There are no additional runtime dependencies, Android features, external account calls or RF actions.

A checksum is **not an authenticity signature**: a hostile party may replace an internally consistent report and recompute its digest. Trusted provenance requires a future authenticated transport or signature outside this checkpoint. Freshness reflects the original report's source policy and must not be guessed from the decoder.

Synthetic Kotlin tests cover canonical empty/golden, non-ASCII text and escaping, source failure/backoff, future and absent timestamps, full solar/ionospheric/geo nested projection, checksum/length/version tampering, reordered and malformed JSON, duplicate keys, limits, invalid number formats, Unicode violations, evidence ID drift and source-status inconsistencies. All tests run without network or device.

CP-0003C remains **DEFERRED** until explicitly resumed. CP-0004A/B/C remain incomplete. No phone, radio, credentials, Android lifecycle or RF proof.
