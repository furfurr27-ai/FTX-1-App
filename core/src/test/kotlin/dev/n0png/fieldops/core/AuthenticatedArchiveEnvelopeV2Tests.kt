package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

/** Deterministic AES-GCM host tests; never assert device Keystore/hardware backing. */
object AuthenticatedArchiveEnvelopeV2Tests {
    private var assertions = 0
    private fun yes(ok: Boolean, label: String) { assertions++; check(ok) { label } }
    private fun refuses(label: String, task: () -> Unit) {
        assertions++; check(runCatching(task).isFailure) { "Accepted: $label" }
    }

    @JvmStatic fun main(args: Array<String>) {
        val legacy = PropagationArchiveStorageCodec.encode(
            PropagationOfflineReportArchiveService.empty(
                PropagationOfflineArchivePolicy(maxEntries = 4)
            )
        )
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val id = ByteArray(16) { it.toByte() }
        val wrongId = ByteArray(16) { (it + 1).toByte() }
        val codec = AuthenticatedArchiveEnvelopeV2
        val one = codec.sealLegacyV1(legacy, key, id)
        val two = codec.sealLegacyV1(legacy, key, id)
        yes(!one.contentEquals(two), "separate AES-GCM nonces yield distinct ciphertext")
        val nonceOffset = 8 + 4 + 4 + 16
        val nonce1 = one.copyOfRange(nonceOffset, nonceOffset + codec.NONCE_BYTES)
        val nonce2 = two.copyOfRange(nonceOffset, nonceOffset + codec.NONCE_BYTES)
        yes(!nonce1.contentEquals(nonce2), "provider-generated IVs are independently distinct")
        yes(legacy.contentEquals(codec.openLegacyV1(one, key, id)), "authenticated decrypt")
        yes(legacy.contentEquals(codec.openLegacyV1(two, key, id)), "second nonce decrypt")
        yes(one.copyOfRange(0, 8).contentEquals("FTX1ENC2".toByteArray()),
            "unambiguous encrypted marker")
        refuses("wrong key") { codec.openLegacyV1(one, wrongKey, id) }
        refuses("wrong key identifier") { codec.openLegacyV1(one, key, wrongId) }
        refuses("truncated ciphertext") { codec.openLegacyV1(one.copyOf(one.size - 1), key, id) }
        refuses("trailing bytes") { codec.openLegacyV1(one + byteArrayOf(0), key, id) }
        refuses("unknown wire version") {
            codec.openLegacyV1(one.copyOf().apply {
                ByteBuffer.wrap(this, 8, 4).putInt(3)
            }, key, id)
        }
        refuses("unknown cipher suite") {
            codec.openLegacyV1(one.copyOf().apply {
                ByteBuffer.wrap(this, 12, 4).putInt(99)
            }, key, id)
        }
        refuses("authenticated metadata modification") {
            codec.openLegacyV1(one.copyOf().apply {
                this[16] = (this[16].toInt() xor 1).toByte()
            }, key, one.copyOfRange(16, 32))
        }
        refuses("nonce authentication failure") {
            codec.openLegacyV1(one.copyOf().apply {
                this[32] = (this[32].toInt() xor 1).toByte()
            }, key, id)
        }
        refuses("ciphertext/tag modification") {
            codec.openLegacyV1(one.copyOf().apply {
                this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
            }, key, id)
        }
        refuses("oversized envelope") {
            codec.openLegacyV1(ByteArray(codec.MAX_ENCRYPTED_BYTES + 1), key, id)
        }
        refuses("noncanonical plaintext never encrypted") {
            codec.sealLegacyV1(legacy.copyOfRange(0, legacy.size - 1), key, id)
        }
        refuses("bad identifier on save") { codec.sealLegacyV1(legacy, key, ByteArray(3)) }
        refuses("weak 128-bit key not silently accepted as AES-256") {
            // Keep rejecting inspectable undersized AES keys (opaque Keystore keys have no encoded bytes).
            val weak = SecretKeySpec(ByteArray(16), "AES")
            codec.sealLegacyV1(legacy, weak, id)
        }
        yes(legacy.contentEquals(PropagationArchiveStorageCodec.encode(
            PropagationArchiveStorageCodec.decode(legacy))), "unchanged legacy envelope")
        println("CP-0009J AES-GCM host envelope PASS: $assertions assertions")
    }
}
