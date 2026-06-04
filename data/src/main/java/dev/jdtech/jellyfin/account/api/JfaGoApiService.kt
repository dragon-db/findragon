package dev.jdtech.jellyfin.account.api

import dev.jdtech.jellyfin.account.api.dto.JfaGoMyDetailsDto
import dev.jdtech.jellyfin.account.api.dto.JfaGoTokenDto
import dev.jdtech.jellyfin.data.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class JfaGoApiService(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = BuildConfig.JFA_GO_BASE_URL,
) {
    suspend fun login(username: String, password: String): JfaGoTokenDto =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(url("my/token/login"))
                    .addHeader("Authorization", Credentials.basic(username, password, Charsets.UTF_8))
                    .get()
                    .build()

            okHttpClient.newCall(request).execute().use(::decodeResponse)
        }

    suspend fun getMyDetails(token: String): JfaGoMyDetailsDto =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(url("my/details"))
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

            okHttpClient.newCall(request).execute().use(::decodeResponse)
        }

    private inline fun <reified T> decodeResponse(response: Response): T {
        val body = response.body.string()
        if (!response.isSuccessful) {
            throw JfaGoApiException(response.code, body)
        }
        return json.decodeFromString(body)
    }

    private fun url(path: String): HttpUrl {
        check(baseUrl.isNotBlank()) { "JFA-GO service is not configured." }
        return "${baseUrl.trimEnd('/')}/$path".toHttpUrl()
    }
}
