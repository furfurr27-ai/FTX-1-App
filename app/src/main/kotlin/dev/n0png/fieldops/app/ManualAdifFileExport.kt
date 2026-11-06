package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.logbook.AdifCodec
import dev.n0png.fieldops.core.logbook.QsoRecord
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Produce multiple independently restorable ASCII ADIF parts when the volatile
 * contact log is larger than the FieldOps strict one-document restore limits.
 *
 * Preparation runs BEFORE the Android document picker. No document is
 * intentionally written unless its complete contents have already passed the
 * same strict parser/size/identity checks used by FieldOps restoration.
 */
internal object ManualAdifFileExport {
    class Batch internal constructor(
        val records: List<QsoRecord>,
        private val content: ByteArray,
    ) {
        val byteCount: Int get() = content.size
        internal fun writeTo(output: OutputStream): Int {
            output.write(content)
            output.flush()
            return records.size
        }
        internal fun bytes(): ByteArray = content.copyOf()

        /**
         * SAF can report a successful write even when the provider stored
         * truncated or altered bytes. Reopen the selected URI and compare the
         * exact preflighted ADIF bytes BEFORE advancing the backup part.
         * This verifies the immediate provider read-back, not long-term
         * durability or a lossless backup of all internal QSO metadata.
         */
        internal fun verifyStored(input: InputStream) {
            val buffer = ByteArray(8192)
            var offset = 0
            while (offset < content.size) {
                val read = input.read(buffer, 0, minOf(buffer.size, content.size - offset))
                require(read > 0) { "Saved ADIF document is truncated or unreadable" }
                for (i in 0 until read) {
                    require(buffer[i] == content[offset + i]) {
                        "Saved ADIF document differs from prepared backup"
                    }
                }
                offset += read
            }
            require(input.read() == -1) { "Saved ADIF document contains trailing bytes" }
        }
    }

    fun plan(records: List<QsoRecord>, expectedStation: String): List<Batch> {
        require(records.isNotEmpty()) { "Cannot export an empty QSO snapshot" }
        val headerSize = AdifCodec.export(emptyList()).toByteArray(Charsets.US_ASCII).size
        val batches = ArrayList<Batch>()
        val pending = ArrayList<QsoRecord>(FieldOpsArchiveBounds.MAX_RECORDS)
        var bytesUsed = headerSize

        fun finishBatch() {
            if (pending.isEmpty()) return
            val snapshot = pending.toList()
            val content = AdifCodec.export(snapshot).toByteArray(Charsets.UTF_8)
            require(content.size <= FieldOpsArchiveBounds.MAX_BYTES &&
                snapshot.size <= FieldOpsArchiveBounds.MAX_RECORDS) {
                "Prepared ADIF batch exceeds restore limits"
            }
            // Validation is all-or-nothing: a FieldOps export must be
            // accepted by FieldOps' own strict restore reader, without QSO
            // mutation, BEFORE the user selects a file destination.
            val parsed = FieldOpsAdifRestore.read(ByteArrayInputStream(content), expectedStation)
            require(parsed.size == snapshot.size) { "ADIF preflight lost contacts" }
            batches.add(Batch(snapshot, content))
            pending.clear()
            bytesUsed = headerSize
        }

        for (record in records) {
            val row = AdifCodec.export(listOf(record)).toByteArray(Charsets.UTF_8)
            require(row.all { (it.toInt() and 255) < 128 }) {
                "Non-ASCII ADIF cannot be restored safely; fix metadata before exporting"
            }
            val recordBytes = row.size - headerSize
            require(recordBytes > 0 && recordBytes + headerSize <= FieldOpsArchiveBounds.MAX_BYTES) {
                "A single ADIF record exceeds the 1 MB restore limit"
            }
            if (pending.size == FieldOpsArchiveBounds.MAX_RECORDS ||
                bytesUsed + recordBytes > FieldOpsArchiveBounds.MAX_BYTES) {
                finishBatch()
            }
            pending.add(record)
            bytesUsed += recordBytes
        }
        finishBatch()
        return batches
    }

    fun write(batch: Batch, output: OutputStream): Int = batch.writeTo(output)

    fun verifySaved(batch: Batch, reopenedDocument: InputStream) =
        batch.verifyStored(reopenedDocument)

    /** Single-document compatibility contract for existing callers. */
    fun write(records: List<QsoRecord>, output: OutputStream): Int {
        require(records.isNotEmpty()) { "Cannot export an empty QSO snapshot" }
        val batches = plan(records, records.first().stationCallsign)
        require(batches.size == 1) {
            "Export requires multiple ADIF parts; select separate documents"
        }
        return batches.single().writeTo(output)
    }
}
