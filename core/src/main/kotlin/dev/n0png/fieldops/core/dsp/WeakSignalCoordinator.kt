package dev.n0png.fieldops.core.dsp

import dev.n0png.fieldops.core.audio.SharedAudioPipeline
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.DigitalModes
import dev.n0png.fieldops.core.digital.Js8Speed

/**
 * Routes the shared 12 kHz branch either to a complete UTC slot assembler
 * (FT family/WSPR) or directly to a continuous engine (JS8).
 */
class WeakSignalCoordinator(
    private val audio: SharedAudioPipeline,
    private val windowedEngines: Map<DigitalMode, WindowedDspEngine>,
    private val streaming12kEngines: Map<DigitalMode, Streaming12kDspEngine> = emptyMap(),
    private val onDecode: (DecodeResult) -> Unit,
) {
    private var mode: DigitalMode? = null
    @Volatile private var assembler: SlotWindowAssembler? = null
    @Volatile private var activeStreaming: Streaming12kDspEngine? = null

    private val consumer = SharedAudioPipeline.Consumer { block ->
        val streaming = activeStreaming
        if (streaming != null) streaming.accept12k(block) else assembler?.accept(block)
    }

    init { audio.add12kConsumer(consumer) }

    @Synchronized
    fun selectMode(newMode: DigitalMode, js8Speed: Js8Speed = Js8Speed.NORMAL) {
        require(newMode in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.FT2, DigitalMode.JS8, DigitalMode.WSPR))

        activeStreaming?.stop()
        activeStreaming = null
        assembler = null

        val streaming = streaming12kEngines[newMode]
        if (streaming != null) {
            require(streaming.mode == newMode)
            streaming.start(onDecode)
            activeStreaming = streaming
            mode = newMode
            return
        }

        val engine = windowedEngines[newMode] ?: error("No DSP engine registered for $newMode")
        require(newMode in engine.modes)
        mode = newMode
        assembler = SlotWindowAssembler(DigitalModes.profile(newMode, js8Speed)) { window ->
            if (!window.complete) return@SlotWindowAssembler
            engine.decode(newMode, window.slotStartUtcMillis, window.samples).forEach(onDecode)
        }
    }

    @Synchronized
    fun close() {
        activeStreaming?.stop()
        activeStreaming = null
        assembler = null
        audio.remove12kConsumer(consumer)
    }
}
