package dev.n0png.fieldops.core.logbook

class LotwSyncManager(private val transport: LotwTransport) {
    fun upload(qsos: List<QsoRecord>, signer: LotwSigner, stationProfileId: String): LotwUploadResponse {
        require(qsos.isNotEmpty()) { "No QSOs selected for LoTW upload" }
        val calls = qsos.map { it.stationCallsign.uppercase() }.toSet()
        require(calls.size == 1) { "A LoTW upload batch must use one station callsign" }
        val adif = AdifCodec.export(qsos)
        val tq8 = signer.signAdif(adif, stationProfileId)
        require(tq8.isNotEmpty()) { "TrustedQSL signer returned an empty payload" }
        return transport.uploadTq8(tq8)
    }

    /** Verify LoTW has actually accepted the uploaded QSOs. HTTP upload acceptance alone is not enough. */
    fun fetchAccepted(credentials: LotwCredentials, since: String? = null): LotwReport {
        val p = linkedMapOf("qso_query" to "1", "qso_qsl" to "no", "qso_withown" to "yes", "qso_mydetail" to "yes")
        if (!since.isNullOrBlank()) p["qso_qsorxsince"] = since
        return AdifCodec.parse(transport.query(credentials, p))
    }

    fun fetchConfirmations(credentials: LotwCredentials, since: String? = null): LotwReport {
        val p = linkedMapOf("qso_query" to "1", "qso_qsl" to "yes", "qso_withown" to "yes", "qso_mydetail" to "yes")
        if (!since.isNullOrBlank()) p["qso_qslsince"] = since
        return AdifCodec.parse(transport.query(credentials, p))
    }

    fun importAll(credentials: LotwCredentials): Pair<LotwReport, LotwReport> =
        fetchAccepted(credentials, null) to fetchConfirmations(credentials, null)

    fun reconcile(local: List<QsoRecord>, accepted: LotwReport?, confirmed: LotwReport?): Pair<List<QsoRecord>, LotwSyncSummary> {
        val acceptedByKey = accepted?.records.orEmpty().associateBy(::recordKey)
        val confirmedByKey = confirmed?.records.orEmpty().associateBy(::recordKey)
        var a = 0; var c = 0
        val updated = local.map { q ->
            val ar = acceptedByKey[q.lotwMatchKey()]
            val cr = confirmedByKey[q.lotwMatchKey()]
            var u = q
            if (ar != null && q.lotwUpload != LotwUploadState.ACCEPTED) {
                a++
                u = u.copy(lotwUpload = LotwUploadState.ACCEPTED, lotwRxQso = ar["APP_LOTW_RXQSO"], lotwRecordId = ar["APP_LOTW_RECORDID"])
            }
            if (cr != null && cr["QSL_RCVD"]?.uppercase() == "Y" && q.lotwConfirmation != LotwConfirmationState.CONFIRMED) {
                c++
                u = u.copy(lotwConfirmation = LotwConfirmationState.CONFIRMED, lotwRxQsl = cr["APP_LOTW_RXQSL"], lotwQslDate = cr["QSLRDATE"])
            }
            u
        }
        val known = local.map { it.lotwMatchKey() }.toSet()
        val remoteKeys = (acceptedByKey.keys + confirmedByKey.keys).toSet()
        return updated to LotwSyncSummary(
            acceptedNow = a,
            confirmedNow = c,
            unmatchedRemoteRecords = remoteKeys.count { it !in known },
            cursor = LotwSyncCursor(accepted?.lastQsoRx, confirmed?.lastQsl)
        )
    }

    private fun recordKey(r: Map<String, String>): String {
        val own = (r["STATION_CALLSIGN"] ?: r["APP_LOTW_OWNCALL"] ?: "").uppercase()
        val call = (r["CALL"] ?: "").uppercase()
        val date = r["QSO_DATE"] ?: ""
        val time = (r["TIME_ON"] ?: "").take(4)
        val band = (r["BAND"] ?: "").lowercase()
        return listOf(own, call, date, time, band).joinToString("|")
    }
}
