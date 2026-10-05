package dev.n0png.fieldops.core.aprs

/** AX.25/HDLC CRC-16/X-25 (reflected polynomial 0x8408). */
object Ax25Fcs {
    fun compute(data: ByteArray): Int {
        var crc = 0xFFFF
        for (raw in data) {
            crc = crc xor (raw.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0x8408 else crc ushr 1
            }
        }
        return crc.inv() and 0xFFFF
    }

    fun append(frameWithoutFcs: ByteArray): ByteArray {
        val fcs = compute(frameWithoutFcs)
        return frameWithoutFcs + byteArrayOf((fcs and 0xFF).toByte(), ((fcs ushr 8) and 0xFF).toByte())
    }

    fun valid(frameWithFcs: ByteArray): Boolean {
        if (frameWithFcs.size < 3) return false
        val payload = frameWithFcs.copyOf(frameWithFcs.size - 2)
        val expected = compute(payload)
        val actual = (frameWithFcs[frameWithFcs.lastIndex - 1].toInt() and 0xFF) or
            ((frameWithFcs[frameWithFcs.lastIndex].toInt() and 0xFF) shl 8)
        return expected == actual
    }
}
