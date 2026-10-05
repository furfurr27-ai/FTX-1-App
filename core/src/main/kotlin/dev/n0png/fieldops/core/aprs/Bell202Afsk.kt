package dev.n0png.fieldops.core.aprs

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bell-202 AFSK1200 modem for phone-side APRS.
 * Mark=1200 Hz, space=2200 Hz, 1200 baud, HDLC NRZI + bit stuffing.
 * The production capture rate is 48 kHz, giving exactly 40 samples/bit.
 */
object Bell202Afsk {
    const val SAMPLE_RATE = 48_000
    const val BAUD = 1_200
    const val MARK_HZ = 1_200.0
    const val SPACE_HZ = 2_200.0
    const val SAMPLES_PER_BIT = SAMPLE_RATE / BAUD
    private val FLAG_BITS = intArrayOf(0, 1, 1, 1, 1, 1, 1, 0)

    fun modulateAx25(frameWithoutFcs: ByteArray, preambleFlags: Int = 35, amplitude: Float = 0.75f): FloatArray {
        require(preambleFlags >= 2)
        val bits = ArrayList<Int>()
        repeat(preambleFlags) { bits.addAll(FLAG_BITS.asList()) }
        appendStuffedDataBits(bits, Ax25Fcs.append(frameWithoutFcs))
        bits.addAll(FLAG_BITS.asList())
        repeat(3) { bits.addAll(FLAG_BITS.asList()) }

        val out = FloatArray(bits.size * SAMPLES_PER_BIT)
        var mark = true
        var phase = 0.0
        var p = 0
        for (bit in bits) {
            if (bit == 0) mark = !mark // NRZI: zero causes transition.
            val f = if (mark) MARK_HZ else SPACE_HZ
            val step = 2.0 * PI * f / SAMPLE_RATE
            repeat(SAMPLES_PER_BIT) {
                out[p++] = (sin(phase) * amplitude).toFloat()
                phase += step
                if (phase >= 2.0 * PI) phase -= 2.0 * PI
            }
        }
        return out
    }

    /** Offline block decoder used by the shared 48 kHz APRS engine and tests. */
    fun demodulateAx25(samples: FloatArray): List<ByteArray> {
        if (samples.size < SAMPLES_PER_BIT * 24) return emptyList()
        val unique = LinkedHashMap<String, ByteArray>()
        for (offset in 0 until SAMPLES_PER_BIT) {
            val tones = classifyTones(samples, offset)
            if (tones.size < 24) continue
            val bits = IntArray(tones.size - 1)
            for (i in 1 until tones.size) bits[i - 1] = if (tones[i] == tones[i - 1]) 1 else 0
            for (frame in extractHdlcFrames(bits)) {
                if (Ax25Fcs.valid(frame)) {
                    val noFcs = frame.copyOf(frame.size - 2)
                    unique[noFcs.joinToString("") { "%02x".format(it.toInt() and 0xff) }] = noFcs
                }
            }
        }
        return unique.values.toList()
    }

    private fun appendStuffedDataBits(out: MutableList<Int>, bytes: ByteArray) {
        var ones = 0
        for (raw in bytes) {
            val v = raw.toInt() and 0xFF
            for (i in 0..7) {
                val bit = (v ushr i) and 1
                out += bit
                if (bit == 1) {
                    ones++
                    if (ones == 5) { out += 0; ones = 0 }
                } else ones = 0
            }
        }
    }

    private fun classifyTones(samples: FloatArray, offset: Int): BooleanArray {
        val n = (samples.size - offset) / SAMPLES_PER_BIT
        val out = BooleanArray(n)
        for (symbol in 0 until n) {
            val start = offset + symbol * SAMPLES_PER_BIT
            val mark = toneEnergy(samples, start, MARK_HZ)
            val space = toneEnergy(samples, start, SPACE_HZ)
            out[symbol] = mark >= space
        }
        return out
    }

    private fun toneEnergy(samples: FloatArray, start: Int, hz: Double): Double {
        var re = 0.0
        var im = 0.0
        val step = 2.0 * PI * hz / SAMPLE_RATE
        for (i in 0 until SAMPLES_PER_BIT) {
            val x = samples[start + i]
            val a = step * i
            re += x * cos(a)
            im -= x * sin(a)
        }
        return re * re + im * im
    }

    private fun extractHdlcFrames(bits: IntArray): List<ByteArray> {
        val flags = mutableListOf<Int>()
        for (i in 0..bits.size - 8) {
            var ok = true
            for (j in 0..7) if (bits[i + j] != FLAG_BITS[j]) { ok = false; break }
            if (ok) flags += i
        }
        val out = mutableListOf<ByteArray>()
        for (k in 0 until flags.lastIndex) {
            val a = flags[k] + 8
            val b = flags[k + 1]
            if (b <= a) continue
            val deStuffed = ArrayList<Int>()
            var ones = 0
            var i = a
            var valid = true
            while (i < b) {
                val bit = bits[i]
                if (bit == 1) {
                    ones++
                    if (ones > 6) { valid = false; break }
                    deStuffed += 1
                } else {
                    if (ones == 5) {
                        // stuffed zero: remove it.
                        ones = 0
                        i++
                        continue
                    }
                    ones = 0
                    deStuffed += 0
                }
                i++
            }
            if (!valid || deStuffed.size < 24 || deStuffed.size % 8 != 0) continue
            val bytes = ByteArray(deStuffed.size / 8)
            for (bi in bytes.indices) {
                var v = 0
                for (j in 0..7) v = v or (deStuffed[bi * 8 + j] shl j)
                bytes[bi] = v.toByte()
            }
            out += bytes
        }
        return out
    }
}
