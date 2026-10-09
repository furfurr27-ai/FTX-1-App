# CP-0008P — Propagation offline report serialization contract

Durable parent: CP-0008O-PROPAGATION_OFFLINE_DIAGNOSTIC_REPORT_PAYLOAD.

## Versioned contract

PropagationOfflineReportSerialization.serialize(report) produces a deterministic, read-only canonical JSON UTF-8 string from the complete CP-0008O typed report DTO. This is an offline export, not a live provider or write operation.

- Media type: application/vnd.fieldops.propagation-diagnostic+json;version=1.
- Canonical root object order: format, payload, wireVersion. Format is fieldops.propagation.offline-diagnostic and wireVersion=1. The payload contains CP-0008O schemaVersion=1.
- Domain object fields and map keys sort lexically by name; enum values use stable names. Unordered sets sort by canonical JSON; semantically ordered lists stay in their original order. Integral UTC milliseconds and all source/evidence references remain exact. Unknown timestamps serialize to null.
- Quotation marks, backslashes and controls are JSON-escaped; valid Unicode sequences preserve UTF-8. Unpaired UTF-16 surrogates and non-finite numbers are rejected.
- Maximum UTF-8 output size: 16 MiB; maximum nested depth: 48; maximum collection size: 100,000. Unsupported non-domain object fields are rejected.
- Output includes SHA-256 and exact UTF-8 byte count. SHA-256 is a content integrity digest, not an authenticity signature. verify(report, artifact) regenerates the canonical output and compares it with the supplied artifact, but does not deserialize arbitrary JSON.
- Wire schema V1 is based on all existing typed CP-0008O domain fields. Changes to fields or semantics require a deliberate version bump and revised golden CI fixtures; the reflection-based traversal is restricted to the propagation domain and is NOT an arbitrary-object serializer.

## Validation and boundaries

Before export, the encoder rejects altered summary source counts, incorrect evidence provenance/index, inconsistent cache age/future marker, noncanonical source row ordering, and invented cross-store transaction claims. It does not read stores; the runtime overload obtains the existing report once.

Offline synthetic tests exercise missing cache, unknown timestamps, full nested source status and evidence observations, escaped control/Unicode strings, stale and future evidence, filtered workspace and unfiltered source accounting, tamper rejection, independent SHA-256, byte-for-byte file-backed runtime recreation and no network or state/cache mutation.

This is canonical JSON generation and verification; **not JSON deserialization**, file export/share UI, an Android activity, phone/radio/CAT/audio/RF test, provider health report, new propagation forecast or credential implementation. Independent snapshot/state stores are not atomic across reads.

CP-0003C remains **DEFERRED** until the owner explicitly says resume CP-0003C. CP-0004A/B/C remain incomplete.
