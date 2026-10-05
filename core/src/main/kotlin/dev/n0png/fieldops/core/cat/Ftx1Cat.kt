package dev.n0png.fieldops.core.cat

/**
 * Pure command builder/parser for the Yaesu FTX-1 CAT protocol.
 *
 * This module deliberately has no Android dependencies so it can be fuzzed and
 * unit-tested independently of USB lifecycle code.
 */
object Ftx1Cat {
    enum class Vfo(val code: Char) { MAIN('0'), SUB('1') }

    enum class Mode(val code: Char) {
        LSB('1'), USB('2'), CW_U('3'), FM('4'), AM('5'), RTTY_L('6'), CW_L('7'),
        DATA_L('8'), RTTY_U('9'), DATA_FM('A'), FM_N('B'), DATA_U('C'), AM_N('D'),
        PSK('E'), DATA_FM_N('F'), C4FM_DN('G'), C4FM_VW('H')
    }

    enum class AprsModemSelect(val value: String) { OFF("0"), AUTO("1"), MAIN("2"), SUB("3") }
    enum class AprsModemType(val value: String) { AFSK_1200("0"), FSK_9600("1") }
    enum class AprsDigiPath(val value: String) { OFF("0"), WIDE1_1("1"), WIDE1_1_WIDE2_1("2") }
    enum class AprsBeaconType(val value: String) { OFF("0"), AUTO("1"), SMART("2") }

    fun setMainFrequencyHz(hz: Long): String {
        require(hz in 0..999_999_999L) { "CAT frequency must fit the FTX-1 9-digit FA field" }
        return "FA%09d;".format(hz)
    }

    fun readMainFrequency(): String = "FA;"

    fun setMode(vfo: Vfo, mode: Mode): String = "MD${vfo.code}${mode.code};"
    fun readMode(vfo: Vfo): String = "MD${vfo.code};"

    fun pttOn(): String = "TX1;"
    fun pttOff(): String = "TX0;"
    fun readPtt(): String = "TX;"

    fun readId(): String = "ID;"
    fun readInformation(): String = "IF;"

    /** Generic EX menu write. P1/P2/P3 are always two decimal digits. */
    fun menuSet(p1: Int, p2: Int, p3: Int, parameter: String): String {
        require(p1 in 0..99 && p2 in 0..99 && p3 in 0..99)
        require(parameter.isNotEmpty())
        require(parameter.none { it == ';' || it == '\r' || it == '\n' }) { "Unsafe CAT menu parameter" }
        return "EX%02d%02d%02d%s;".format(p1, p2, p3, parameter)
    }

    fun menuRead(p1: Int, p2: Int, p3: Int): String {
        require(p1 in 0..99 && p2 in 0..99 && p3 in 0..99)
        return "EX%02d%02d%02d;".format(p1, p2, p3)
    }

    // Current English CAT manual Table 3 APRS controls.
    fun setAprsModem(select: AprsModemSelect) = menuSet(6, 1, 1, select.value)
    fun setAprsModemType(type: AprsModemType) = menuSet(6, 1, 2, type.value)

    fun setAprsCallsign(callsignSsid: String): String {
        val s = callsignSsid.uppercase()
        require(Regex("^[A-Z0-9]{1,6}(-([0-9]|1[0-5]))?$").matches(s)) { "Invalid APRS callsign/SSID" }
        return menuSet(6, 1, 5, s)
    }

    fun setAprsDigiPath(path: AprsDigiPath) = menuSet(6, 4, 1, path.value)
    fun setAprsBeaconType(type: AprsBeaconType) = menuSet(7, 1, 1, type.value)

    /** PRESET1 TX BPF: 0=50-3050, 1=100-2900, 2=200-2800, 3=300-2700, 4=400-2600. */
    fun setPreset1TxBpf(selection: Int): String {
        require(selection in 0..4)
        return menuSet(9, 1, 13, selection.toString())
    }

    /** PRESET1 MOD SOURCE: 0=MIC, 1=USB, 2=REAR, 3=AUTO in the English 2508C table. */
    fun setPreset1ModSource(selection: Int): String {
        require(selection in 0..3)
        return menuSet(9, 1, 14, selection.toString())
    }

    fun splitReplies(bytesAsAscii: String): List<String> =
        bytesAsAscii.split(';').map { it.trim() }.filter { it.isNotEmpty() }.map { "$it;" }

    fun parseMainFrequency(reply: String): Long? {
        val match = Regex("^FA(\\d{9});$").matchEntire(reply.trim()) ?: return null
        return match.groupValues[1].toLongOrNull()
    }
}
