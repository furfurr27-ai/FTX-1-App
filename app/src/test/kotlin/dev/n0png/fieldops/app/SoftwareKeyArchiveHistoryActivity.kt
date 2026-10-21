package dev.n0png.fieldops.app

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Robolectric-only Activity fixture for CP-0009P.
 * Never compiled into production APK. The production Activity still defaults
 * to AndroidKeyStore and refuses to create replacement keys on restore.
 */
class SoftwareKeyArchiveHistoryActivity : ArchiveHistoryActivity() {
    private class ExplicitSaveOnlyFixture(private val fill: Byte) : EncryptedArchiveKeyProvider {
        private var created: SecretKey? = null
        override fun existingKey(): SecretKey? = created
        override fun createKeyOnExplicitSave(): SecretKey {
            return created ?: SecretKeySpec(ByteArray(32) { fill }, "AES").also {
                created = it
            }
        }
    }

    private val original = ExplicitSaveOnlyFixture(0x31)
    private val rotated = ExplicitSaveOnlyFixture(0x42)

    override fun originalArchiveKeyProvider(): EncryptedArchiveKeyProvider = original
    override fun rotatedArchiveKeyProvider(): EncryptedArchiveKeyProvider = rotated
}
