package dev.jdtech.jellyfin.jellyseerr.api

import java.io.IOException

class JellyseerrApiException(val code: Int, val fullResponseBody: String) :
    IOException("Jellyseerr request failed with $code: ${responsePreview(fullResponseBody)}") {
    private companion object {
        const val MAX_RESPONSE_PREVIEW_LENGTH = 200

        fun responsePreview(responseBody: String): String {
            val trimmedResponseBody = responseBody.trim()
            if (trimmedResponseBody.isBlank()) {
                return "No response body"
            }

            return if (trimmedResponseBody.length > MAX_RESPONSE_PREVIEW_LENGTH) {
                trimmedResponseBody.take(MAX_RESPONSE_PREVIEW_LENGTH) + "...(truncated)"
            } else {
                trimmedResponseBody
            }
        }
    }
}
