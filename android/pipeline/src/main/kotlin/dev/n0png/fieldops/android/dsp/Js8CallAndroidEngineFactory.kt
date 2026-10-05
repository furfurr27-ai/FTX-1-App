package dev.n0png.fieldops.android.dsp

import com.js8call.core.JS8Engine

/**
 * Production binding to JS8Call-improved/Android-port pinned at
 * 9996202f355569c5ee7b97fae539f3b763081dc2.
 *
 * CP-0002A is RX-only: the upstream TX audio tap is disabled and no transmit
 * method is exposed through FieldOps' NativeEngine boundary.
 */
class Js8CallAndroidEngineFactory(
    private val submodes: Int = 0x1F,
) : Js8EngineAdapter.EngineFactory {
    override fun create(callbacks: Js8EngineAdapter.Callbacks): Js8EngineAdapter.NativeEngine {
        val upstream = JS8Engine.create(
            sampleRateHz = 12_000,
            submodes = submodes,
            callbackHandler = object : JS8Engine.CallbackHandler {
                override fun onDecoded(
                    utc: Int,
                    snr: Int,
                    dt: Float,
                    freq: Float,
                    text: String,
                    type: Int,
                    quality: Float,
                    mode: Int,
                    driftMs: Int,
                ) = callbacks.onDecoded(utc, snr, dt, freq, text, type, quality, mode, driftMs)

                override fun onSpectrum(
                    bins: FloatArray,
                    binHz: Float,
                    powerDb: Float,
                    peakDb: Float,
                ) = Unit

                override fun onDecodeStarted(submodes: Int) = Unit
                override fun onDecodeFinished(count: Int) = Unit
                override fun onError(message: String) = callbacks.onError(message)
                override fun onLog(level: Int, message: String) = Unit
                override fun onTxAudio(samples: ShortArray, sampleRateHz: Int) = Unit
            },
            enableTxAudioTap = false,
            useQmxUsbAudio = false,
        )

        return object : Js8EngineAdapter.NativeEngine {
            override fun start(): Boolean = upstream.start()
            override fun stop() = upstream.stop()
            override fun submitAudio(samples: ShortArray, timestampNs: Long): Boolean =
                upstream.submitAudio(samples, timestampNs)
            override fun close() = upstream.close()
        }
    }
}
