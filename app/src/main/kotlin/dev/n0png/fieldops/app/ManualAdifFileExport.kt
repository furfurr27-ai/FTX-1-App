package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.logbook.AdifCodec
import dev.n0png.fieldops.core.logbook.QsoRecord
import java.io.OutputStream

/**
 * Export a user-selected snapshot of authoritative manual contacts as ADIF.
 *
 * Receives an explicit SAF-created document stream; it never opens an account,
 * mutates the QSO repository, sends LoTW requests, or asserts confirmation.
 * OutputStream ownership stays with the caller for reliable close on failure.
 */
internal object ManualAdifFileExport {
    fun write(records: List<QsoRecord>, output: OutputStream): Int {
        require(records.isNotEmpty()) { "Cannot export an empty QSO snapshot" }
        output.write(AdifCodec.export(records).toByteArray(Charsets.UTF_8))
        output.flush()
        return records.size
    }
}
