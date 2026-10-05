package dev.n0png.fieldops.core.aprs

import kotlin.math.abs
import kotlin.math.floor

/** APRS uncompressed position encoder (no timestamp). */
object AprsPosition {
    fun encode(latitude: Double, longitude: Double, symbolTable: Char = '/', symbolCode: Char = '>', comment: String = ""): String {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        require(comment.all { it.code in 32..126 })
        val lat = degreesMinutes(latitude, true)
        val lon = degreesMinutes(longitude, false)
        return "!$lat$symbolTable$lon$symbolCode$comment"
    }

    private fun degreesMinutes(value: Double, latitude: Boolean): String {
        val av = abs(value)
        val deg = floor(av).toInt()
        val minutes = (av - deg) * 60.0
        val hemi = if (latitude) {
            if (value >= 0) 'N' else 'S'
        } else {
            if (value >= 0) 'E' else 'W'
        }
        return if (latitude) "%02d%05.2f%c".format(deg, minutes, hemi)
        else "%03d%05.2f%c".format(deg, minutes, hemi)
    }
}
