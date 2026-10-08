package dev.n0png.fieldops.core

import dev.n0png.fieldops.core.propagation.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

object HttpsUrlConnectionPublicPropagationTransportTests {
    private var assertions = 0

    private fun checkThat(value: Boolean, message: String) {
        assertions++
        check(value) { message }
    }

    private fun <T> eq(expected: T, actual: T, message: String) {
        assertions++
        check(expected == actual) {
            "$message expected=$expected actual=$actual"
        }
    }

    private fun expectFailure(
        message: String,
        block: () -> Unit,
    ): Throwable {
        assertions++
        val failure = runCatching(block).exceptionOrNull()
        check(failure != null) { "Expected failure: $message" }
        return failure
    }

    @JvmStatic
    fun main(args: Array<String>) {
        configValidation()
        successfulGetConfiguresConnection()
        nonSuccessReadsErrorStream()
        declaredOversizeFailsBeforeRead()
        streamedOversizeFailsClosed()
        invalidUtf8FailsClosed()
        compressedResponseFailsClosed()
        timeoutIsRetryableAndCleansUp()
        connectionFactoryFailureIsRetryable()
        credentialedUrlFailsBeforeOpening()
        sourceAdapterPreservesTransportRetryability()
        sourceAdapterIntegrationUsesConcreteTransport()
        println(
            "CP-0008I concrete HTTPS transport tests: " +
                "PASS assertions=$assertions"
        )
    }

    private fun configValidation() {
        val config = HttpsPublicPropagationTransportConfig()
        eq(10_000, config.connectTimeoutMillis, "default connect timeout")
        eq(15_000, config.readTimeoutMillis, "default read timeout")
        eq("FTX-1-FieldOps", config.userAgent, "default user agent")

        expectFailure("zero connect timeout") {
            HttpsPublicPropagationTransportConfig(connectTimeoutMillis = 0)
        }
        expectFailure("excessive connect timeout") {
            HttpsPublicPropagationTransportConfig(connectTimeoutMillis = 120_001)
        }
        expectFailure("zero read timeout") {
            HttpsPublicPropagationTransportConfig(readTimeoutMillis = 0)
        }
        expectFailure("blank user agent") {
            HttpsPublicPropagationTransportConfig(userAgent = " ")
        }
        expectFailure("control character user agent") {
            HttpsPublicPropagationTransportConfig(userAgent = "FieldOps\nBad")
        }
    }

    private fun successfulGetConfiguresConnection() {
        val requestedUrl = "https://example.invalid/data"
        val body = """{"ok":true}"""
        val input = TrackingInputStream(body.toByteArray(StandardCharsets.UTF_8))
        val fake = FakeHttpsConnection(
            url = URL(requestedUrl),
            status = 200,
            responseContentType = "application/json; charset=utf-8",
            declaredContentLength = body.toByteArray(StandardCharsets.UTF_8).size.toLong(),
            input = input,
        )
        val transport = HttpsUrlConnectionPublicPropagationTransport(
            config = HttpsPublicPropagationTransportConfig(
                connectTimeoutMillis = 1_234,
                readTimeoutMillis = 5_678,
                userAgent = "FieldOps-Test/1",
            ),
            connectionFactory = HttpsConnectionFactory { url ->
                eq(requestedUrl, url.toExternalForm(), "factory gets exact URL")
                fake
            },
        )
        val request = request(
            requestedUrl,
            1024,
            setOf("text/plain", "application/json"),
        )

        val response = transport.get(request)

        eq(requestedUrl, response.requestedUrl, "response requested URL")
        eq(requestedUrl, response.effectiveUrl, "response effective URL")
        eq(200, response.statusCode, "response status")
        eq("application/json; charset=utf-8", response.contentType, "content type")
        eq(body, response.body, "body decoded exactly")

        eq("GET", fake.requestMethod, "GET method")
        eq(false, fake.instanceFollowRedirects, "redirects disabled")
        eq(1_234, fake.connectTimeout, "connect timeout configured")
        eq(5_678, fake.readTimeout, "read timeout configured")
        eq(false, fake.useCaches, "caches disabled")
        eq(true, fake.doInput, "input enabled")
        eq(false, fake.doOutput, "output disabled")
        eq(
            "application/json, text/plain",
            fake.getRequestProperty("Accept"),
            "Accept header deterministic",
        )
        eq("UTF-8", fake.getRequestProperty("Accept-Charset"), "UTF-8 requested")
        eq("identity", fake.getRequestProperty("Accept-Encoding"), "compression disabled")
        eq("FieldOps-Test/1", fake.getRequestProperty("User-Agent"), "user agent")
        eq(true, fake.connectCalled, "connect called")
        eq(true, input.closed, "input stream closed")
        eq(true, fake.disconnectCalled, "connection disconnected")
    }

    private fun nonSuccessReadsErrorStream() {
        val url = "https://example.invalid/error"
        val error = TrackingInputStream("busy".toByteArray())
        val fake = FakeHttpsConnection(
            url = URL(url),
            status = 503,
            responseContentType = "text/plain",
            declaredContentLength = 4,
            error = error,
        )
        val transport = transport(fake)
        val response = transport.get(request(url, 100, setOf("text/plain")))

        eq(503, response.statusCode, "503 preserved")
        eq("busy", response.body, "error stream body retained")
        eq(true, error.closed, "error stream closed")
        eq(true, fake.disconnectCalled, "503 disconnects")
    }

    private fun declaredOversizeFailsBeforeRead() {
        val url = "https://example.invalid/large"
        val input = TrackingInputStream("small".toByteArray())
        val fake = FakeHttpsConnection(
            url = URL(url),
            status = 200,
            responseContentType = "text/plain",
            declaredContentLength = 101,
            input = input,
        )
        val failure = expectFailure("declared body too large") {
            transport(fake).get(request(url, 100, setOf("text/plain")))
        }
        checkThat(
            failure is PublicPropagationTransportException &&
                !failure.retryable,
            "declared oversize is non-retryable",
        )
        checkThat(
            failure.message!!.contains("Content-Length"),
            "declared oversize reason",
        )
        eq(false, input.readStarted, "oversize rejected before stream read")
        eq(true, fake.disconnectCalled, "oversize disconnects")
    }

    private fun streamedOversizeFailsClosed() {
        val url = "https://example.invalid/chunked"
        val input = TrackingInputStream("123456".toByteArray())
        val fake = FakeHttpsConnection(
            url = URL(url),
            status = 200,
            responseContentType = "text/plain",
            declaredContentLength = -1,
            input = input,
        )
        val failure = expectFailure("streamed body too large") {
            transport(fake).get(request(url, 5, setOf("text/plain")))
        }
        checkThat(
            failure is PublicPropagationTransportException &&
                !failure.retryable,
            "stream oversize is non-retryable",
        )
        checkThat(
            failure.message!!.contains("streamed response exceeds"),
            "stream oversize reason",
        )
        eq(true, input.closed, "oversize stream closed")
        eq(true, fake.disconnectCalled, "oversize connection disconnected")
    }

    private fun invalidUtf8FailsClosed() {
        val url = "https://example.invalid/utf8"
        val bytes = byteArrayOf(0xC3.toByte(), 0x28)
        val input = TrackingInputStream(bytes)
        val fake = FakeHttpsConnection(
            url = URL(url),
            status = 200,
            responseContentType = "text/plain",
            declaredContentLength = bytes.size.toLong(),
            input = input,
        )
        val failure = expectFailure("invalid UTF-8") {
            transport(fake).get(request(url, 100, setOf("text/plain")))
        }
        checkThat(
            failure is PublicPropagationTransportException &&
                !failure.retryable,
            "invalid UTF-8 is non-retryable",
        )
        checkThat(
            failure.message!!.contains("valid UTF-8"),
            "invalid UTF-8 reason",
        )
        eq(true, input.closed, "invalid UTF-8 stream closed")
        eq(true, fake.disconnectCalled, "invalid UTF-8 disconnects")
    }

    private fun compressedResponseFailsClosed() {
        val url = "https://example.invalid/gzip"
        val input = TrackingInputStream("abc".toByteArray())
        val fake = FakeHttpsConnection(
            url = URL(url),
            status = 200,
            responseContentType = "text/plain",
            responseContentEncoding = "gzip",
            declaredContentLength = 3,
            input = input,
        )
        val failure = expectFailure("compressed response") {
            transport(fake).get(request(url, 100, setOf("text/plain")))
        }
        checkThat(
            failure is PublicPropagationTransportException &&
                !failure.retryable,
            "unexpected compression is non-retryable",
        )
        checkThat(
            failure.message!!.contains("Content-Encoding"),
            "compression reason",
        )
        eq(false, input.readStarted, "compression rejected before body read")
        eq(true, fake.disconnectCalled, "compressed response disconnects")
    }

    private fun timeoutIsRetryableAndCleansUp() {
        val url = "https://example.invalid/timeout"
        val input = ThrowingInputStream(
            SocketTimeoutException("simulated read timeout")
        )
        val fake = FakeHttpsConnection(
            url = URL(url),
            status = 200,
            responseContentType = "text/plain",
            declaredContentLength = -1,
            input = input,
        )
        val failure = expectFailure("read timeout") {
            transport(fake).get(request(url, 100, setOf("text/plain")))
        }
        checkThat(
            failure is PublicPropagationTransportException &&
                failure.retryable,
            "timeout is retryable",
        )
        checkThat(failure.message!!.contains("HTTPS timeout"), "timeout reason")
        eq(true, input.closed, "timeout stream closed")
        eq(true, fake.disconnectCalled, "timeout disconnects")
    }

    private fun connectionFactoryFailureIsRetryable() {
        val url = "https://example.invalid/factory"
        val transport = HttpsUrlConnectionPublicPropagationTransport(
            connectionFactory = HttpsConnectionFactory {
                throw IOException("simulated open failure")
            }
        )
        val failure = expectFailure("factory failure") {
            transport.get(request(url, 100, setOf("text/plain")))
        }
        checkThat(
            failure is PublicPropagationTransportException &&
                failure.retryable,
            "connection-open I/O failure is retryable",
        )
        checkThat(
            failure.message!!.contains("Unable to open HTTPS connection"),
            "factory failure reason",
        )
    }

    private fun credentialedUrlFailsBeforeOpening() {
        var factoryCalls = 0
        val transport = HttpsUrlConnectionPublicPropagationTransport(
            connectionFactory = HttpsConnectionFactory {
                factoryCalls++
                error("factory must not be reached")
            }
        )
        val request = PublicPropagationRequest(
            url = "https://user:secret@example.invalid/data",
            maxResponseBytes = 100,
            acceptedContentTypes = setOf("text/plain"),
        )
        val failure = expectFailure("URL credentials") {
            transport.get(request)
        }
        checkThat(
            failure is PublicPropagationTransportException &&
                !failure.retryable,
            "credential-bearing URL is non-retryable",
        )
        checkThat(
            failure.message!!.contains("user-info credentials"),
            "credential URL reason",
        )
        eq(0, factoryCalls, "credential URL rejected before opening connection")
    }

    private fun sourceAdapterPreservesTransportRetryability() {
        val now = 1_000L
        val nonRetryable = PublicPropagationTransport {
            throw PublicPropagationTransportException(
                "simulated contract rejection",
                retryable = false,
            )
        }
        val result = PublicPropagationSourceAdapters
            .noaaF107(nonRetryable, policy())
            .fetcher
            .fetch(now) as PropagationSourceFetchResult.Failure
        eq(false, result.retryable, "source adapter preserves non-retryable transport failure")
        checkThat(
            result.message.contains("simulated contract rejection"),
            "source adapter preserves transport failure message",
        )

        val retryable = PublicPropagationTransport {
            throw PublicPropagationTransportException(
                "simulated transient failure",
                retryable = true,
            )
        }
        val retryResult = PublicPropagationSourceAdapters
            .noaaF107(retryable, policy())
            .fetcher
            .fetch(now) as PropagationSourceFetchResult.Failure
        eq(true, retryResult.retryable, "source adapter preserves retryable transport failure")
    }

    private fun sourceAdapterIntegrationUsesConcreteTransport() {
        val url = NoaaSwpcPropagationAdapter.F107_SUMMARY_URL
        val body = """[{"flux":113,"time_tag":"2026-10-06T20:00:00"}]"""
        val input = TrackingInputStream(body.toByteArray(StandardCharsets.UTF_8))
        val fake = FakeHttpsConnection(
            url = URL(url),
            status = 200,
            responseContentType = "application/json",
            declaredContentLength = body.toByteArray(StandardCharsets.UTF_8).size.toLong(),
            input = input,
        )
        val concrete = transport(fake)
        val result = PublicPropagationSourceAdapters
            .noaaF107(concrete, policy())
            .fetcher
            .fetch(1_800_000_000_000L) as PropagationSourceFetchResult.Success

        eq(1, result.input.solarGeomagnetic.size, "concrete transport feeds existing F10.7 parser")
        eq(
            113.0,
            result.input.solarGeomagnetic.single().f107SolarFluxSfu,
            "concrete transport preserves parsed F10.7 value",
        )
        eq(true, fake.disconnectCalled, "integration connection disconnected")
    }

    private fun request(
        url: String,
        maxBytes: Int,
        contentTypes: Set<String>,
    ) = PublicPropagationRequest(
        url = url,
        maxResponseBytes = maxBytes,
        acceptedContentTypes = contentTypes,
    )

    private fun policy() =
        PropagationRefreshPolicy(
            cadenceMillis = 15 * 60_000L,
            initialRetryBackoffMillis = 60_000L,
            maximumRetryBackoffMillis = 15 * 60_000L,
        )

    private fun transport(
        connection: FakeHttpsConnection,
    ) = HttpsUrlConnectionPublicPropagationTransport(
        connectionFactory = HttpsConnectionFactory { connection }
    )

    private class TrackingInputStream(
        bytes: ByteArray,
    ) : ByteArrayInputStream(bytes) {
        var closed = false
        var readStarted = false

        override fun read(): Int {
            readStarted = true
            return super.read()
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            readStarted = true
            return super.read(b, off, len)
        }

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class ThrowingInputStream(
        private val failure: IOException,
    ) : InputStream() {
        var closed = false

        override fun read(): Int = throw failure

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = throw failure

        override fun close() {
            closed = true
        }
    }

    private class FakeHttpsConnection(
        url: URL,
        private val status: Int,
        private val responseContentType: String?,
        private val responseContentEncoding: String? = null,
        private val declaredContentLength: Long = -1,
        private val input: InputStream? = null,
        private val error: InputStream? = null,
    ) : HttpsURLConnection(url) {
        var connectCalled = false
        var disconnectCalled = false

        override fun connect() {
            connectCalled = true
        }

        override fun disconnect() {
            disconnectCalled = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = status

        override fun getInputStream(): InputStream =
            input ?: ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream? = error

        override fun getContentType(): String? = responseContentType

        override fun getContentEncoding(): String? = responseContentEncoding

        override fun getContentLengthLong(): Long = declaredContentLength

        override fun getCipherSuite(): String = "TLS_FAKE"

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate> =
            emptyArray()

        override fun getPeerPrincipal(): Principal? = null

        override fun getLocalPrincipal(): Principal? = null
    }
}
