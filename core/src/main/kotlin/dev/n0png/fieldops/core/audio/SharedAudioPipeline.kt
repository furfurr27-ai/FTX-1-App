package dev.n0png.fieldops.core.audio

import java.util.concurrent.CopyOnWriteArrayList

/**
 * One capture stream, two rate domains:
 *   - 48 kHz: APRS/Bell-202 and diagnostics
 *   - 12 kHz: FT8/FT4/FT2/JS8/WSPR/waterfall
 */
class SharedAudioPipeline {
    fun interface Consumer { fun onAudio(block: TimedPcmBlock) }

    private val raw48Consumers = CopyOnWriteArrayList<Consumer>()
    private val weak12Consumers = CopyOnWriteArrayList<Consumer>()
    private val decimator = FirDecimator4()
    private var weakSequence = 0L

    fun add48kConsumer(consumer: Consumer) { raw48Consumers += consumer }
    fun add12kConsumer(consumer: Consumer) { weak12Consumers += consumer }
    fun remove48kConsumer(consumer: Consumer) { raw48Consumers -= consumer }
    fun remove12kConsumer(consumer: Consumer) { weak12Consumers -= consumer }

    fun reset() {
        decimator.reset()
        weakSequence = 0
    }

    fun accept48k(block: TimedPcmBlock) {
        require(block.sampleRate == 48_000) { "FTX-1 shared capture is normalized to 48 kHz" }
        raw48Consumers.forEach { it.onAudio(block) }

        val r = decimator.process(block.samples)
        if (r.samples.isEmpty()) return
        val firstInputNanos = r.firstOutputInputIndex.toLong() * 1_000_000_000L / 48_000L
        val groupDelayNanos = r.groupDelayInputSamples.toLong() * 1_000_000_000L / 48_000L
        val weak = TimedPcmBlock(
            utcStartNanos = block.utcStartNanos + firstInputNanos - groupDelayNanos,
            sampleRate = 12_000,
            samples = r.samples,
            sequence = weakSequence++,
        )
        weak12Consumers.forEach { it.onAudio(weak) }
    }
}
