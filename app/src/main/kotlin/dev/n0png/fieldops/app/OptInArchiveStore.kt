package dev.n0png.fieldops.app

import android.content.Context
import dev.n0png.fieldops.core.propagation.PropagationArchiveStorageCodec
import dev.n0png.fieldops.core.propagation.PropagationOfflineReportArchive
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Internal CI-controlled failure points; never exposed to production UI. */
internal enum class ArchiveSaveFaultPoint {
    NONE,
    AFTER_STAGED_FILE_SYNC,
    ATOMIC_RENAME_UNAVAILABLE,
}

/**
 * Explicit opt-in, bounded Android private file store for OFFLINE report artifacts.
 * No automatic restore, save, background service or external/cloud/backup storage.
 * SHA-256 is a corruption check, not encryption or source authentication.
 *
 * Atomic file replacement prevents exposing a partially written target; power-loss
 * durability (directory fsync) and physical-device behavior are NOT proven by JVM CI.
 */
class OptInArchiveStore(private val context: Context) {
    private val filename = "fieldops-synthetic-archive-v1.bin"
    private var enabled = false

    fun enableForThisSession() { enabled = true }
    fun disableForThisSession() { enabled = false }
    fun isEnabled(): Boolean = enabled

    fun saveExplicitly(archive: PropagationOfflineReportArchive) {
        saveWithInjectedFault(archive, ArchiveSaveFaultPoint.NONE)
    }

    internal fun saveWithInjectedFault(
        archive: PropagationOfflineReportArchive,
        failAt: ArchiveSaveFaultPoint,
    ) {
        check(enabled) { "Explicit session opt-in required to save archive" }
        val bytes = PropagationArchiveStorageCodec.encode(archive)
        val directory = context.noBackupFilesDir
        check(directory.isDirectory || directory.mkdirs()) { "Private app directory unavailable" }
        val temporary = Files.createTempFile(directory.toPath(), "fieldops-stage-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            if (failAt == ArchiveSaveFaultPoint.AFTER_STAGED_FILE_SYNC) {
                throw IOException("Simulated staged-file failure before atomic replacement")
            }
            try {
                if (failAt == ArchiveSaveFaultPoint.ATOMIC_RENAME_UNAVAILABLE) {
                    throw AtomicMoveNotSupportedException(
                        temporary.toString(), File(directory, filename).path,
                        "Simulated unavailable atomic rename",
                    )
                }
                Files.move(
                    temporary,
                    File(directory, filename).toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (missing: AtomicMoveNotSupportedException) {
                throw IOException("Atomic private-file replacement unavailable; previous save retained", missing)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun loadExplicitly(): PropagationOfflineReportArchive? {
        check(enabled) { "Explicit session opt-in required to load archive" }
        val file = File(context.noBackupFilesDir, filename)
        if (!file.exists()) return null
        require(file.isFile && file.length() in 1L..PropagationArchiveStorageCodec.MAX_ENVELOPE_BYTES.toLong()) {
            "Retained archive exceeds bounded private-file limit"
        }
        // Full checksum, canonical import and receipt revalidation before value is returned.
        val maxBytes = PropagationArchiveStorageCodec.MAX_ENVELOPE_BYTES
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        FileInputStream(file).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= maxBytes.toLong()) {
                    "Saved archive changed or exceeded its bounded read limit"
                }
                output.write(buffer, 0, count)
            }
        }
        return PropagationArchiveStorageCodec.decode(output.toByteArray())
    }

    /** User-requested app-private deletion; not a secure data-wipe guarantee. */
    fun deleteExplicitly(): Boolean {
        check(enabled) { "Explicit session opt-in required to delete archive" }
        val file = File(context.noBackupFilesDir, filename)
        return !file.exists() || file.delete()
    }

    /** For app-private synthetic CI corruption tests only; NOT a public file path. */
    internal fun localFileForTest(): File = File(context.noBackupFilesDir, filename)
}
