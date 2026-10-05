package com.js8call.core

/**
 * Host-test API mirror of the pinned upstream JS8Engine Kotlin surface.
 * TEST SOURCE ONLY. Production uses the real JS8Call Android AAR/JNI engine.
 */
class JS8Engine private constructor() : AutoCloseable {
    companion object {
        fun create(
            sampleRateHz: Int = 12000,
            submodes: Int = 0x1F,
            callbackHandler: CallbackHandler,
            enableTxAudioTap: Boolean = false,
            useQmxUsbAudio: Boolean = false,
        ): JS8Engine = JS8Engine()
    }

    fun start(): Boolean = true
    fun stop() = Unit
    fun submitAudio(samples: ShortArray, timestampNs: Long = 0L): Boolean = true

    fun transmitMessage(
        text: String,
        myCall: String,
        myGrid: String,
        selectedCall: String = "",
        submode: Int = 0,
        audioFrequencyHz: Double,
        txDelaySec: Double = 0.0,
        forceIdentify: Boolean = false,
        forceData: Boolean = false,
    ): Boolean = true

    fun transmitFrame(
        frame: String,
        bits: Int,
        submode: Int,
        audioFrequencyHz: Double,
        txDelaySec: Double = 0.0,
    ): Boolean = true

    fun stopTransmit() = Unit
    fun isTransmitting(): Boolean = false
    fun isTransmittingAudio(): Boolean = false
    fun txMillisecondsUntilAudio(): Int = -1
    fun setTransmitReady(ready: Boolean) = Unit

    override fun close() = Unit

    interface CallbackHandler {
        fun onDecoded(
            utc: Int,
            snr: Int,
            dt: Float,
            freq: Float,
            text: String,
            type: Int,
            quality: Float,
            mode: Int,
            driftMs: Int,
        )
        fun onSpectrum(bins: FloatArray, binHz: Float, powerDb: Float, peakDb: Float)
        fun onDecodeStarted(submodes: Int)
        fun onDecodeFinished(count: Int)
        fun onError(message: String)
        fun onLog(level: Int, message: String)
        fun onTxAudio(samples: ShortArray, sampleRateHz: Int) {}
    }
}
