package dev.jdtech.jellyfin.dragonhub.api

import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubAccountDto
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubErrorDto
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubNotificationsResponseDto
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubVersionResponseDto
import dev.jdtech.jellyfin.findragon.FindragonConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class DragonHubApiService(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = FindragonConfig.dragonHubUrl,
) {
    suspend fun getVersion(): DragonHubVersionResponseDto? {
        return get("api/version")
    }

    suspend fun getNotifications(username: String): DragonHubNotificationsResponseDto? {
        if (username.isBlank()) return null
        return get("api/notifications") {
            addQueryParameter("username", username)
        }
    }

    /**
     * Account status of whoever owns [jellyfinToken]. The hub still answers after Jellyfin revoked
     * the token, so this also explains a dead login.
     */
    suspend fun getAccount(jellyfinToken: String): DragonHubAccountResponse =
        withContext(Dispatchers.IO) {
            runCatching {
                    val request =
                        Request.Builder()
                            .url(url("p/accountdb/app/account") {})
                            .header(JELLYFIN_TOKEN_HEADER, jellyfinToken)
                            .get()
                            .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        val body = response.body.string()
                        if (response.isSuccessful) {
                            DragonHubAccountResponse.Success(
                                json.decodeFromString<DragonHubAccountDto>(body)
                            )
                        } else {
                            val error =
                                runCatching { json.decodeFromString<DragonHubErrorDto>(body) }
                                    .getOrNull()
                            DragonHubAccountResponse.Error(response.code, error?.code)
                        }
                    }
                }
                .getOrElse { DragonHubAccountResponse.Unreachable }
        }

    private suspend inline fun <reified T> get(
        path: String,
        noinline configureUrl: HttpUrl.Builder.() -> Unit = {},
    ): T? =
        withContext<T?>(Dispatchers.IO) {
            runCatching {
                    val request =
                        Request.Builder()
                            .url(url(path, configureUrl))
                            .get()
                            .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        decodeResponse<T>(response)
                    }
                }
                .getOrNull()
        }

    private inline fun <reified T> decodeResponse(response: Response): T? {
        val body = response.body.string()
        if (!response.isSuccessful) return null
        return json.decodeFromString(body)
    }

    private fun url(
        path: String,
        configureUrl: HttpUrl.Builder.() -> Unit,
    ): HttpUrl {
        check(baseUrl.isNotBlank()) { "Dragon Hub service is not configured." }
        return baseUrl
            .trimEnd('/')
            .toHttpUrl()
            .newBuilder()
            .addPathSegments(path)
            .apply(configureUrl)
            .build()
    }
}

private const val JELLYFIN_TOKEN_HEADER = "X-Jellyfin-Token"

sealed interface DragonHubAccountResponse {
    data class Success(val account: DragonHubAccountDto) : DragonHubAccountResponse

    /** [code] is the hub's error code (e.g. `token_unknown`), null if the body had none. */
    data class Error(val httpStatus: Int, val code: String?) : DragonHubAccountResponse

    /** Network failure, unreadable response, or the hub is not configured. */
    data object Unreachable : DragonHubAccountResponse
}
