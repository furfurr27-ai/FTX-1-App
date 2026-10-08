package dev.n0png.fieldops.core.propagation

import java.nio.charset.StandardCharsets

data class PublicPropagationRequest(
    val url: String,
    val maxResponseBytes: Int,
    val acceptedContentTypes: Set<String>,
) {
    init {
        require(url.startsWith("https://")) {
            "Public propagation request must use HTTPS"
        }
        require(!url.contains('#')) {
            "Public propagation request URL must not contain a fragment"
        }
        require(!url.contains('
') && !url.contains('')) {
            "Public propagation request URL must not contain control-line separators"
        }
        require(maxResponseBytes > 0) {
            "Public propagation maximum response bytes must be positive"
        }
        require(acceptedContentTypes.isNotEmpty()) {
            "Public propagation request requires accepted content types"
        }
        require(acceptedContentTypes.all { it.isNotBlank() }) {
            "Public propagation accepted content types must not be blank"
        }
    }

    val normalizedAcceptedContentTypes: Set<String>
        get() = acceptedContentTypes.map { it.trim().lowercase() }.toSet()
}

data class PublicPropagationResponse(
    val requestedUrl: String,
    val effectiveUrl: String,
    val statusCode: Int,
    val contentType: String?,
    val body: String,
) {
    init {
        require(requestedUrl.isNotBlank()) {
            "Public propagation response requested URL must not be blank"
        }
        require(effectiveUrl.isNotBlank()) {
            "Public propagation response effective URL must not be blank"
        }
        require(statusCode in 100..599) {
            "Public propagation HTTP status must be in 100..599"
        }
    }

    val bodyBytes: Int
        get() = body.toByteArray(StandardCharsets.UTF_8).size

    val normalizedContentType: String?
        get() = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
}

fun interface PublicPropagationTransport {
    fun get(request: PublicPropagationRequest): PublicPropagationResponse
}

internal sealed interface ValidatedPublicBody {
    data class Success(
        val body: String,
        val effectiveUrl: String,
    ) : ValidatedPublicBody

    data class Failure(
        val message: String,
        val retryable: Boolean,
    ) : ValidatedPublicBody
}

internal object PublicPropagationResponseValidator {
    fun validate(
        request: PublicPropagationRequest,
        response: PublicPropagationResponse,
    ): ValidatedPublicBody {
        if (response.requestedUrl != request.url) {
            return failure(
                "Transport response requested URL does not match the request",
                retryable = false,
            )
        }
        if (response.effectiveUrl != request.url) {
            return failure(
                "Unexpected redirect or effective URL: " + response.effectiveUrl,
                retryable = false,
            )
        }
        if (response.statusCode != 200) {
            return failure(
                "HTTP " + response.statusCode + " from " + request.url,
                retryable = response.statusCode == 408 ||
                    response.statusCode == 425 ||
                    response.statusCode == 429 ||
                    response.statusCode in 500..599,
            )
        }
        if (response.body.isBlank()) {
            return failure(
                "Successful public propagation response body is blank",
                retryable = true,
            )
        }
        if (response.bodyBytes > request.maxResponseBytes) {
            return failure(
                "Public propagation response exceeds " +
                    request.maxResponseBytes +
                    " bytes",
                retryable = false,
            )
        }
        response.normalizedContentType?.let { contentType ->
            if (contentType !in request.normalizedAcceptedContentTypes) {
                return failure(
                    "Unexpected content type " + contentType,
                    retryable = false,
                )
            }
        }
        return ValidatedPublicBody.Success(
            body = response.body,
            effectiveUrl = response.effectiveUrl,
        )
    }

    private fun failure(
        message: String,
        retryable: Boolean,
    ) = ValidatedPublicBody.Failure(
        message = message,
        retryable = retryable,
    )
}
