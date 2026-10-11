package dev.n0png.fieldops.app

import android.content.Context
import dev.n0png.fieldops.core.propagation.PropagationArchiveStorageCodec
import dev.n0png.fieldops.core.propagation.PropagationOfflineReportArchive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Explicit opt-in, bounded Android private file store for OFFLINE report artifacts.
 * No automatic restore, save, background service or external/cloud/backup storage.
 * SHA-256 is a corruption check, not encryption or source authentication.
 *
 * Atomic file replacement prevents exposing a partially written target; power-loss
 * durability (directory fsync) and physical-device behavior are NOT proven by JVM CI.
 */
/**
 * Injectable *test-only* pre-commit failures. Injection never makes an actual
 * save on the device by itself. Production always uses the no-op hook.
 */
enum class ArchivePreCommitStage { AFTER_STAGED_SYNC, BEFORE_ATOMIC_REPLACE }
fun interface ArchivePreCommitFault {
    fun check(stage: ArchivePreCommitStage)
}

class OptInArchiveStore(
    private val context: Context,
    private val preCommitFault: ArchivePreCommitFault = ArchivePreCommitFault { },
) {
    private val filename = "fieldops-synthetic-archive-v1.bin"
    private var enabled = false

    fun enableForThisSession() { enabled = true }
    fun disableForThisSession() { enabled = false }
    fun isEnabled(): Boolean = enabled

    fun saveExplicitly(archive: PropagationOfflineReportArchive) {
        check(enabled) { "Explicit session opt-in required to save archive" }
        val bytes = PropagationArchiveStorageCodec.encode(archive)
        val directory = context.noBackupFilesDir
        check(directory.isDirectory || directory.mkdirs()) { "Private app directory unavailable" }
        val destination = File(directory, filename)
        // Do not silently overwrite corrupt local evidence with new synthetic data.
        // Recovery requires an explicit user-triggered delete of the unreadable file.
        if (destination.exists()) {
            require(destination.isFile && destination.length() in
                1L..PropagationArchiveStorageCodec.MAX_ENVELOPE_BYTES.toLong()) {
                "Existing archive is unreadable; delete it explicitly before saving"
            }
            try {
                PropagationArchiveStorageCodec.decode(readBounded(destination))
            } catch (invalid: IllegalArgumentException) {
                throw IOException(
                    "Existing archive failed integrity checks; delete it explicitly before saving",
                    invalid,
                )
            }
        }
        val temporary = Files.createTempFile(directory.toPath(), "fieldops-stage-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            preCommitFault.check(ArchivePreCommitStage.AFTER_STAGED_SYNC)
            try {
                preCommitFault.check(ArchivePreCommitStage.BEFORE_ATOMIC_REPLACE)
                Files.move(
                    temporary,
                    destination.toPath(),
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
        // Full checksum, canonical import and receipt revalidation before value is returned.
        return PropagationArchiveStorageCodec.decode(readBounded(file))
    }

    /** Reject growth during reading; a pre-read file length check alone is not a bound. */
    private fun readBounded(file: File): ByteArray {
        val maxBytes = PropagationArchiveStorageCodec.MAX_ENVELOPE_BYTES
        require(file.isFile && file.length() in 1L..maxBytes.toLong()) {
            "Retained archive exceeds bounded private-file limit"
        }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
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
        return output.toByteArray()
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
