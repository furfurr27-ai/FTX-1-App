package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.DigitalModes
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.WindowedDspEngine
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Bounded, receive-only offline replay for mono 12 kHz PCM16 RIFF/WAVE.
 * Complete slots work unchanged; shorter complete-signal sample archives may
 * zero-pad the remainder of the selected mode's one-slot receive window.
 *
 * The WAV header contains no trusted real-world timestamp; callers must label
 * the decode time as a synthetic slot epoch. Nothing keys RF or logs a QSO.
 * Production uses FtFamilyNativeEngine; tests can inject a fixture decoder.
 */
object FtFamilyOfflineWav {
    private const val MAX_FILE_BYTES = 2_000_000

    fun decode(input: InputStream, mode: DigitalMode, engine: WindowedDspEngine): List<DecodeResult> {
        require(mode in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2))
        require(mode in engine.modes)
        val b = readBounded(input)
        require(b.size >= 44 && ascii(b, 0, 4) == "RIFF" &&
            ascii(b, 8, 4) == "WAVE") { "Not a RIFF/WAVE file" }
        // Chunk offsets are relative to the declared RIFF container, not the
        // entire uploaded file. Trailing bytes cannot supply hidden audio.
        val riffEnd = u32(b, 4) + 8L
        require(riffEnd <= b.size.toLong()) { "Truncated WAVE container" }
        var pos = 12
        var formatFound = false
        var dataOffset = -1
        var dataSize = 0
        while (pos.toLong() + 8L <= riffEnd) {
            val kind = ascii(b, pos, 4)
            val len = u32(b, pos + 4)
            val start = pos.toLong() + 8
            require(len <= riffEnd - start) { "Truncated WAVE chunk" }
            if (kind == "fmt ") {
                require(!formatFound) { "Duplicate WAVE format chunk" }
                require(len >= 16) { "Truncated WAVE format" }
                val off = start.toInt()
                val pcm = u16(b, off)
                val channels = u16(b, off + 2)
                val sampleRate = u32(b, off + 4)
                val bytesPerSecond = u32(b, off + 8)
                val blockAlign = u16(b, off + 12)
                val bitsPerSample = u16(b, off + 14)
                require(pcm == 1 && channels == 1 && sampleRate == 12_000L &&
                    bytesPerSecond == 24_000L && blockAlign == 2 &&
                    bitsPerSample == 16) {
                    "Requires mono 12 kHz 16-bit uncompressed PCM WAV"
                }
                formatFound = true
            } else if (kind == "data") {
                require(dataOffset == -1) { "Duplicate WAVE audio chunk" }
                dataOffset = start.toInt()
                dataSize = len.toInt()
            }
            pos = (start + len + (len and 1L)).toInt()
            require(pos.toLong() <= riffEnd) { "Invalid WAVE padding" }
        }
        require(pos.toLong() == riffEnd) { "Incomplete WAVE chunk header" }
        require(formatFound && dataOffset >= 0) { "WAVE format or audio missing" }
        val expectedSamples = DigitalModes.profile(mode).cycleMillis.toInt() * 12
        // A complete burst, plus 500 ms of timing margin, is the minimum
        // usable recorded waveform. FT4 reference archives include 6.048 s
        // captures that carry a complete 105-symbol waveform but not 7.5 s of
        // silence. We pad those ONLY for offline decoder input; UTC remains
        // explicitly unverified and the result cannot create a contact.
        val burstSamples = when (mode) {
            DigitalMode.FT8 -> 79 * 1_920 // 79 symbols * 160 ms * 12 kHz
            DigitalMode.FT4 -> 105 * 576   // 105 symbols * 48 ms * 12 kHz
            DigitalMode.FT2 -> 105 * 288   // 105 symbols * 24 ms * 12 kHz
            else -> error("Unsupported FT-family decoder")
        }
        val minSamples = burstSamples + 6_000 // 500 ms capture margin
        require(dataSize % 2 == 0 && dataSize / 2 in minSamples..expectedSamples) {
            "${mode.name} requires ${minSamples / 12}..${DigitalModes.profile(mode).cycleMillis} ms of complete 12 kHz PCM16 audio"
        }
        val actualSamples = dataSize / 2
        val samples = FloatArray(expectedSamples) // zero pad SHORT, bounded file recordings
        for (index in 0 until actualSamples) {
            val off = dataOffset + 2 * index
            val pcm = u16(b, off).toShort().toInt()
            samples[index] = pcm / 32768.0f
        }
        // Zero denotes a RELATIVE offline test slot, not the recording's UTC.
        return engine.decode(mode, 0L, samples)
    }

    private fun readBounded(input: InputStream): ByteArray {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(count != 0) { "WAV input stream returned zero bytes without EOF" }
            require(bytes.size() + count <= MAX_FILE_BYTES) { "WAV exceeds offline 2 MB limit" }
            bytes.write(buffer, 0, count)
        }
        return bytes.toByteArray()
    }

    private fun ascii(bytes: ByteArray, pos: Int, count: Int): String =
        String(bytes, pos, count, Charsets.US_ASCII)

    private fun u16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)

    private fun u32(b: ByteArray, i: Int): Long =
        (u16(b, i).toLong() or (u16(b, i + 2).toLong() shl 16))
}
