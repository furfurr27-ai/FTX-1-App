package dev.n0png.fieldops.core.propagation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * CP-0008U strict, bounded offline comparison export decoder.
 *
 * A valid unkeyed SHA-256 proves only consistency of supplied bytes and receipt,
 * not provenance. Selected-view rows are NOT live source or RF observations.
 * Full projection-change recomputation needs both original CP-0008P artifacts.
 */
object PropagationOfflineReportComparisonDecoder {
    /** Require complete V1 metadata; bare JSON is not an authenticated import. */
    fun decode(encoded: PropagationOfflineSerializedComparison):
        PropagationOfflineReportComparison {
        require(encoded.contentType == PropagationOfflineReportComparisonSerialization.CONTENT_TYPE) {
            "Incompatible comparison media type"
        }
        require(encoded.wireVersion == PropagationOfflineReportComparisonSerialization.WIRE_VERSION) {
            "Incompatible comparison metadata version"
        }
        val bytes = checkedBytes(encoded.json)
        require(encoded.utf8ByteCount == bytes.size) { "Comparison UTF-8 length mismatch" }
        require(encoded.sha256Hex.matches(Regex("[0-9a-f]{64}"))) {
            "Invalid comparison digest format"
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
        val expected = ByteArray(32) { i ->
            encoded.sha256Hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        require(MessageDigest.isEqual(actual, expected)) { "Comparison SHA-256 mismatch" }
        val decoded = decodeCanonical(encoded.json)
        require(PropagationOfflineReportComparisonSerialization.serialize(decoded) == encoded) {
            "Comparison metadata differs from canonical wire"
        }
        return decoded
    }

    /** Lower-level raw canonical JSON entrypoint, without supplied receipt metadata. */
    fun decodeCanonical(json: String): PropagationOfflineReportComparison {
        checkedBytes(json)
        val decoded = PropagationOfflineReportDecoder.decodeComparisonPayloadCanonical(json)
        // CP-0008T's original structural invariants are authoritative:
        // receipts, order, classifications, evidence identity and flags.
        // Byte equality also rejects alternate escapes, key order, whitespace,
        // numeric spellings and extra or missing DTO fields.
        require(PropagationOfflineReportComparisonSerialization.serialize(decoded).json == json) {
            "Comparison is not canonical V1"
        }
        return decoded
    }

    private fun checkedBytes(json: String): ByteArray {
        val limit = PropagationOfflineReportComparisonSerialization.MAX_UTF8_BYTES
        require(json.length <= limit) { "Comparison character limit exceeded" }
        var i = 0
        while (i < json.length) {
            val ch = json[i]
            if (ch.isHighSurrogate()) {
                require(i + 1 < json.length && json[i + 1].isLowSurrogate()) {
                    "Unpaired comparison high surrogate"
                }
                i++
            } else {
                require(!ch.isLowSurrogate()) { "Unpaired comparison low surrogate" }
            }
            i++
        }
        return json.toByteArray(StandardCharsets.UTF_8).also {
            require(it.size <= limit) { "Comparison UTF-8 limit exceeded" }
        }
    }
}
