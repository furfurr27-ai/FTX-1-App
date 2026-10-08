package dev.n0png.fieldops.core.propagation

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

data class HttpsPublicPropagationTransportConfig(
    val connectTimeoutMillis: Int = 10_000,
    val readTimeoutMillis: Int = 15_000,
    val userAgent: String = "FTX-1-FieldOps",
) {
    init {
        require(connectTimeoutMillis in 1..120_000) {
            "HTTPS connect timeout must be in 1..120000 ms"
        }
        require(readTimeoutMillis in 1..120_000) {
            "HTTPS read timeout must be in 1..120000 ms"
        }
        require(userAgent.isNotBlank()) {
            "HTTPS user agent must not be blank"
        }
        require(userAgent.length <= 128) {
            "HTTPS user agent must not exceed 128 characters"
        }
        require(userAgent.none { it.code < 0x20 || it.code == 0x7F }) {
            "HTTPS user agent must not contain control characters"
        }
    }
}

fun interface HttpsConnectionFactory {
    fun open(url: URL): HttpsURLConnection
}

class PublicPropagationTransportException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : IOException(message, cause)

class HttpsUrlConnectionPublicPropagationTransport(
    private val config: HttpsPublicPropagationTransportConfig =
        HttpsPublicPropagationTransportConfig(),
    private val connectionFactory: HttpsConnectionFactory =
        HttpsConnectionFactory { url ->
            val connection = url.openConnection()
            require(connection is HttpsURLConnection) {
                "Public propagation transport requires HttpsURLConnection"
            }
            connection
        },
) : PublicPropagationTransport {

    override fun get(
        request: PublicPropagationRequest,
    ): PublicPropagationResponse {
        val url = parseHttpsUrl(request.url)
        val connection = try {
            connectionFactory.open(url)
        } catch (e: Exception) {
            throw PublicPropagationTransportException(
                message = boundedMessage(
                    "Unable to open HTTPS connection: " +
                        e::class.java.simpleName +
                        ": " +
                        (e.message ?: "no message")
                ),
                retryable = true,
                cause = e,
            )
        }

        try {
            configure(connection, request)
            connection.connect()

            val statusCode = connection.responseCode
            val effectiveUrl = connection.url.toExternalForm()
            val contentType = connection.contentType

            validateContentEncoding(connection.contentEncoding)
            validateDeclaredLength(
                contentLength = connection.contentLengthLong,
                maximumBytes = request.maxResponseBytes,
            )

            val body = responseStream(connection, statusCode)?.use { stream ->
                readBoundedUtf8(
                    input = stream,
                    maximumBytes = request.maxResponseBytes,
                )
            }.orEmpty()

            return PublicPropagationResponse(
                requestedUrl = request.url,
                effectiveUrl = effectiveUrl,
                statusCode = statusCode,
                contentType = contentType,
                body = body,
            )
        } catch (e: PublicPropagationTransportException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw PublicPropagationTransportException(
                message = boundedMessage(
                    "HTTPS timeout: " + (e.message ?: "operation timed out")
                ),
                retryable = true,
                cause = e,
            )
        } catch (e: IOException) {
            throw PublicPropagationTransportException(
                message = boundedMessage(
                    "HTTPS I/O failure: " +
                        e::class.java.simpleName +
                        ": " +
                        (e.message ?: "no message")
                ),
                retryable = true,
                cause = e,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun configure(
        connection: HttpsURLConnection,
        request: PublicPropagationRequest,
    ) {
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = config.connectTimeoutMillis
        connection.readTimeout = config.readTimeoutMillis
        connection.useCaches = false
        connection.doInput = true
        connection.doOutput = false

        connection.setRequestProperty(
            "Accept",
            request.normalizedAcceptedContentTypes
                .toList()
                .sorted()
                .joinToString(", "),
        )
        connection.setRequestProperty("Accept-Charset", "UTF-8")
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("User-Agent", config.userAgent)
    }

    private fun parseHttpsUrl(value: String): URL {
        val uri = try {
            URI(value)
        } catch (e: Exception) {
            throw PublicPropagationTransportException(
                message = "Invalid public propagation HTTPS URL",
                retryable = false,
                cause = e,
            )
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw PublicPropagationTransportException(
                "Public propagation concrete transport requires HTTPS",
                retryable = false,
            )
        }
        if (uri.host.isNullOrBlank()) {
            throw PublicPropagationTransportException(
                "Public propagation HTTPS URL requires a host",
                retryable = false,
            )
        }
        if (uri.userInfo != null) {
            throw PublicPropagationTransportException(
                "Public propagation HTTPS URL must not contain user-info credentials",
                retryable = false,
            )
        }
        if (uri.fragment != null) {
            throw PublicPropagationTransportException(
                "Public propagation HTTPS URL must not contain a fragment",
                retryable = false,
            )
        }

        return try {
            uri.toURL()
        } catch (e: Exception) {
            throw PublicPropagationTransportException(
                message = "Invalid public propagation HTTPS URL",
                retryable = false,
                cause = e,
            )
        }
    }

    private fun validateContentEncoding(contentEncoding: String?) {
        val normalized = contentEncoding
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

        if (normalized != null && normalized != "identity") {
            throw PublicPropagationTransportException(
                "Unsupported public propagation Content-Encoding: $normalized",
                retryable = false,
            )
        }
    }

    private fun validateDeclaredLength(
        contentLength: Long,
        maximumBytes: Int,
    ) {
        if (contentLength > maximumBytes.toLong()) {
            throw PublicPropagationTransportException(
                "Public propagation response Content-Length exceeds " +
                    "$maximumBytes bytes",
                retryable = false,
            )
        }
    }

    private fun responseStream(
        connection: HttpsURLConnection,
        statusCode: Int,
    ): InputStream? =
        if (statusCode >= 400) {
            connection.errorStream
        } else {
            connection.inputStream
        }

    private fun readBoundedUtf8(
        input: InputStream,
        maximumBytes: Int,
    ): String {
        val initialSize = minOf(maximumBytes, 64 * 1024)
        val output = ByteArrayOutputStream(initialSize)
        val buffer = ByteArray(8 * 1024)
        var total = 0

        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maximumBytes) {
                throw PublicPropagationTransportException(
                    "Public propagation streamed response exceeds " +
                        "$maximumBytes bytes",
                    retryable = false,
                )
            }
            output.write(buffer, 0, read)
        }

        val bytes = output.toByteArray()
        return decodeUtf8Strict(bytes)
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String =
        try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw PublicPropagationTransportException(
                "Public propagation response is not valid UTF-8",
                retryable = false,
                cause = e,
            )
        }

    private fun boundedMessage(message: String): String =
        message.trim()
            .replace(Regex("""\s+"""), " ")
            .take(240)
            .ifBlank { "unspecified HTTPS transport failure" }
}
