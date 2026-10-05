package dev.n0png.fieldops.core.dsp

import dev.n0png.fieldops.core.audio.SharedAudioPipeline
import dev.n0png.fieldops.core.audio.TimedPcmBlock
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.DigitalModes
import dev.n0png.fieldops.core.digital.Js8Speed

/** Owns the active weak-signal slot assembler. APRS stays independent at 48 kHz. */
class WeakSignalCoordinator(
    private val audio: SharedAudioPipeline,
    private val engines: Map<DigitalMode, WindowedDspEngine>,
    private val onDecode: (DecodeResult) -> Unit,
) {
    private var mode: DigitalMode? = null
    @Volatile private var assembler: SlotWindowAssembler? = null

    private val consumer = SharedAudioPipeline.Consumer { block -> assembler?.accept(block) }

    init { audio.add12kConsumer(consumer) }

    fun selectMode(newMode: DigitalMode, js8Speed: Js8Speed = Js8Speed.NORMAL) {
        require(newMode in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2, DigitalMode.JS8, DigitalMode.WSPR))
        val engine = engines[newMode] ?: error("No DSP engine registered for $newMode")
        require(newMode in engine.modes)
        mode = newMode
        assembler = SlotWindowAssembler(DigitalModes.profile(newMode, js8Speed)) { window ->
            if (!window.complete) return@SlotWindowAssembler
            engine.decode(newMode, window.slotStartUtcMillis, window.samples).forEach(onDecode)
        }
    }

    fun close() { audio.remove12kConsumer(consumer) }
}
