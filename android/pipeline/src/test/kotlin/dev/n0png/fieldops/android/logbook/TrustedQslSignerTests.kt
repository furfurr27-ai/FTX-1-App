package dev.n0png.fieldops.android.logbook

import dev.n0png.fieldops.core.logbook.LotwSigningRequest
import dev.n0png.fieldops.core.logbook.LotwStationProfile
import dev.n0png.fieldops.core.logbook.LotwSigningSessionState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object TrustedQslSignerTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun expectFailure(
        containsCode: String? = null,
        forbidden: List<String> = emptyList(),
        block: () -> Unit,
    ) {
        val failure = runCatching(block).exceptionOrNull()
        checkThat(failure != null, "expected operation to fail")
        val message = failure?.message.orEmpty()
        if (containsCode != null) {
            checkThat(message.contains(containsCode), "failure did not contain expected status code: " + message)
        }
        for (secret in forbidden) {
            checkThat(!message.contains(secret), "failure leaked sensitive material")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) {
            "usage: TrustedQslSignerTests <data-directory> <resource-directory>"
        }
        val dataDirectory = args[0]
        val resourceDirectory = args[1]

        testUnavailableLibraryFailsClosed(dataDirectory, resourceDirectory)
        testMissingResourceConfigFailsClosed(dataDirectory, resourceDirectory)

        val signer = TrustedQslSigner(dataDirectory, resourceDirectory)
        val homeProfile = LotwStationProfile(
            id = "home",
            name = "Home",
            stationCallsign = "N0PNG",
            gridSquare = "JN49",
            dxcc = 230,
            cqZone = 14,
            ituZone = 28,
            country = "Germany",
        )
        signer.ensureStationLocation(homeProfile, "Home")

        expectFailure(
            containsCode = "133",
            forbidden = listOf("Home", "JO40"),
        ) {
            signer.ensureStationLocation(
                homeProfile.copy(gridSquare = "JO40"),
                "Home",
            )
        }

        val wiesbadenProfile = homeProfile.copy(
            id = "wiesbaden",
            name = "Wiesbaden",
        )
        signer.ensureStationLocation(wiesbadenProfile, "Wiesbaden")
        signer.ensureStationLocation(wiesbadenProfile, "Wiesbaden")

        val request = LotwSigningRequest(
            adif = testAdif(),
            stationProfileId = "wiesbaden",
            stationLocationName = "Wiesbaden",
            expectedStationCallsign = "N0PNG",
            expectedDxcc = 230,
        )

        expectFailure(
            containsCode = "141",
            forbidden = listOf("key-pass", "N0PNG"),
        ) {
            signer.beginSigning(request, "key-pass".toCharArray())
        }

        expectFailure {
            signer.importPkcs12(ByteArray(0), "p12-pass".toCharArray(), "key-pass".toCharArray())
        }

        expectFailure(
            containsCode = "121",
            forbidden = listOf("wrong-p12-secret", "key-pass"),
        ) {
            signer.importPkcs12(
                byteArrayOf(1, 2, 3, 4),
                "wrong-p12-secret".toCharArray(),
                "key-pass".toCharArray(),
            )
        }

        val backup = testBackup()
        signer.importBackup(backup)
        checkThat(backup.isNotEmpty(), "caller backup buffer was unexpectedly modified")

        val container = byteArrayOf(7, 6, 5, 4, 3, 2, 1)
        val p12Password = "p12-pass".toCharArray()
        val keyPassword = "key-pass".toCharArray()
        signer.importPkcs12(container, p12Password, keyPassword)
        checkThat(container.contentEquals(byteArrayOf(7, 6, 5, 4, 3, 2, 1)), "caller PKCS#12 buffer was modified")
        checkThat(String(p12Password) == "p12-pass", "caller PKCS#12 password array was modified")
        checkThat(String(keyPassword) == "key-pass", "caller key password array was modified")

        expectFailure(
            containsCode = "130",
            forbidden = listOf("MissingLocation", "key-pass"),
        ) {
            signer.beginSigning(
                request.copy(stationLocationName = "MissingLocation"),
                keyPassword,
            )
        }

        expectFailure(
            containsCode = "131",
            forbidden = listOf("W1AW", "key-pass"),
        ) {
            signer.beginSigning(
                request.copy(expectedStationCallsign = "W1AW"),
                keyPassword,
            )
        }

        expectFailure(
            containsCode = "132",
            forbidden = listOf("291", "key-pass"),
        ) {
            signer.beginSigning(
                request.copy(expectedDxcc = 291),
                keyPassword,
            )
        }

        expectFailure(
            containsCode = "150",
            forbidden = listOf("wrong-key-secret"),
        ) {
            signer.beginSigning(
                request,
                "wrong-key-secret".toCharArray(),
            )
        }

        val rolledByClose = signer.beginSigning(request, keyPassword)
        checkThat(rolledByClose.state == LotwSigningSessionState.OPEN, "new session must be OPEN")
        val payload = rolledByClose.tq8Payload
        checkThat(payload.size > 20, "signed tq8 payload is unexpectedly short")
        checkThat((payload[0].toInt() and 0xff) == 0x1f && (payload[1].toInt() and 0xff) == 0x8b, "tq8 payload must be gzip/zlib output")
        val gabbi = gunzip(payload)
        checkThat(gabbi.contains("<Rec_Type:5>tCERT"), "signed fixture output lost certificate GABBI")
        checkThat(gabbi.contains("<Rec_Type:8>tCONTACT"), "signed fixture output lost contact GABBI")
        checkThat(gabbi.contains("SIGN_LOTW_V2.0"), "signed fixture output lost signature field")
        checkThat(!gabbi.contains("p12-pass") && !gabbi.contains("key-pass"), "signed output leaked a password")

        val payloadCopy = rolledByClose.tq8Payload
        payloadCopy[0] = 0
        checkThat((rolledByClose.tq8Payload[0].toInt() and 0xff) == 0x1f, "session exposed mutable internal payload")
        rolledByClose.close()
        checkThat(rolledByClose.state == LotwSigningSessionState.ROLLED_BACK, "close must roll back an open signer transaction")

        val committed = signer.beginSigning(request, keyPassword)
        committed.commit()
        checkThat(committed.state == LotwSigningSessionState.COMMITTED, "commit state mismatch")
        expectFailure { committed.rollback() }
        committed.close()
        checkThat(committed.state == LotwSigningSessionState.COMMITTED, "close after commit must remain committed")

        val rolled = signer.beginSigning(request, keyPassword)
        rolled.rollback()
        checkThat(rolled.state == LotwSigningSessionState.ROLLED_BACK, "explicit rollback state mismatch")
        expectFailure { rolled.commit() }
        rolled.close()

        val alternateResource = File(resourceDirectory).resolveSibling("alternate-tqsl-resource")
        alternateResource.mkdirs()
        File(alternateResource, "config.xml").writeText("<tqslconfig/>")
        expectFailure(
            containsCode = "101",
            forbidden = listOf(dataDirectory, resourceDirectory),
        ) {
            TrustedQslSigner(dataDirectory, alternateResource.absolutePath)
        }

        println("TrustedQSL signer bridge tests: PASS assertions=" + assertions)
    }

    private fun testUnavailableLibraryFailsClosed(
        dataDirectory: String,
        resourceDirectory: String,
    ) {
        expectFailure(
            forbidden = listOf("loader-secret"),
        ) {
            TrustedQslSigner(
                dataDirectory = dataDirectory,
                resourceDirectory = resourceDirectory,
                libraryLoader = { throw UnsatisfiedLinkError("loader-secret") },
            )
        }
    }

    private fun testMissingResourceConfigFailsClosed(
        dataDirectory: String,
        resourceDirectory: String,
    ) {
        val missing = File(resourceDirectory).resolveSibling("missing-tqsl-resource")
        missing.mkdirs()
        File(missing, "config.xml").delete()
        expectFailure(
            containsCode = "102",
            forbidden = listOf(dataDirectory, missing.absolutePath),
        ) {
            TrustedQslSigner(dataDirectory, missing.absolutePath)
        }
    }

    private fun gunzip(payload: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(payload)).bufferedReader().use { it.readText() }

    private fun testBackup(): ByteArray {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <TQSL_Configuration>
              <Certificates>
                <RootCert>ROOT-CERT</RootCert>
                <CACert>CA-CERT</CACert>
                <UserCert CallSign="N0PNG" dxcc="230" serial="1">
                  <SignedCert>USER-CERT</SignedCert>
                  <PrivateKey>PRIVATE-KEY-MATERIAL</PrivateKey>
                </UserCert>
              </Certificates>
              <Locations>
                <Location name="Home" CALL="N0PNG" DXCC="230" GRIDSQUARE="JN49" CQZ="14" ITUZ="28" />
              </Locations>
            </TQSL_Configuration>
        """.trimIndent()
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(xml.toByteArray()) }
        return out.toByteArray()
    }

    private fun testAdif(): String = """
        <ADIF_VER:5>3.1.6
        <PROGRAMID:13>FTX1_FieldOps
        <EOH>
        <CALL:4>W1AW
        <STATION_CALLSIGN:5>N0PNG
        <QSO_DATE:8>20261005
        <TIME_ON:6>132800
        <BAND:3>40m
        <MODE:3>FT8
        <MY_GRIDSQUARE:4>JN49
        <MY_DXCC:3>230
        <EOR>
    """.trimIndent()
}
