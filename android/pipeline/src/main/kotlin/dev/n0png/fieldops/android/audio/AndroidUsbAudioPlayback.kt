package dev.n0png.fieldops.android.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dev.n0png.fieldops.core.radio.TxAudioPort

/** Shared TX audio endpoint. CAT/PTT ownership is handled by Ftx1RadioSession. */
class AndroidUsbAudioPlayback(private val audioManager: AudioManager) : TxAudioPort {
    private var track: AudioTrack? = null

    fun findUsbOutput(): AudioDeviceInfo? = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }

    override fun open(sampleRate: Int) {
        check(track == null)
        val device = requireNotNull(findUsbOutput()) { "No FTX-1 USB audio output" }
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(min * 2, sampleRate / 2 * 4))
            .build()
        check(t.setPreferredDevice(device)) { "Unable to route TX audio to FTX-1" }
        t.play()
        track = t
    }

    override fun write(samples: FloatArray) {
        val t = checkNotNull(track)
        var p = 0
        while (p < samples.size) {
            val n = t.write(samples, p, samples.size - p, AudioTrack.WRITE_BLOCKING)
            check(n > 0) { "AudioTrack.write failed: $n" }
            p += n
        }
    }

    override fun close() {
        val t = track ?: return
        track = null
        runCatching { t.stop() }
        t.release()
    }
}
