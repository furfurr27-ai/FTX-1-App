package dev.n0png.fieldops.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.n0png.fieldops.core.propagation.AuthenticatedArchiveEnvelopeV2
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
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Keystore access is deliberately separated for isolated Robolectric tests.
 * Production creation ONLY occurs from explicit save/migration actions.
 * Restore must never create a replacement key for unreadable ciphertext.
 */
interface EncryptedArchiveKeyProvider {
    fun existingKey(): SecretKey?
    fun createKeyOnExplicitSave(): SecretKey
}

class AndroidKeystoreArchiveKeyProvider : EncryptedArchiveKeyProvider {
    companion object {
        const val ALIAS = "dev.n0png.fieldops.synthetic-archive-aes-v2"
        val KEY_IDENTIFIER: ByteArray = MessageDigest.getInstance("SHA-256")
            .digest(ALIAS.toByteArray(Charsets.UTF_8)).copyOf(16)
    }

    override fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        return store.getKey(ALIAS, null) as? SecretKey
    }

    override fun createKeyOnExplicitSave(): SecretKey {
        existingKey()?.let { return it }
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(builder.build())
        return generator.generateKey()
    }
}

/**
 * Separate opt-in V2 file. Never modifies the existing plaintext V1 file,
 * never auto-restores, never downgrades, and never creates keys on restore.
 * Atomic rename is required, but physical power-loss durability is unproven.
 */
class OptInEncryptedArchiveStore(
    private val context: Context,
    private val keys: EncryptedArchiveKeyProvider = AndroidKeystoreArchiveKeyProvider(),
    private val preCommitFault: ArchivePreCommitFault = ArchivePreCommitFault { },
) {
    companion object {
        private const val FILENAME = "fieldops-synthetic-archive-v2.enc"
    }
    private var sessionEnabled = false
    fun enableForThisSession() { sessionEnabled = true }
    fun disableForThisSession() { sessionEnabled = false }
    fun isEnabled(): Boolean = sessionEnabled

    private fun destination() = File(context.noBackupFilesDir, FILENAME)

    private fun readBounded(file: File): ByteArray {
        val cap = AuthenticatedArchiveEnvelopeV2.MAX_ENCRYPTED_BYTES
        require(file.isFile && file.length() in 1L..cap.toLong()) {
            "Encrypted file is missing, malformed, or oversized"
        }
        val output = ByteArrayOutputStream()
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(output.size().toLong() + n <= cap.toLong()) {
                    "Encrypted file exceeds bounded read size"
                }
                output.write(buffer, 0, n)
            }
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray, key: SecretKey): PropagationOfflineReportArchive {
        val legacy = AuthenticatedArchiveEnvelopeV2.openLegacyV1(
            bytes, key, AndroidKeystoreArchiveKeyProvider.KEY_IDENTIFIER
        )
        return PropagationArchiveStorageCodec.decode(legacy)
    }

    fun saveEncryptedExplicitly(archive: PropagationOfflineReportArchive) {
        check(sessionEnabled) { "Explicit encrypted archive session opt-in required" }
        val legacy = PropagationArchiveStorageCodec.encode(archive)
        val target = destination()
        val dir = target.parentFile ?: throw IOException("Missing private directory")
        check(dir.isDirectory || dir.mkdirs()) { "Private no-backup directory unavailable" }

        // Verify the existing ciphertext before allowing replacement; never
        // silently reset a missing/invalid Keystore key or overwrite corrupt data.
        val existing = if (target.exists()) readBounded(target) else null
        val key = if (existing != null) {
            keys.existingKey()
                ?: throw IOException("Encrypted archive key unavailable; original bytes retained")
        } else {
            keys.existingKey() ?: keys.createKeyOnExplicitSave()
        }
        if (existing != null) {
            try {
                decode(existing, key)
            } catch (e: Exception) {
                throw IOException(
                    "Existing encrypted archive failed authentication; bytes retained",
                    e,
                )
            }
        }

        val ciphertext = AuthenticatedArchiveEnvelopeV2.sealLegacyV1(
            legacy, key, AndroidKeystoreArchiveKeyProvider.KEY_IDENTIFIER
        )
        val staged = Files.createTempFile(dir.toPath(), "fieldops-v2-stage-", ".tmp")
        try {
            FileOutputStream(staged.toFile()).use { output ->
                output.write(ciphertext)
                output.fd.sync()
            }
            preCommitFault.check(ArchivePreCommitStage.AFTER_STAGED_SYNC)
            // Validate staged bytes before commit; old V1 and V2 remain intact.
            require(decode(readBounded(staged.toFile()), key) == archive) {
                "Staged encrypted archive failed canonical verification"
            }
            preCommitFault.check(ArchivePreCommitStage.BEFORE_ATOMIC_REPLACE)
            try {
                Files.move(staged, target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                throw IOException("Atomic V2 archive replacement not supported", e)
            }
        } finally {
            Files.deleteIfExists(staged)
        }
    }

    fun loadEncryptedExplicitly(): PropagationOfflineReportArchive? {
        check(sessionEnabled) { "Explicit encrypted archive session opt-in required" }
        val target = destination()
        if (!target.exists()) return null
        // Do NOT provision a new key here, and do NOT fall back to plaintext V1.
        val key = keys.existingKey()
            ?: throw IOException("Encryption key missing; ciphertext retained without downgrade")
        return decode(readBounded(target), key)
    }

    /** Deletes only the V2 file; this is NOT secure erase or key retirement. */
    fun deleteEncryptedExplicitly(): Boolean {
        check(sessionEnabled) { "Explicit encrypted archive session opt-in required" }
        val target = destination()
        return !target.exists() || target.delete()
    }

    internal fun localEncryptedFileForTest(): File = destination()
}
