package dev.n0png.fieldops.core.time

import kotlin.math.abs

/** Maps Android's monotonic clock to UTC without using wall-clock time for audio deltas. */
class DisciplinedClock(
    initialUtcNanos: Long,
    initialMonotonicNanos: Long,
    initialSource: Source = Source.SYSTEM,
) {
    enum class Source { SYSTEM, NTP, GPS }

    data class Snapshot(
        val utcNanos: Long,
        val monotonicNanos: Long,
        val source: Source,
        val uncertaintyNanos: Long,
    )

    @Volatile private var anchor = Snapshot(
        initialUtcNanos,
        initialMonotonicNanos,
        initialSource,
        if (initialSource == Source.SYSTEM) 50_000_000L else 5_000_000L,
    )

    fun snapshot(): Snapshot = anchor

    fun utcNanosAt(monotonicNanos: Long): Long {
        val a = anchor
        return a.utcNanos + (monotonicNanos - a.monotonicNanos)
    }

    fun utcMillisAt(monotonicNanos: Long): Long = utcNanosAt(monotonicNanos) / 1_000_000L

    /**
     * Installs a new measured anchor.  The caller decides whether the source is
     * trustworthy (GPS/NTP).  Returning the previous prediction error makes
     * clock jumps visible to diagnostics and lets TX scheduling be inhibited.
     */
    @Synchronized
    fun discipline(
        measuredUtcNanos: Long,
        measuredMonotonicNanos: Long,
        source: Source,
        uncertaintyNanos: Long,
    ): Long {
        require(uncertaintyNanos >= 0)
        val predicted = utcNanosAt(measuredMonotonicNanos)
        val error = measuredUtcNanos - predicted
        anchor = Snapshot(measuredUtcNanos, measuredMonotonicNanos, source, uncertaintyNanos)
        return error
    }

    fun safeForTimedTx(maxUncertaintyMillis: Long = 100): Boolean =
        snapshot().uncertaintyNanos <= maxUncertaintyMillis * 1_000_000L

    fun absoluteErrorMillis(measuredUtcNanos: Long, monotonicNanos: Long): Double =
        abs(measuredUtcNanos - utcNanosAt(monotonicNanos)) / 1_000_000.0
}
