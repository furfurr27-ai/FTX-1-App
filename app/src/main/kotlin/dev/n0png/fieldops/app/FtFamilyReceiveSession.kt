package dev.n0png.fieldops.app

import dev.n0png.fieldops.android.dsp.FtFamilyNativeEngine
import dev.n0png.fieldops.core.audio.SharedAudioPipeline
import dev.n0png.fieldops.core.audio.TimedPcmBlock
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.WeakSignalCoordinator
import dev.n0png.fieldops.core.dsp.WindowedDspEngine

/**
 * FT8 / FT4 / FT2 production Android 48 kHz -> 12 kHz -> UTC slot receive path.
 *
 * This is a non-transmitting, testable receive-only boundary. No capture
 * permission, USB device, CAT, audio playback or PTT is used by this type.
 * The caller supplies timestamped actual PCM. A fake engine can be injected
 * by a software test, but that never counts as a native decoder/golden vector.
 */
class FtFamilyReceiveSession(
    private val engine: WindowedDspEngine,
    onDecode: (DecodeResult) -> Unit,
) : AutoCloseable {
    private val audio = SharedAudioPipeline()
    private val family = setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2)
    private val coordinator = WeakSignalCoordinator(
        audio = audio,
        windowedEngines = family.associateWith { engine },
        onDecode = onDecode,
    )
    @Volatile private var closed = false

    var selectedMode: DigitalMode = DigitalMode.FT8
        private set

    init {
        require(engine.modes.containsAll(family)) {
            "An FT-family session requires support for FT8, FT4 and FT2"
        }
        coordinator.selectMode(selectedMode)
    }

    @Synchronized
    fun selectMode(mode: DigitalMode) {
        check(!closed) { "Receive session closed" }
        require(mode in family) { "Only FT8, FT4 and FT2 are valid" }
        // Never reuse stale FIR/partial UTC-slot state when changing modes.
        audio.reset()
        coordinator.selectMode(mode)
        selectedMode = mode
    }

    @Synchronized
    fun accept48k(block: TimedPcmBlock) {
        check(!closed) { "Receive session closed" }
        require(block.sampleRate == 48_000) { "48 kHz PCM input required" }
        audio.accept48k(block)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        coordinator.close()
        audio.reset()
    }
}

/**
 * The GitHub tree currently contains the JNI declarations, but not
 * ARM64 libft8af.so. An installed app MUST fail closed if the native
 * decoder is absent. Never fall back to synthetic fixture decodes.
 */
object FtFamilyNativeAvailability {
    fun probe(loader: () -> Unit = { System.loadLibrary("ft8af") }): Boolean =
        try {
            loader()
            true
        } catch (_: LinkageError) {
            false
        } catch (_: SecurityException) {
            false
        }

    fun createReceiveSession(
        onDecode: (DecodeResult) -> Unit,
        loader: () -> Unit = { System.loadLibrary("ft8af") },
    ): FtFamilyReceiveSession {
        check(probe(loader)) { "FT8/FT4/FT2 native decoder is NOT packaged or unavailable" }
        // A real FT8AF-backed provider is constructed only AFTER loader proof.
        return FtFamilyReceiveSession(FtFamilyNativeEngine(), onDecode)
    }
}
