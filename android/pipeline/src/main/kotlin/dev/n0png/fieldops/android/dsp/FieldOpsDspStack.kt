package dev.n0png.fieldops.android.dsp

import dev.n0png.fieldops.core.aprs.AprsAfskEngine
import dev.n0png.fieldops.core.audio.SharedAudioPipeline
import dev.n0png.fieldops.core.digital.DigitalMode
import dev.n0png.fieldops.core.digital.Js8Speed
import dev.n0png.fieldops.core.dsp.DecodeResult
import dev.n0png.fieldops.core.dsp.WeakSignalCoordinator
import dev.n0png.fieldops.core.dsp.WindowedDspEngine

/**
 * Composition root for the shared receive DSP path.
 *
 * Exactly one 48 kHz capture stream enters [audio]. APRS continuously consumes
 * that raw stream. Slotted weak-signal modes consume complete windows from the
 * common 12 kHz branch; JS8 consumes that branch continuously.
 */
class FieldOpsDspStack(
    private val audio: SharedAudioPipeline,
    ftFamily: FtFamilyNativeEngine,
    private val js8: Js8EngineAdapter,
    wspr: WsprEngineAdapter,
    private val onDecode: (DecodeResult) -> Unit,
) : AutoCloseable {
    private val aprs = AprsAfskEngine()
    private val aprsConsumer = SharedAudioPipeline.Consumer { block ->
        aprs.accept48k(block.utcStartNanos, block.samples).forEach(onDecode)
    }

    private val windowedEngines: Map<DigitalMode, WindowedDspEngine> = mapOf(
        DigitalMode.FT8 to ftFamily,
        DigitalMode.FT4 to ftFamily,
        DigitalMode.FT2 to ftFamily,
        DigitalMode.WSPR to wspr,
    )

    private val weak = WeakSignalCoordinator(
        audio = audio,
        windowedEngines = windowedEngines,
        streaming12kEngines = mapOf(DigitalMode.JS8 to js8),
        onDecode = onDecode,
    )

    init {
        audio.add48kConsumer(aprsConsumer)
    }

    fun selectWeakMode(mode: DigitalMode, js8Speed: Js8Speed = Js8Speed.NORMAL) {
        weak.selectMode(mode, js8Speed)
    }

    override fun close() {
        audio.remove48kConsumer(aprsConsumer)
        weak.close()
        js8.close()
    }
}
