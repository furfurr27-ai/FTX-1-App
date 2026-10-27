package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.DigitalModes
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.dsp.WindowedDspEngine
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class FtFamilyOfflineWavTest {
    private class Decoder : WindowedDspEngine {
        override val modes = setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)
        var lastMode: DigitalMode? = null
        override fun decode(mode: DigitalMode, slotStartUtcMillis: Long, samples12k: FloatArray): List<DecodeResult> {
            assertEquals(0L, slotStartUtcMillis)
            assertEquals(DigitalModes.profile(mode).cycleMillis * 12, samples12k.size.toLong())
            assertTrue(samples12k.any { it != 0f })
            lastMode = mode
            return listOf(DecodeResult(mode, slotStartUtcMillis, text = "TEST ONLY"))
        }
        override fun encode(request: EncodeRequest): TxWaveform = error("Offline receive must not transmit")
    }

    private fun wav(mode: DigitalMode, sampleRate: Int = 12_000, channels: Int = 1,
                    bits: Int = 16, samples: Int =
                    (DigitalModes.profile(mode).cycleMillis * 12).toInt()): ByteArray {
        val dataBytes = samples * 2
        return ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(dataBytes + 36)
            put("WAVE".toByteArray()); put("fmt ".toByteArray())
            putInt(16); putShort(1); putShort(channels.toShort())
            putInt(sampleRate); putInt(sampleRate * channels * bits / 8)
            putShort((channels * bits / 8).toShort()); putShort(bits.toShort())
            put("data".toByteArray()); putInt(dataBytes)
            repeat(samples) { putShort((if (it % 2 == 0) 1000 else -1000).toShort()) }
        }.array()
    }

    @Test fun eachModeUsesItsOwnExactSlotLengthAndReturnsFixtureLabel() {
        val d = Decoder()
        for (mode in listOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)) {
            val results = FtFamilyOfflineWav.decode(ByteArrayInputStream(wav(mode)), mode, d)
            assertEquals(mode, d.lastMode)
            assertEquals("TEST ONLY", results.single().text)
            assertEquals(0L, results.single().utcMillis)
        }
    }

    @Test fun independentArchiveLengthMayPadToFullSlotButCannotGuessUtc() {
        val dec = object : WindowedDspEngine {
            override val modes = setOf(DigitalMode.FT4)
            var samples: FloatArray? = null
            override fun decode(mode: DigitalMode, slotStartUtcMillis: Long,
                                samples12k: FloatArray): List<DecodeResult> {
                assertEquals(DigitalMode.FT4, mode)
                assertEquals(0L, slotStartUtcMillis)
                samples = samples12k
                return emptyList()
            }
            override fun encode(request: EncodeRequest): TxWaveform =
                error("Offline receive must not transmit")
        }
        val sourceSamples = 72_576 // WSJT-X FT4 sample: 145196-byte WAV, 6.048 s audio
        assertTrue(FtFamilyOfflineWav.decode(ByteArrayInputStream(wav(DigitalMode.FT4,
            samples = sourceSamples)), DigitalMode.FT4, dec).isEmpty())
        val pcm = requireNotNull(dec.samples)
        assertEquals(90_000, pcm.size)
        assertNotEquals(0f, pcm[sourceSamples - 1])
        assertTrue(pcm.copyOfRange(sourceSamples, pcm.size).all { it == 0f })
    }

    @Test fun rejectOddByteLengthOverlongAndIncompleteFastModeBursts() {
        val dec = Decoder()
        val short = wav(DigitalMode.FT4, samples = 60_000)
        val extraLong = wav(DigitalMode.FT4, samples = 90_001)
        val odd = wav(DigitalMode.FT4).let { source ->
            source.copyOf(source.size + 1).also {
                ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(4, it.size - 8)
                    .putInt(40, it.size - 44)
            }
        }
        for (malformed in listOf(short, extraLong, odd)) {
            assertThrows(IllegalArgumentException::class.java) {
                FtFamilyOfflineWav.decode(ByteArrayInputStream(malformed), DigitalMode.FT4, dec)
            }
        }
        assertNull("Reject malformed receive WAV before DSP invocation", dec.lastMode)
    }

    @Test fun invalidHeadersRatesLengthsAndUnboundedInputFailBeforeEngine() {
        val d = Decoder()
        assertThrows(IllegalArgumentException::class.java) {
            FtFamilyOfflineWav.decode(ByteArrayInputStream(byteArrayOf(1, 2, 3)), DigitalMode.FT8, d)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FtFamilyOfflineWav.decode(ByteArrayInputStream(wav(DigitalMode.FT8, sampleRate = 44_100)),
                DigitalMode.FT8, d)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FtFamilyOfflineWav.decode(ByteArrayInputStream(wav(DigitalMode.FT8, samples = 30)),
                DigitalMode.FT8, d)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FtFamilyOfflineWav.decode(ByteArrayInputStream(ByteArray(2_000_001)),
                DigitalMode.FT8, d)
        }
        assertNull(d.lastMode)
    }

    @Test fun rejectAudioOutsideDeclaredRiffContainerBeforeInvokingNativeDecoder() {
        val decoder = Decoder()
        val valid = wav(DigitalMode.FT8)
        // RIFF closes immediately after the data chunk header. The bytes
        // following that boundary are not part of the declared container.
        ByteBuffer.wrap(valid).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 36)
        assertThrows(IllegalArgumentException::class.java) {
            FtFamilyOfflineWav.decode(ByteArrayInputStream(valid), DigitalMode.FT8, decoder)
        }
        assertNull(decoder.lastMode)
    }

    @Test fun rejectPartialFinalChunkHeaderInsideDeclaredRiff() {
        val decoder = Decoder()
        val valid = wav(DigitalMode.FT4)
        val extra = valid.copyOf(valid.size + 4)
        ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(4, extra.size - 8)
        assertThrows(IllegalArgumentException::class.java) {
            FtFamilyOfflineWav.decode(ByteArrayInputStream(extra), DigitalMode.FT4, decoder)
        }
        assertNull(decoder.lastMode)
    }


    @Test fun duplicateFormatAndAudioChunksCannotReachDecoder() {
        val format = wav(DigitalMode.FT8).copyOfRange(20, 36)
        for ((kind, payload) in listOf(
            "fmt " to format,
            "data" to byteArrayOf(),
        )) {
            val base = wav(DigitalMode.FT8)
            val appended = ByteBuffer.allocate(base.size + 8 + payload.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(base)
                .put(kind.toByteArray(Charsets.US_ASCII))
                .putInt(payload.size)
                .put(payload)
                .array()
            ByteBuffer.wrap(appended).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(4, appended.size - 8)
            val decoder = Decoder()
            val rejection = assertThrows(IllegalArgumentException::class.java) {
                FtFamilyOfflineWav.decode(ByteArrayInputStream(appended), DigitalMode.FT8, decoder)
            }
            assertTrue(rejection.message.orEmpty().contains("Duplicate WAVE"))
            assertNull("Decoder must not see ambiguous audio", decoder.lastMode)
        }
    }

    @Test fun nonProgressingContentProviderStreamFailsClosedWithoutSpinning() {
        val decoder = Decoder()
        val neverProgresses = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        val rejection = assertThrows(IllegalArgumentException::class.java) {
            FtFamilyOfflineWav.decode(neverProgresses, DigitalMode.FT8, decoder)
        }
        assertTrue(rejection.message.orEmpty().contains("zero bytes"))
        assertNull(decoder.lastMode)
    }

    @Test fun noJs8OrWsprWavMayUseFtFamilyPath() {
        val d = Decoder()
        assertThrows(IllegalArgumentException::class.java) {
            FtFamilyOfflineWav.decode(ByteArrayInputStream(wav(DigitalMode.FT8)), DigitalMode.JS8, d)
        }
        assertNull(d.lastMode)
    }
}
