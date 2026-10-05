package dev.n0png.fieldops.android.dsp

import com.js8call.core.JS8Engine

/**
 * Production binding to JS8Call-improved/Android-port pinned at
 * 9996202f355569c5ee7b97fae539f3b763081dc2.
 *
 * FieldOps uses the native TX audio tap only. Upstream rig/PTT/service control
 * is not instantiated; CAT/PTT and USB playback remain FieldOps-owned.
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
                override fun onTxAudio(samples: ShortArray, sampleRateHz: Int) =
                    callbacks.onTxAudio(samples, sampleRateHz)
            },
            enableTxAudioTap = true,
            useQmxUsbAudio = false,
        )

        return object : Js8EngineAdapter.NativeEngine {
            override fun start(): Boolean = upstream.start()
            override fun stop() = upstream.stop()
            override fun submitAudio(samples: ShortArray, timestampNs: Long): Boolean =
                upstream.submitAudio(samples, timestampNs)

            override fun transmitMessage(request: Js8EngineAdapter.TxRequest): Boolean =
                upstream.transmitMessage(
                    text = request.text,
                    myCall = request.myCall,
                    myGrid = request.myGrid,
                    selectedCall = request.selectedCall,
                    submode = request.submode,
                    audioFrequencyHz = request.audioFrequencyHz,
                    txDelaySec = request.txDelaySec,
                    forceIdentify = request.forceIdentify,
                    forceData = request.forceData,
                )

            override fun stopTransmit() = upstream.stopTransmit()
            override fun isTransmitting(): Boolean = upstream.isTransmitting()
            override fun isTransmittingAudio(): Boolean = upstream.isTransmittingAudio()
            override fun txMillisecondsUntilAudio(): Int = upstream.txMillisecondsUntilAudio()
            override fun setTransmitReady(ready: Boolean) = upstream.setTransmitReady(ready)
            override fun close() = upstream.close()
        }
    }
}
