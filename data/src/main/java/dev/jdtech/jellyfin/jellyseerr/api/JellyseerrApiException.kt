package dev.jdtech.jellyfin.jellyseerr.api

import java.io.IOException

class JellyseerrApiException(val code: Int, responseBody: String) :
    IOException("Jellyseerr request failed with $code: ${responseBody.ifBlank { "No response body" }}")
