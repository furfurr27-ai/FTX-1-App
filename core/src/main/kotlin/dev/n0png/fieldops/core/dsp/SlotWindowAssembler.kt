package dev.n0png.fieldops.core.dsp

import dev.n0png.fieldops.core.audio.TimedPcmBlock
import dev.n0png.fieldops.core.digital.DigitalModeProfile
import dev.n0png.fieldops.core.digital.DigitalSlotClock
import kotlin.math.ceil
import kotlin.math.min

/**
 * Converts arbitrary 12 kHz callback chunks into UTC-aligned complete mode windows.
 * Gaps or timestamp discontinuities drop the partial window rather than decoding
 * time-shifted garbage.
 */
class SlotWindowAssembler(
    private val profile: DigitalModeProfile,
    private val sampleRate: Int = 12_000,
    private val onWindow: (SlotWindow) -> Unit,
) {
    data class SlotWindow(
        val slotStartUtcMillis: Long,
        val samples: FloatArray,
        val complete: Boolean,
    )

    private val expectedSamples = (profile.cycleMillis * sampleRate / 1000L).toInt()
    private var slotStartMillis: Long? = null
    private var buffer = FloatArray(expectedSamples)
    private var used = 0
    private var expectedNextUtcNanos: Long? = null

    fun reset() {
        slotStartMillis = null
        used = 0
        expectedNextUtcNanos = null
    }

    fun accept(block: TimedPcmBlock) {
        require(block.sampleRate == sampleRate)
        val expected = expectedNextUtcNanos
        val tolerance = 3_000_000L // 3 ms accommodates callback timestamp quantization.
        if (expected != null && kotlin.math.abs(block.utcStartNanos - expected) > tolerance) {
            reset()
        }

        var index = 0
        var cursorNanos = block.utcStartNanos
        while (index < block.samples.size) {
            val cursorMillis = Math.floorDiv(cursorNanos, 1_000_000L)
            val thisSlot = DigitalSlotClock.slotStartMillis(cursorMillis, profile)
            if (slotStartMillis != thisSlot) {
                if (used > 0 && slotStartMillis != null) {
                    onWindow(SlotWindow(slotStartMillis!!, buffer.copyOf(used), complete = false))
                }
                slotStartMillis = thisSlot
                used = 0
            }

            val slotEndNanos = (thisSlot + profile.cycleMillis) * 1_000_000L
            val nanosRemaining = slotEndNanos - cursorNanos
            if (nanosRemaining <= 0) {
                finishCurrent()
                continue
            }
            val untilBoundary = ceil(nanosRemaining * sampleRate / 1_000_000_000.0).toInt().coerceAtLeast(1)
            val take = min(min(untilBoundary, block.samples.size - index), expectedSamples - used)
            block.samples.copyInto(buffer, used, index, index + take)
            used += take
            index += take
            cursorNanos = block.utcNanosAt(index)

            if (used == expectedSamples || cursorNanos >= slotEndNanos) finishCurrent()
        }
        expectedNextUtcNanos = block.utcStartNanos + block.durationNanos
    }

    private fun finishCurrent() {
        val s = slotStartMillis ?: return
        onWindow(SlotWindow(s, buffer.copyOf(used), complete = used == expectedSamples))
        slotStartMillis = s + profile.cycleMillis
        used = 0
    }
}
