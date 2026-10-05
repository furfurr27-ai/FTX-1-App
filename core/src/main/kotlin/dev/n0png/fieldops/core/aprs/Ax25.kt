package dev.n0png.fieldops.core.aprs

import java.io.ByteArrayOutputStream

/** AX.25 UI frame primitives for APRS. */
data class Ax25Address(val callsign: String, val ssid: Int = 0, val repeated: Boolean = false) {
    init {
        require(Regex("^[A-Za-z0-9]{1,6}$").matches(callsign)) { "AX.25 callsign must be 1-6 alphanumeric chars" }
        require(ssid in 0..15)
    }

    fun encode(last: Boolean): ByteArray {
        val call = callsign.uppercase().padEnd(6, ' ')
        val out = ByteArray(7)
        for (i in 0 until 6) out[i] = (call[i].code shl 1).toByte()
        var ssidByte = 0x60 or (ssid shl 1)
        if (repeated) ssidByte = ssidByte or 0x80
        if (last) ssidByte = ssidByte or 0x01
        out[6] = ssidByte.toByte()
        return out
    }

    override fun toString(): String = callsign.uppercase() + if (ssid == 0) "" else "-$ssid"
}

data class Ax25UiFrame(
    val destination: Ax25Address,
    val source: Ax25Address,
    val path: List<Ax25Address> = emptyList(),
    val information: ByteArray
) {
    fun encode(): ByteArray {
        val addresses = listOf(destination, source) + path
        require(addresses.size in 2..10) { "AX.25 address field is unreasonable" }
        require(information.size <= 256) { "APRS information field too large for this app profile" }
        val out = ByteArrayOutputStream()
        addresses.forEachIndexed { index, address ->
            out.write(address.encode(last = index == addresses.lastIndex))
        }
        out.write(0x03) // UI frame
        out.write(0xF0) // no layer 3
        out.write(information)
        return out.toByteArray()
    }

    companion object {
        fun aprsText(
            source: Ax25Address,
            information: String,
            destination: Ax25Address = Ax25Address("APRS"),
            path: List<Ax25Address> = listOf(Ax25Address("WIDE1", 1), Ax25Address("WIDE2", 1))
        ) = Ax25UiFrame(destination, source, path, information.toByteArray(Charsets.US_ASCII))
    }
}
