package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.ByteBuffer
import java.security.MessageDigest

/** CP-0009G deterministic storage bytes, tamper/recovery and bounded opt-in contract. */
object PropagationArchiveStorageCodecTests {
    private var assertions = 0
    private fun yes(ok: Boolean, label: String) {
        assertions++
        check(ok) { label }
    }
    private fun same(a: Any?, b: Any?, label: String) {
        assertions++
        check(a == b) { "$label: $a != $b" }
    }
    private fun refuses(label: String, action: () -> Unit) {
        assertions++
        check(runCatching(action).isFailure) { "Accepted: $label" }
    }
    private fun picture(t: Long) = PropagationOperatingPictureService(
        InMemoryPropagationSnapshotStore(), InMemoryPropagationRefreshStateStore()
    ).read(PropagationProjectionQuery(t))

    private fun withRecomputedChecksum(mutated: ByteArray): ByteArray {
        val body = mutated.copyOfRange(0, mutated.size - 32)
        val hash = MessageDigest.getInstance("SHA-256").digest(body)
        return body + hash
    }

    @JvmStatic fun main(args: Array<String>) {
        val policy = PropagationOfflineArchivePolicy(maxEntries = 4)
        val service = PropagationArchiveWorkspaceService
        val empty = service.open(policy)
        val codec = PropagationArchiveStorageCodec
        val sealedEmpty = codec.encode(empty.archive)
        same(empty.archive, codec.decode(sealedEmpty), "empty history canonical roundtrip")
        same(sealedEmpty.toList(), codec.encode(codec.decode(sealedEmpty)).toList(),
            "empty history deterministic wire")
        val a = service.apply(empty, PropagationArchiveWorkspaceAction.Capture(picture(2_001_000L)))
        val b = service.apply(a, PropagationArchiveWorkspaceAction.Capture(picture(2_002_000L)))
        val c = service.apply(b, PropagationArchiveWorkspaceAction.Capture(picture(2_003_000L)))
        val wire = codec.encode(c.archive)
        val restored = codec.decode(wire)
        same(c.archive, restored, "exact canonical insertion order and immutable receipt")
        same(wire.toList(), codec.encode(restored).toList(), "deterministic encoding")
        same(service.screen(c).rows.map { it.sha256Hex },
            service.screen(c.copy(archive = restored)).rows.map { it.sha256Hex },
            "canonical UTC sorted view maintained after restore")
        yes(restored.entries.all { it.receipt.originAuthenticated == false },
            "checksum never authenticates station origin")
        yes(restored.entries.all { it.receipt.crossStoreAtomicityVerified == false },
            "checksum cannot claim cross-store transaction")
        refuses("empty bytes") { codec.decode(byteArrayOf()) }
        refuses("truncation") { codec.decode(wire.copyOfRange(0, wire.size - 1)) }
        refuses("trailing garbage") { codec.decode(wire + byteArrayOf(0)) }
        val flipped = wire.copyOf().apply { this[size / 2] = (this[size / 2].toInt() xor 1).toByte() }
        refuses("data corruption") { codec.decode(flipped) }
        val modifiedDigest = wire.copyOf().apply {
            val offset = 8 + 4 + 4 + 8 + 4 + 4 +
                c.archive.entries[0].artifact.utf8ByteCount
            this[offset] = (this[offset].toInt() xor 1).toByte()
        }
        refuses("retained report tamper despite envelope rechecksum") {
            codec.decode(withRecomputedChecksum(modifiedDigest))
        }
        val badVersion = wire.copyOf().apply {
            ByteBuffer.wrap(this, 8, 4).putInt(77)
        }
        refuses("unknown wire version even when intact") {
            codec.decode(withRecomputedChecksum(badVersion))
        }
        val badCount = wire.copyOf().apply {
            ByteBuffer.wrap(this, 24, 4).putInt(128)
        }
        refuses("forged count") { codec.decode(withRecomputedChecksum(badCount)) }
        val tooSmall = wire.copyOf().apply {
            ByteBuffer.wrap(this, 8 + 4 + 4, 8).putLong(1L)
        }
        refuses("budget lower than archive retained content") {
            codec.decode(withRecomputedChecksum(tooSmall))
        }
        refuses("huge envelope") {
            codec.decode(ByteArray(codec.MAX_ENVELOPE_BYTES + 1))
        }
        refuses("policy outside local max") {
            codec.encode(service.open(PropagationOfflineArchivePolicy(maxEntries = 100)).archive)
        }
        same(restored, codec.decode(wire), "previously valid snapshot not mutated by failures")
        println("CP-0009G canonical archive storage codec PASS: $assertions assertions")
    }
}
