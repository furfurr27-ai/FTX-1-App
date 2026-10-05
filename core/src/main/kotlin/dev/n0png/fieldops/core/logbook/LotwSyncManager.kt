package dev.n0png.fieldops.core.logbook

class LotwSyncManager(private val transport: LotwTransport) {

    /**
     * CP-0003B transaction:
     *
     * sign -> upload -> verify every QSO in LoTW accepted report -> commit
     *
     * A TrustedQSL duplicate-tracking transaction is never committed merely
     * because the HTTP upload endpoint accepted the .tq8 file.
     */
    fun uploadTransactional(
        qsos: List<QsoRecord>,
        signer: TransactionalLotwSigner,
        context: LotwUploadContext,
        privateKeyPassword: CharArray,
        acceptancePolicy: LotwAcceptancePolicy = LotwAcceptancePolicy(),
        verificationDelay: LotwVerificationDelay = LotwVerificationDelay.SYSTEM,
    ): LotwTransactionalUploadResult {
        validateBatch(qsos, context.stationProfile)

        val queued = qsos.map { it.copy(lotwUpload = LotwUploadState.QUEUED) }
        val adif = AdifCodec.export(queued)
        val request = LotwSigningRequest(
            adif = adif,
            stationProfileId = context.stationProfile.id,
            stationLocationName = context.tqslStationLocationName,
            expectedStationCallsign = context.stationProfile.stationCallsign,
            expectedDxcc = context.stationProfile.dxcc,
        )

        val session = try {
            signer.beginSigning(request, privateKeyPassword)
        } catch (_: Throwable) {
            return LotwTransactionalUploadResult(
                outcome = LotwTransactionOutcome.SIGNING_FAILED,
                qsos = queued,
            )
        }

        try {
            val upload = try {
                transport.uploadTq8(session.tq8Payload)
            } catch (_: Throwable) {
                rollbackOpen(session)
                return LotwTransactionalUploadResult(
                    outcome = LotwTransactionOutcome.UPLOAD_FAILED,
                    qsos = queued,
                )
            }

            if (upload.httpCode !in 200..299) {
                rollbackOpen(session)
                return LotwTransactionalUploadResult(
                    outcome = LotwTransactionOutcome.UPLOAD_FAILED,
                    qsos = queued,
                    uploadResponse = upload,
                )
            }

            if (!upload.accepted) {
                rollbackOpen(session)
                return LotwTransactionalUploadResult(
                    outcome = LotwTransactionOutcome.UPLOAD_REJECTED,
                    qsos = queued.map { it.copy(lotwUpload = LotwUploadState.REJECTED) },
                    uploadResponse = upload,
                )
            }

            val submitted = queued.map { it.copy(lotwUpload = LotwUploadState.SUBMITTED) }
            val expectedKeys = submitted.map { it.lotwMatchKey() }.toSet()

            var lastReport: LotwReport? = null
            for (attempt in 0 until acceptancePolicy.attempts) {
                val delayMillis = if (attempt == 0) {
                    acceptancePolicy.initialDelayMillis
                } else {
                    acceptancePolicy.retryDelayMillis
                }
                verificationDelay.sleep(delayMillis)

                val report = try {
                    fetchAccepted(context.credentials, context.acceptedSince)
                } catch (_: Throwable) {
                    if (attempt + 1 == acceptancePolicy.attempts) {
                        rollbackOpen(session)
                        return LotwTransactionalUploadResult(
                            outcome = LotwTransactionOutcome.VERIFICATION_FAILED,
                            qsos = submitted,
                            uploadResponse = upload,
                            acceptedReport = lastReport,
                        )
                    }
                    continue
                }

                lastReport = report
                val acceptedKeys = report.records.map(::recordKey).toSet()
                if (expectedKeys.all { it in acceptedKeys }) {
                    try {
                        session.commit()
                    } catch (_: Throwable) {
                        rollbackOpen(session)
                        return LotwTransactionalUploadResult(
                            outcome = LotwTransactionOutcome.VERIFICATION_FAILED,
                            qsos = submitted,
                            uploadResponse = upload,
                            acceptedReport = report,
                        )
                    }

                    return LotwTransactionalUploadResult(
                        outcome = LotwTransactionOutcome.ACCEPTED,
                        qsos = submitted.map { q ->
                            val match = report.records.firstOrNull { recordKey(it) == q.lotwMatchKey() }
                            q.copy(
                                lotwUpload = LotwUploadState.ACCEPTED,
                                lotwRxQso = match?.get("APP_LOTW_RXQSO"),
                                lotwRecordId = match?.get("APP_LOTW_RECORDID"),
                            )
                        },
                        uploadResponse = upload,
                        acceptedReport = report,
                    )
                }
            }

            rollbackOpen(session)
            return LotwTransactionalUploadResult(
                outcome = LotwTransactionOutcome.VERIFICATION_FAILED,
                qsos = submitted,
                uploadResponse = upload,
                acceptedReport = lastReport,
            )
        } finally {
            runCatching { session.close() }
        }
    }

    /** Verify LoTW has actually accepted uploaded QSOs. HTTP upload acceptance alone is not enough. */
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
        var a = 0
        var c = 0
        val updated = local.map { q ->
            val ar = acceptedByKey[q.lotwMatchKey()]
            val cr = confirmedByKey[q.lotwMatchKey()]
            var u = q
            if (ar != null && q.lotwUpload != LotwUploadState.ACCEPTED) {
                a++
                u = u.copy(
                    lotwUpload = LotwUploadState.ACCEPTED,
                    lotwRxQso = ar["APP_LOTW_RXQSO"],
                    lotwRecordId = ar["APP_LOTW_RECORDID"],
                )
            }
            if (cr != null && cr["QSL_RCVD"]?.uppercase() == "Y" &&
                q.lotwConfirmation != LotwConfirmationState.CONFIRMED) {
                c++
                u = u.copy(
                    lotwConfirmation = LotwConfirmationState.CONFIRMED,
                    lotwRxQsl = cr["APP_LOTW_RXQSL"],
                    lotwQslDate = cr["QSLRDATE"],
                )
            }
            u
        }
        val known = local.map { it.lotwMatchKey() }.toSet()
        val remoteKeys = (acceptedByKey.keys + confirmedByKey.keys).toSet()
        return updated to LotwSyncSummary(
            acceptedNow = a,
            confirmedNow = c,
            unmatchedRemoteRecords = remoteKeys.count { it !in known },
            cursor = LotwSyncCursor(accepted?.lastQsoRx, confirmed?.lastQsl),
        )
    }

    private fun validateBatch(qsos: List<QsoRecord>, profile: LotwStationProfile) {
        require(qsos.isNotEmpty()) { "No QSOs selected for LoTW upload" }

        val calls = qsos.map { it.stationCallsign.trim().uppercase() }.toSet()
        require(calls.size == 1) { "A LoTW upload batch must use one station callsign" }
        require(calls.single() == profile.stationCallsign.trim().uppercase()) {
            "QSO station callsign does not match the selected LoTW station profile"
        }

        val ids = qsos.map { it.id }
        require(ids.toSet().size == ids.size) { "LoTW upload batch contains duplicate local QSO ids" }

        val keys = qsos.map { it.lotwMatchKey() }
        require(keys.toSet().size == keys.size) {
            "LoTW upload batch contains ambiguous acceptance match keys"
        }
    }

    private fun rollbackOpen(session: LotwSigningSession) {
        if (session.state == LotwSigningSessionState.OPEN) {
            runCatching { session.rollback() }
        }
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
