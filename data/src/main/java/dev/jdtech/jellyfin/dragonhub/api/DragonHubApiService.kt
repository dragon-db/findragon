package dev.jdtech.jellyfin.dragonhub.api

import dev.jdtech.jellyfin.data.BuildConfig
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubNotificationsResponseDto
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubVersionResponseDto
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
    private val baseUrl: String = BuildConfig.DRAGON_HUB_URL,
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
