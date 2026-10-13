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
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Separate distinct AES-256-GCM alias for an explicit rotation COPY.
 * Never deletes/invalidates the legacy V2 key. Device hardware status
 * and exact Android Keystore behavior require physical-device testing.
 */
class AndroidKeystoreRotatedArchiveKeyProvider : EncryptedArchiveKeyProvider {
    companion object {
        const val ALIAS = "dev.n0png.fieldops.synthetic-archive-aes-v2-rotated-k1"
        val KEY_IDENTIFIER: ByteArray = MessageDigest.getInstance("SHA-256")
            .digest(ALIAS.toByteArray(Charsets.UTF_8)).copyOf(16)
    }

    override fun existingKey(): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        return ks.getKey(ALIAS, null) as? SecretKey
    }

    override fun createKeyOnExplicitSave(): SecretKey {
        existingKey()?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply { init(spec) }.generateKey()
    }
}

enum class EncryptedRotationStage {
    AFTER_STAGED_SYNC,
    BEFORE_ATOMIC_PUBLISH,
}

fun interface EncryptedRotationFault {
    fun check(stage: EncryptedRotationStage)
}

/**
 * One-shot EXPLICIT rotation to a separate immutable encrypted V2 copy.
 *
 * The original V2 archive and its alias are never mutated, removed,
 * re-keyed, or auto-selected. No legacy plaintext downgrade is allowed.
 * The rotated copy is separately enabled and explicitly restored.
 *
 * A hard-link publish requires atomic exclusive creation, so the existing
 * rotated copy can never be silently overwritten by rename semantics.
 * Unsupported filesystems fail closed; physical power-loss/directory
 * durability are not proven by host or Robolectric tests.
 */
class ExplicitEncryptedArchiveRotation(
    private val context: Context,
    private val legacyEncrypted: OptInEncryptedArchiveStore,
    private val rotatedKeys: EncryptedArchiveKeyProvider =
        AndroidKeystoreRotatedArchiveKeyProvider(),
    private val fault: EncryptedRotationFault = EncryptedRotationFault { },
) {
    companion object {
        const val ROTATED_FILENAME = "fieldops-synthetic-archive-v2-rotated-k1.enc"
    }

    private var enabled = false
    fun enableForThisSession() { enabled = true }
    fun disableForThisSession() { enabled = false }
    fun isEnabled(): Boolean = enabled
    private fun destination() = File(context.noBackupFilesDir, ROTATED_FILENAME)

    private fun readBounded(file: File): ByteArray {
        val cap = AuthenticatedArchiveEnvelopeV2.MAX_ENCRYPTED_BYTES
        require(file.isFile && file.length() in 1L..cap.toLong()) {
            "Rotated archive is missing, malformed or oversized"
        }
        val result = ByteArrayOutputStream()
        FileInputStream(file).use { input ->
            val bytes = ByteArray(8192)
            while (true) {
                val n = input.read(bytes)
                if (n < 0) break
                require(result.size().toLong() + n <= cap.toLong()) {
                    "Rotated archive exceeds bounded size"
                }
                result.write(bytes, 0, n)
            }
        }
        return result.toByteArray()
    }

    private fun open(file: File, key: SecretKey): PropagationOfflineReportArchive {
        val legacy = AuthenticatedArchiveEnvelopeV2.openLegacyV1(
            readBounded(file), key,
            AndroidKeystoreRotatedArchiveKeyProvider.KEY_IDENTIFIER,
        )
        return PropagationArchiveStorageCodec.decode(legacy)
    }

    @Synchronized
    fun rotateExplicitly(): PropagationOfflineReportArchive {
        check(enabled && legacyEncrypted.isEnabled()) {
            "Both source V2 and rotation sessions must be explicitly enabled"
        }
        val target = destination()
        // An existing encrypted candidate is evidence, not disposable output.
        check(!target.exists()) { "Rotated copy exists; use explicit restore, not overwrite" }
        // This step must authenticate source under its ORIGINAL Keystore key
        // BEFORE provisioning a new rotation key.
        val source = legacyEncrypted.loadEncryptedExplicitly()
            ?: throw IOException("No original encrypted V2 file; plaintext fallback forbidden")
        val legacy = PropagationArchiveStorageCodec.encode(source)
        check(!AndroidKeystoreArchiveKeyProvider.KEY_IDENTIFIER.contentEquals(
            AndroidKeystoreRotatedArchiveKeyProvider.KEY_IDENTIFIER)) {
            "Rotated and source key identifiers must differ"
        }
        val key = rotatedKeys.existingKey() ?: rotatedKeys.createKeyOnExplicitSave()
        val ciphertext = AuthenticatedArchiveEnvelopeV2.sealLegacyV1(
            legacy, key, AndroidKeystoreRotatedArchiveKeyProvider.KEY_IDENTIFIER
        )
        val dir = context.noBackupFilesDir
        check(dir.isDirectory || dir.mkdirs()) { "Private no-backup storage unavailable" }
        val stage = Files.createTempFile(dir.toPath(), "fieldops-rotation-stage-", ".tmp")
        try {
            FileOutputStream(stage.toFile()).use {
                it.write(ciphertext)
                it.fd.sync()
            }
            fault.check(EncryptedRotationStage.AFTER_STAGED_SYNC)
            require(open(stage.toFile(), key) == source) {
                "Authenticated staged rotation does not match canonical source"
            }
            fault.check(EncryptedRotationStage.BEFORE_ATOMIC_PUBLISH)
            // createLink(target, stage) is an atomic exclusive publication:
            // FileAlreadyExistsException cannot overwrite older ciphertext.
            try {
                Files.createLink(target.toPath(), stage)
            } catch (e: FileAlreadyExistsException) {
                throw IOException("Concurrent or previous rotated copy retained", e)
            }
            // Read back published ciphertext while still retaining the original.
            check(open(target, key) == source) {
                "Published encrypted rotation copy failed read-back"
            }
        } finally {
            Files.deleteIfExists(stage)
        }
        return source
    }

    fun restoreRotatedExplicitly(): PropagationOfflineReportArchive? {
        check(enabled) { "Explicit rotation session opt-in required" }
        val file = destination()
        if (!file.exists()) return null
        val bytes = readBounded(file)
        // No key generation on restore, nor any fallback to legacy V2 or V1.
        val key = rotatedKeys.existingKey()
            ?: throw EncryptedArchiveKeyUnavailableException("Rotated key unavailable; encrypted copy retained")
        val canonical = AuthenticatedArchiveEnvelopeV2.openLegacyV1(
            bytes, key, AndroidKeystoreRotatedArchiveKeyProvider.KEY_IDENTIFIER
        )
        return PropagationArchiveStorageCodec.decode(canonical)
    }

    /** Explicit cleanup of ONLY the rotated file, never a secure wipe. */
    fun deleteRotatedCopyExplicitly(): Boolean {
        check(enabled) { "Explicit rotation session opt-in required" }
        val file = destination()
        return !file.exists() || file.delete()
    }

    internal fun rotatedFileForTest(): File = destination()
}
