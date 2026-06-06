package dev.jdtech.jellyfin.dragonhub.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DragonHubApiServiceTest {
    @Test
    fun `version parses sample response and uses configured base url`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val service =
            DragonHubApiService(
                okHttpClient =
                    clientReturning(
                        body =
                            """
                            {
                              "latest_version": "1.0.2.5",
                              "version_code": 32005,
                              "changelog": "- signed APK\r\n- minor UI fixes",
                              "notes": "",
                              "apk_url": "http://localhost:5001/api/download/latest",
                              "force_update": false
                            }
                            """
                                .trimIndent(),
                        capturedUrls = capturedUrls,
                    ),
                json = json,
                baseUrl = "http://localhost:5001",
            )

        val version = service.getVersion()

        assertEquals("http://localhost:5001/api/version", capturedUrls.single())
        assertEquals("1.0.2.5", version?.latestVersion)
        assertEquals(32005, version?.versionCode)
        assertEquals("- signed APK\r\n- minor UI fixes", version?.changelog)
        assertEquals("http://localhost:5001/api/download/latest", version?.apkUrl)
        assertEquals(false, version?.forceUpdate)
    }

    @Test
    fun `notifications sends username query and parses response`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val service =
            DragonHubApiService(
                okHttpClient =
                    clientReturning(
                        body =
                            """
                            {
                              "notifications": [
                                {
                                  "id": 1,
                                  "message": "app maintenance on SUNDAY",
                                  "created_at": "2026-06-06T15:11:42.038813"
                                }
                              ]
                            }
                            """
                                .trimIndent(),
                        capturedUrls = capturedUrls,
                    ),
                json = json,
                baseUrl = "http://localhost:5001",
            )

        val notifications = service.getNotifications("dragon user")

        assertEquals(
            "http://localhost:5001/api/notifications?username=dragon%20user",
            capturedUrls.single(),
        )
        assertEquals(1, notifications?.notifications?.single()?.id)
        assertEquals("app maintenance on SUNDAY", notifications?.notifications?.single()?.message)
    }

    @Test
    fun `version returns null for non 2xx response`() = runTest {
        val service =
            DragonHubApiService(
                okHttpClient = clientReturning(body = """{"error":"nope"}""", code = 500),
                json = json,
                baseUrl = "http://localhost:5001",
            )

        assertNull(service.getVersion())
    }

    @Test
    fun `version returns null for malformed json`() = runTest {
        val service =
            DragonHubApiService(
                okHttpClient = clientReturning(body = "not json"),
                json = json,
                baseUrl = "http://localhost:5001",
            )

        assertNull(service.getVersion())
    }

    @Test
    fun `blank config returns null without making request`() = runTest {
        var requestCount = 0
        val service =
            DragonHubApiService(
                okHttpClient =
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            requestCount++
                            chain.proceed(chain.request())
                        }
                        .build(),
                json = json,
                baseUrl = "",
            )

        assertNull(service.getVersion())
        assertEquals(0, requestCount)
    }

    @Test
    fun `malformed config returns null`() = runTest {
        val service =
            DragonHubApiService(
                okHttpClient = clientReturning(body = "{}"),
                json = json,
                baseUrl = "not a url",
            )

        assertNull(service.getVersion())
    }

    private fun clientReturning(
        body: String,
        code: Int = 200,
        capturedUrls: MutableList<String> = mutableListOf(),
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    capturedUrls += chain.request().url.toString()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code in 200..299) "OK" else "Error")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
            )
            .build()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
