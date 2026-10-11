package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.ByteBuffer

/** CP-0009I simulated fail-closed preflight; NOT encryption proof. */
object EncryptedArchiveMigrationContractTests {
    private var count = 0
    private fun same(want: Any, got: Any, label: String) {
        count++
        check(want == got) { "$label: expected $want but got $got" }
    }
    @JvmStatic fun main(args: Array<String>) {
        val empty = PropagationOfflineReportArchiveService.empty(
            PropagationOfflineArchivePolicy(maxEntries = 4)
        )
        val v1 = PropagationArchiveStorageCodec.encode(empty)
        val c = EncryptedArchiveMigrationContract
        val allowed = ArchiveMigrationPrerequisites(true, true, true)
        same(ArchiveStorageClassification.VALID_LEGACY_CLEAR_V1,
            c.classify(v1), "canonical v1 recognized")
        same(ArchiveMigrationDecision.ENCRYPTED_EXECUTOR_NOT_IMPLEMENTED,
            c.assess(v1, allowed), "all conditions never enable encryption")
        same(ArchiveMigrationDecision.REQUIRE_USER_OPT_IN,
            c.assess(v1, allowed.copy(explicitUserOptIn = false)), "no automatic migration")
        same(ArchiveMigrationDecision.REQUIRE_AVAILABLE_KEY,
            c.assess(v1, allowed.copy(keystoreKeyAvailable = false)), "lost key")
        same(ArchiveMigrationDecision.REQUIRE_ATOMIC_REPLACE,
            c.assess(v1, allowed.copy(atomicReplaceSupported = false)),
            "no non-atomic fallback")
        val tampered = v1.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        same(ArchiveStorageClassification.CORRUPT_LEGACY_CLEAR_V1,
            c.classify(tampered), "checksum tamper")
        same(ArchiveMigrationDecision.REJECT_CORRUPT_LEGACY,
            c.assess(tampered, allowed), "corrupt source kept untouched")
        val forged = v1.copyOf().apply { ByteBuffer.wrap(this, 8, 4).putInt(2) }
        same(ArchiveMigrationDecision.REJECT_CORRUPT_LEGACY,
            c.assess(forged, allowed), "forged v1 version")
        val future = "FTX1ENC2".toByteArray(Charsets.US_ASCII) +
            ByteBuffer.allocate(4).putInt(2).array() + ByteArray(64)
        same(ArchiveStorageClassification.V2_RESERVED_UNREADABLE,
            c.classify(future), "reserved magic never authenticated")
        same(ArchiveMigrationDecision.REJECT_RESERVED_V2_UNREADABLE,
            c.assess(future, allowed), "no v2 fallback")
        same(ArchiveMigrationDecision.REJECT_UNSUPPORTED_OR_DOWNGRADE,
            c.assess("UNKNOWN!".toByteArray(), allowed), "unknown format")
        same(ArchiveMigrationDecision.REJECT_OVER_LIMIT,
            c.assess(ByteArray(c.MAX_RESERVED_ENCRYPTED_BYTES + 1), allowed),
            "reserved envelope cap")
        same(ArchiveMigrationDecision.REJECT_RESERVED_V2_UNREADABLE,
            c.assessRotationOrRollback(), "no automatic rotation or downgrade")
        same(v1.toList(), PropagationArchiveStorageCodec.encode(
            PropagationArchiveStorageCodec.decode(v1)).toList(), "legacy unchanged")
        println("CP-0009I encrypted archive migration contract PASS: $count assertions")
    }
}
