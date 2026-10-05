package dev.n0png.fieldops.android.audio

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import dev.n0png.fieldops.core.audio.SharedAudioPipeline
import dev.n0png.fieldops.core.audio.TimedPcmBlock
import dev.n0png.fieldops.core.time.DisciplinedClock
import java.util.concurrent.atomic.AtomicBoolean

/** S23-Ultra/FTX-1 capture adapter. One AudioRecord feeds every mode. */
class AndroidUsbAudioCapture(
    private val audioManager: AudioManager,
    private val clock: DisciplinedClock,
    private val pipeline: SharedAudioPipeline,
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var recorder: AudioRecord? = null

    fun findUsbInput(): AudioDeviceInfo? = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }

    fun start(device: AudioDeviceInfo = requireNotNull(findUsbInput()) { "No USB audio input" }) {
        check(running.compareAndSet(false, true)) { "capture already running" }
        val min = AudioRecord.getMinBufferSize(48_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0)
        val rec = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(48_000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build())
            .setBufferSizeInBytes(maxOf(min * 4, 48_000 * 2))
            .build()
        check(rec.setPreferredDevice(device)) { "Unable to route AudioRecord to FTX-1 USB input" }
        recorder = rec
        rec.startRecording()
        thread = Thread({ captureLoop(rec) }, "FTX1-USB-Audio-RX").also { it.start() }
    }

    private fun captureLoop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val shorts = ShortArray(1_920) // 40 ms at 48 kHz
        var seq = 0L
        var framesRead = 0L
        val hwStamp = AudioTimestamp()
        try {
            while (running.get()) {
                val n = rec.read(shorts, 0, shorts.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) continue

                val blockStartFrame = framesRead
                framesRead += n
                val monoStart = if (rec.getTimestamp(hwStamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                    // AudioTimestamp ties an audio frame position to CLOCK_MONOTONIC.
                    // Extrapolate back to the first frame of this callback so Java
                    // scheduling latency is not mistaken for RF/audio timing.
                    hwStamp.nanoTime +
                        (blockStartFrame - hwStamp.framePosition) * 1_000_000_000L / 48_000L
                } else {
                    // Startup/fallback path until a hardware timestamp is available.
                    val endMono = SystemClock.elapsedRealtimeNanos()
                    endMono - n.toLong() * 1_000_000_000L / 48_000L
                }
                val startUtc = clock.utcNanosAt(monoStart)
                val f = FloatArray(n) { shorts[it] / 32768f }
                pipeline.accept48k(TimedPcmBlock(startUtc, 48_000, f, seq++))
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }

    fun stop() {
        running.set(false)
        runCatching { recorder?.stop() }
        thread?.join(500)
        thread = null
        recorder = null
    }
}
