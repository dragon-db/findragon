package dev.jdtech.jellyfin.account.api

class JfaGoApiException(val code: Int, message: String) : IllegalStateException(message)
