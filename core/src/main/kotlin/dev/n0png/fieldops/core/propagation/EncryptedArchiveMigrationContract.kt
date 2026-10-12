package dev.n0png.fieldops.core.propagation

import java.nio.charset.StandardCharsets

/**
 * CP-0009I offline-only, NON-EXECUTING migration preflight.
 * No cryptography, Android Keystore, persisted write, decryption, or migration
 * is implemented here. Recognizing a v2 prefix is NOT authentication.
 */
enum class ArchiveStorageClassification {
    VALID_LEGACY_CLEAR_V1,
    CORRUPT_LEGACY_CLEAR_V1,
    V2_RESERVED_UNREADABLE,
    UNSUPPORTED_OR_TRUNCATED,
    OVER_LIMIT,
}

enum class ArchiveMigrationDecision {
    REJECT_CORRUPT_LEGACY,
    REJECT_UNSUPPORTED_OR_DOWNGRADE,
    REJECT_OVER_LIMIT,
    REJECT_RESERVED_V2_UNREADABLE,
    REQUIRE_USER_OPT_IN,
    REQUIRE_AVAILABLE_KEY,
    REQUIRE_ATOMIC_REPLACE,
    ENCRYPTED_EXECUTOR_NOT_IMPLEMENTED,
}

/** Simulated conditions: booleans cannot attest to genuine hardware keys. */
data class ArchiveMigrationPrerequisites(
    val explicitUserOptIn: Boolean,
    val keystoreKeyAvailable: Boolean,
    val atomicReplaceSupported: Boolean,
)

/** An archive must pass canonical receipt validation before a migration plan. */
object EncryptedArchiveMigrationContract {
    const val RESERVED_ENCRYPTED_WIRE_VERSION = 2
    const val MAX_RESERVED_ENCRYPTED_BYTES =
        PropagationArchiveStorageCodec.MAX_ENVELOPE_BYTES + 4096
    private val LEGACY_MAGIC = "FTX1ARCH".toByteArray(StandardCharsets.US_ASCII)
    private val V2_MAGIC = "FTX1ENC2".toByteArray(StandardCharsets.US_ASCII)

    fun classify(source: ByteArray): ArchiveStorageClassification {
        if (source.size > MAX_RESERVED_ENCRYPTED_BYTES) {
            return ArchiveStorageClassification.OVER_LIMIT
        }
        if (source.size < LEGACY_MAGIC.size + 4) {
            return ArchiveStorageClassification.UNSUPPORTED_OR_TRUNCATED
        }
        val prefix = source.copyOfRange(0, LEGACY_MAGIC.size)
        if (prefix.contentEquals(V2_MAGIC)) {
            return ArchiveStorageClassification.V2_RESERVED_UNREADABLE
        }
        if (!prefix.contentEquals(LEGACY_MAGIC)) {
            return ArchiveStorageClassification.UNSUPPORTED_OR_TRUNCATED
        }
        return try {
            PropagationArchiveStorageCodec.decode(source)
            ArchiveStorageClassification.VALID_LEGACY_CLEAR_V1
        } catch (_: IllegalArgumentException) {
            ArchiveStorageClassification.CORRUPT_LEGACY_CLEAR_V1
        } catch (_: java.io.IOException) {
            ArchiveStorageClassification.CORRUPT_LEGACY_CLEAR_V1
        }
    }

    /** Never grants permission to write: cryptographic execution is future work. */
    fun assess(
        source: ByteArray,
        prerequisites: ArchiveMigrationPrerequisites,
    ): ArchiveMigrationDecision {
        return when (classify(source)) {
            ArchiveStorageClassification.OVER_LIMIT ->
                ArchiveMigrationDecision.REJECT_OVER_LIMIT
            ArchiveStorageClassification.CORRUPT_LEGACY_CLEAR_V1 ->
                ArchiveMigrationDecision.REJECT_CORRUPT_LEGACY
            ArchiveStorageClassification.UNSUPPORTED_OR_TRUNCATED ->
                ArchiveMigrationDecision.REJECT_UNSUPPORTED_OR_DOWNGRADE
            ArchiveStorageClassification.V2_RESERVED_UNREADABLE ->
                ArchiveMigrationDecision.REJECT_RESERVED_V2_UNREADABLE
            ArchiveStorageClassification.VALID_LEGACY_CLEAR_V1 -> when {
                !prerequisites.explicitUserOptIn ->
                    ArchiveMigrationDecision.REQUIRE_USER_OPT_IN
                !prerequisites.keystoreKeyAvailable ->
                    ArchiveMigrationDecision.REQUIRE_AVAILABLE_KEY
                !prerequisites.atomicReplaceSupported ->
                    ArchiveMigrationDecision.REQUIRE_ATOMIC_REPLACE
                else -> ArchiveMigrationDecision.ENCRYPTED_EXECUTOR_NOT_IMPLEMENTED
            }
        }
    }

    /** Neither rotation nor ciphertext downgrade is implemented or authorized. */
    fun assessRotationOrRollback(): ArchiveMigrationDecision =
        ArchiveMigrationDecision.REJECT_RESERVED_V2_UNREADABLE
}
