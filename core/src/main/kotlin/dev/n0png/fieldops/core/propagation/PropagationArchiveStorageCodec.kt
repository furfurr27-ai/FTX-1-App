package dev.n0png.fieldops.core.propagation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * CP-0009G deterministic, offline-only archive storage envelope.
 *
 * A checksum proves byte consistency, NOT writer authenticity or source trust.
 * Import ALWAYS reconstructs canonical receipts from the full report JSON.
 * No filesystem, Android context, network, clock, USB, RF or write operation.
 */
object PropagationArchiveStorageCodec {
    const val WIRE_VERSION = 1
    const val MAX_ENVELOPE_BYTES = 4 * 1024 * 1024 + 8192
    private const val MAX_REPORT_BYTES = 4 * 1024 * 1024
    private const val MAX_ENTRIES = 32
    private const val MAX_ARCHIVE_POLICY_BYTES = 64L * 1024 * 1024
    private const val SHA256_BYTES = 32
    private val MAGIC = "FTX1ARCH".toByteArray(StandardCharsets.US_ASCII)

    fun encode(archive: PropagationOfflineReportArchive): ByteArray {
        PropagationOfflineReportArchiveService.validate(archive)
        require(archive.policy.maxEntries <= MAX_ENTRIES) { "Persistence entry policy exceeds bound" }
        require(archive.totalUtf8Bytes <= MAX_REPORT_BYTES.toLong()) {
            "Archive too large for opt-in private-file storage"
        }
        require(archive.policy.maxTotalUtf8Bytes <= MAX_ARCHIVE_POLICY_BYTES) {
            "Persistence byte policy exceeds bound"
        }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(MAGIC)
            output.writeInt(WIRE_VERSION)
            output.writeInt(archive.policy.maxEntries)
            output.writeLong(archive.policy.maxTotalUtf8Bytes)
            output.writeInt(archive.entries.size)
            for (entry in archive.entries) {
                val json = entry.artifact.json.toByteArray(StandardCharsets.UTF_8)
                require(json.size in 1..MAX_REPORT_BYTES) { "Report too large for local archive" }
                output.writeInt(json.size)
                output.write(json)
                // Wire ID is the canonical report's exact untrusted content digest.
                val digest = hexToBytes(entry.id)
                output.write(digest)
            }
        }
        val body = bytes.toByteArray()
        require(body.size + SHA256_BYTES <= MAX_ENVELOPE_BYTES) {
            "Archive exceeds local storage envelope bound"
        }
        return body + sha256(body)
    }

    fun decode(encoded: ByteArray): PropagationOfflineReportArchive {
        require(encoded.size in (MAGIC.size + 4 + 4 + 8 + 4 + SHA256_BYTES)..MAX_ENVELOPE_BYTES) {
            "Archive envelope is empty, truncated, or too large"
        }
        val body = encoded.copyOfRange(0, encoded.size - SHA256_BYTES)
        val checksum = encoded.copyOfRange(encoded.size - SHA256_BYTES, encoded.size)
        require(MessageDigest.isEqual(checksum, sha256(body))) {
            "Archive envelope checksum does not match"
        }
        val input = DataInputStream(ByteArrayInputStream(body))
        val magic = ByteArray(MAGIC.size)
        input.readFully(magic)
        require(magic.contentEquals(MAGIC)) { "Unrecognized archive envelope format" }
        require(input.readInt() == WIRE_VERSION) { "Unsupported archive wire version" }
        val maxEntries = input.readInt()
        val maxBytes = input.readLong()
        require(maxEntries in 1..MAX_ENTRIES && maxBytes in 1..MAX_ARCHIVE_POLICY_BYTES) {
            "Invalid or oversized archive retention policy"
        }
        val count = input.readInt()
        require(count in 0..maxEntries) { "Invalid archive report count" }
        var archive = PropagationOfflineReportArchiveService.empty(
            PropagationOfflineArchivePolicy(maxEntries, maxBytes)
        )
        repeat(count) {
            val length = input.readInt()
            require(length in 1..MAX_REPORT_BYTES && length <= input.available() - SHA256_BYTES) {
                "Invalid or truncated canonical report length"
            }
            val content = ByteArray(length)
            input.readFully(content)
            val expectedDigest = ByteArray(SHA256_BYTES)
            input.readFully(expectedDigest)
            require(MessageDigest.isEqual(expectedDigest, sha256(content))) {
                "Retained report content digest mismatch"
            }
            val json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content)).toString()
            val artifact = PropagationOfflineSerializedReport(
                contentType = PropagationOfflineReportSerialization.CONTENT_TYPE,
                wireVersion = PropagationOfflineReportSerialization.WIRE_VERSION,
                json = json,
                utf8ByteCount = content.size,
                sha256Hex = expectedDigest.joinToString("") { "%02x".format(it.toInt() and 0xff) },
            )
            val receipt = PropagationOfflineReportImportService.importReport(artifact).receipt
            val appended = PropagationOfflineReportArchiveService.append(
                archive, PropagationWorkspaceHistoryCapture(artifact, receipt)
            )
            require(appended.inserted && appended.evictedSha256.isEmpty()) {
                "Stored report identity duplicate or unexpected retention eviction"
            }
            archive = appended.archive
        }
        require(input.available() == 0) { "Trailing unrecognized archive data" }
        return PropagationOfflineReportArchiveService.validate(archive)
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length == 2 * SHA256_BYTES && hex.all {
            it in '0'..'9' || it in 'a'..'f'
        }) { "Invalid canonical report SHA-256 hex" }
        return ByteArray(SHA256_BYTES) { i ->
            hex.substring(2 * i, 2 * i + 2).toInt(16).toByte()
        }
    }
}
