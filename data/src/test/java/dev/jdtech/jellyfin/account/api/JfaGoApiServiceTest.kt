package dev.jdtech.jellyfin.account.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class JfaGoApiServiceTest {
    @Test
    fun `login sends basic auth credentials`() = runTest {
        val capturedHeaders = mutableListOf<String?>()
        val service =
            JfaGoApiService(
                okHttpClient = clientReturning("""{"token":"jwt"}""", capturedHeaders),
                json = json,
                baseUrl = "https://account.example.invalid",
            )

        val token = service.login("dragon", "password")

        assertEquals("jwt", token.token)
        assertEquals("Basic ZHJhZ29uOnBhc3N3b3Jk", capturedHeaders.single())
    }

    @Test
    fun `details sends bearer token and parses email and expiry`() = runTest {
        val capturedHeaders = mutableListOf<String?>()
        val service =
            JfaGoApiService(
                okHttpClient =
                    clientReturning(
                        """{"username":"dragon","expiry":1748908800,"email":{"value":"dragon@example.com","enabled":true}}""",
                        capturedHeaders,
                    ),
                json = json,
                baseUrl = "https://account.example.invalid",
            )

        val details = service.getMyDetails("jwt")

        assertEquals("Bearer jwt", capturedHeaders.single())
        assertEquals("dragon", details.username)
        assertEquals("dragon@example.com", details.email?.value)
        assertEquals(1_748_908_800, details.expiry)
    }

    private fun clientReturning(
        body: String,
        capturedHeaders: MutableList<String?>,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    capturedHeaders += chain.request().header("Authorization")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
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
