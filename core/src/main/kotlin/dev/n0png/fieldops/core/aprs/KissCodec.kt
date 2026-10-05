package dev.n0png.fieldops.core.aprs

/** Minimal KISS framing. FCS is not included in KISS data frames. */
object KissCodec {
    const val FEND = 0xC0
    const val FESC = 0xDB
    const val TFEND = 0xDC
    const val TFESC = 0xDD
    const val CMD_DATA = 0x00

    fun encodeData(ax25: ByteArray, port: Int = 0): ByteArray {
        require(port in 0..15)
        val out = ArrayList<Byte>(ax25.size + 4)
        out += FEND.toByte()
        out += ((port shl 4) or CMD_DATA).toByte()
        for (b in ax25) {
            when (b.toInt() and 0xFF) {
                FEND -> { out += FESC.toByte(); out += TFEND.toByte() }
                FESC -> { out += FESC.toByte(); out += TFESC.toByte() }
                else -> out += b
            }
        }
        out += FEND.toByte()
        return out.toByteArray()
    }

    /** Returns DATA payloads only; non-DATA KISS commands are skipped. */
    fun decodeDataFrames(stream: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        val current = ArrayList<Byte>()
        var inFrame = false
        var escaped = false

        fun finish() {
            if (current.isNotEmpty()) {
                val command = current[0].toInt() and 0x0F
                if (command == CMD_DATA) frames += current.drop(1).toByteArray()
            }
            current.clear()
            escaped = false
        }

        for (raw in stream) {
            val v = raw.toInt() and 0xFF
            if (v == FEND) {
                if (inFrame) finish()
                inFrame = true
                continue
            }
            if (!inFrame) continue
            if (escaped) {
                when (v) {
                    TFEND -> current += FEND.toByte()
                    TFESC -> current += FESC.toByte()
                    else -> { current += FESC.toByte(); current += raw }
                }
                escaped = false
            } else if (v == FESC) {
                escaped = true
            } else {
                current += raw
            }
        }
        return frames
    }
}
