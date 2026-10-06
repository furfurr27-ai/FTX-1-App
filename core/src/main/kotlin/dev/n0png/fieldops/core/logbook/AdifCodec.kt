package dev.n0png.fieldops.core.logbook

object AdifCodec {
    private val tag = Regex("<([^:>]+)(?::(\\d+)(?::[^>]+)?)?>", RegexOption.IGNORE_CASE)

    fun export(qsos: List<QsoRecord>, programId: String = "FTX1_FieldOps"): String = buildString {
        append("<ADIF_VER:5>3.1.6\n")
        append("<PROGRAMID:${programId.length}>$programId\n<EOH>\n")
        for (q in qsos) {
            field("CALL", q.call)
            field("STATION_CALLSIGN", q.stationCallsign)
            field("QSO_DATE", q.qsoDate)
            field("TIME_ON", q.timeOn)
            field("BAND", q.band)
            field("MODE", q.mode)
            q.submode?.let { field("SUBMODE", it) }
            q.exactFrequencyMhz?.let { field("FREQ", it) }
            q.exactRemoteGrid?.let { field("GRIDSQUARE", it) }
            q.rstSent?.let { field("RST_SENT", it) }
            q.rstRcvd?.let { field("RST_RCVD", it) }
            q.qsoDateOff?.let { field("QSO_DATE_OFF", it) }
            q.timeOff?.let { field("TIME_OFF", it) }
            q.exactStationGrid?.let { field("MY_GRIDSQUARE", it) }
            q.myDxcc?.let { field("MY_DXCC", it.toString()) }
            q.myCqZone?.let { field("MY_CQ_ZONE", it.toString()) }
            q.myItuZone?.let { field("MY_ITU_ZONE", it.toString()) }
            q.myCountry?.let { field("MY_COUNTRY", it) }
            q.myState?.let { field("MY_STATE", it) }
            append("<EOR>\n")
        }
    }

    fun parse(text: String): LotwReport {
        val headerEnd = text.indexOf("<EOH>", ignoreCase = true)
        require(headerEnd >= 0) { "Not ADIF: missing <EOH>" }
        val header = parseSection(text.substring(0, headerEnd))
        val body = text.substring(headerEnd + 5)
        val records = body.split(Regex("(?i)<EOR>")).mapNotNull { chunk ->
            val m = parseSection(chunk)
            if (m.isEmpty()) null else m
        }
        return LotwReport(header, records)
    }

    private fun StringBuilder.field(name: String, value: String) {
        append('<').append(name).append(':').append(value.length).append('>').append(value).append('\n')
    }

    private fun parseSection(section: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var pos = 0
        while (true) {
            val m = tag.find(section, pos) ?: break
            val name = m.groupValues[1].uppercase()
            val len = m.groupValues[2].toIntOrNull()
            val start = m.range.last + 1
            if (len != null && start + len <= section.length) {
                out[name] = section.substring(start, start + len)
                pos = start + len
            } else {
                pos = start
            }
        }
        return out
    }
}
