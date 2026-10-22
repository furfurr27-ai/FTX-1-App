package dev.n0png.fieldops.app

import dev.n0png.fieldops.core.audio.TimedPcmBlock
import dev.n0png.fieldops.core.audio.TxWaveform
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.DigitalModes
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.EncodeRequest
import dev.n0png.fieldops.core.dsp.WindowedDspEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real production audio/slot routing, but an explicitly fake decoder.
 * This does NOT demonstrate native FT8/FT4/FT2 decoding or RF operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FtFamilyReceiveSessionTest {
    private class FixtureDecoder : WindowedDspEngine {
        override val modes = setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)
        val calls = mutableListOf<Pair<DigitalMode, Long>>()
        override fun decode(mode: DigitalMode, slotStartUtcMillis: Long, samples12k: FloatArray): List<DecodeResult> {
            assertEquals(DigitalModes.profile(mode).cycleMillis * 12, samples12k.size.toLong())
            calls += mode to slotStartUtcMillis
            return listOf(DecodeResult(mode, slotStartUtcMillis, text = "TEST FIXTURE NOT RF"))
        }
        override fun encode(request: EncodeRequest): TxWaveform =
            error("Receive-only test; TX must not be called")
    }

    private fun sendBlocks(session: FtFamilyReceiveSession, utcStartMillis: Long, seconds: Int) {
        val chunk = FloatArray(1_920)
        for (index in 0 until seconds * 25) {
            session.accept48k(TimedPcmBlock(
                utcStartNanos = utcStartMillis * 1_000_000L + index * 40_000_000L,
                sampleRate = 48_000,
                samples = chunk,
                sequence = index.toLong(),
            ))
        }
    }

    @Test fun allThreeModesRouteRealTimedPcmViaSharedDecimatorAndSlotAssembler() {
        val fake = FixtureDecoder()
        val received = mutableListOf<DecodeResult>()
        FtFamilyReceiveSession(fake, received::add).use { rx ->
            for (mode in listOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)) {
                rx.selectMode(mode)
                val start = 60_000L // UTC alignment valid for all three slot lengths.
                sendBlocks(rx, start, 32)
                assertTrue("Expected complete UTC slot for $mode", fake.calls.any { it.first == mode })
                assertTrue("Expected correct callback mode", received.any { it.mode == mode })
            }
            assertEquals(DigitalMode.FT2, rx.selectedMode)
            assertThrows(IllegalArgumentException::class.java) { rx.selectMode(DigitalMode.JS8) }
        }
        assertEquals(fake.calls.size, received.size)
    }

    @Test fun missingNativeLibraryFailsClosedAndNeverInstantiatesEngine() {
        assertFalse(FtFamilyNativeAvailability.probe {
            throw UnsatisfiedLinkError("libft8af.so absent")
        })
        assertFalse(FtFamilyNativeAvailability.probe {
            throw SecurityException("native load disallowed")
        })
        val thrown = assertThrows(IllegalStateException::class.java) {
            FtFamilyNativeAvailability.createReceiveSession(
                onDecode = { error("Must not dispatch synthetic/native callbacks") },
                loader = { throw UnsatisfiedLinkError("native missing") },
            )
        }
        assertTrue(thrown.message!!.contains("NOT packaged"))
    }

    @Test fun afterCloseRxBlocksAndModeChangesAreRejected() {
        val rx = FtFamilyReceiveSession(FixtureDecoder()) {}
        rx.close()
        assertThrows(IllegalStateException::class.java) { rx.selectMode(DigitalMode.FT4) }
        assertThrows(IllegalStateException::class.java) {
            rx.accept48k(TimedPcmBlock(0L, 48_000, FloatArray(1_920), 0L))
        }
    }
}
