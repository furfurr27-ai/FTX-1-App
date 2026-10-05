package dev.n0png.fieldops.core.aprs

import kotlin.math.*

/**
 * SmartBeaconing decision engine. Defaults are intentionally configurable;
 * Android preferences/radio-menu values should own the final profile.
 */
class SmartBeaconing(private val cfg: Config = Config()) {
    data class Config(
        val slowSpeedKph: Double = 5.0,
        val fastSpeedKph: Double = 100.0,
        val slowRateSeconds: Int = 1200,
        val fastRateSeconds: Int = 60,
        val turnTimeSeconds: Int = 15,
        val turnMinDegrees: Double = 10.0,
        val turnSlope: Double = 240.0
    ) {
        init {
            require(slowSpeedKph >= 0 && fastSpeedKph > slowSpeedKph)
            require(slowRateSeconds > 0 && fastRateSeconds > 0)
            require(turnTimeSeconds > 0 && turnMinDegrees >= 0 && turnSlope >= 0)
        }
    }

    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val timeMillis: Long,
        val speedMps: Double,
        val bearingDeg: Double? = null
    )

    fun speedRateSeconds(speedMps: Double): Int {
        val slow = cfg.slowSpeedKph / 3.6
        val fast = cfg.fastSpeedKph / 3.6
        return when {
            speedMps <= slow -> cfg.slowRateSeconds
            speedMps >= fast -> cfg.fastRateSeconds
            else -> (cfg.fastRateSeconds +
                (cfg.slowRateSeconds - cfg.fastRateSeconds) * (fast - speedMps) / (fast - slow)).toInt()
        }
    }

    fun shouldBeacon(previous: Fix?, current: Fix): Boolean {
        if (previous == null) return true
        val dtSec = (current.timeMillis - previous.timeMillis) / 1000.0
        if (dtSec < 0) return false

        if (cornerPeg(previous, current, dtSec)) return true
        val inferred = if (dtSec > 0) distanceMeters(previous, current) / dtSec else 0.0
        val effectiveSpeed = max(max(current.speedMps, previous.speedMps), inferred)
        return dtSec >= speedRateSeconds(effectiveSpeed)
    }

    private fun cornerPeg(previous: Fix, current: Fix, dtSec: Double): Boolean {
        val a = previous.bearingDeg ?: return false
        val b = current.bearingDeg ?: return false
        if (current.speedMps <= 0.0 || dtSec < cfg.turnTimeSeconds) return false
        val mph = current.speedMps * 2.2369362920544
        if (mph <= 0.0) return false
        val threshold = cfg.turnMinDegrees + cfg.turnSlope / mph
        return bearingDelta(a, b) > threshold
    }

    fun bearingDelta(a: Double, b: Double): Double {
        val d = abs((a - b) % 360.0)
        return if (d <= 180.0) d else 360.0 - d
    }

    fun distanceMeters(a: Fix, b: Fix): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(b.latitude)
        val dp = Math.toRadians(b.latitude - a.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
