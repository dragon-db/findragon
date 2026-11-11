package dev.jdtech.jellyfin.dragonhub.api

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class DragonHubAccountApiTest {
    @Test
    fun `account sends the Jellyfin token header and parses the response`() = runTest {
        val requests = mutableListOf<Request>()
        val service =
            service(
                clientReturning(
                    body =
                        """
                        {
                          "session": "valid",
                          "session_reason": null,
                          "managed": true,
                          "username": "dragon",
                          "email": "dragon@example.com",
                          "status": "expired",
                          "active": true,
                          "plan": {"id": 1, "name": "Monthly", "price": 5.0, "duration_days": 30},
                          "subscription_start": "2026-10-01T00:00:00+00:00",
                          "subscription_end": "2026-11-01T00:00:00+00:00",
                          "days_remaining": 0, "hours_remaining": 0,
                          "expired": true, "expiring_soon": false,
                          "disabled_reason": "expired",
                          "jellyfin": {"disabled": false, "library_access": "none"},
                          "sessions": {"max": 0, "active": null},
                          "message": "Renew to keep watching.",
                          "action_url": "https://example.invalid/renew",
                          "action_label": "Renew"
                        }
                        """
                            .trimIndent(),
                    requests = requests,
                )
            )

        val response = service.getAccount("token-1") as DragonHubAccountResponse.Success

        val request = requests.single()
        assertEquals("http://localhost:5001/p/accountdb/app/account", request.url.toString())
        assertEquals("token-1", request.header("X-Jellyfin-Token"))
        assertEquals("expired", response.account.disabledReason)
        assertEquals("Monthly", response.account.plan?.name)
        assertEquals("2026-11-01T00:00:00+00:00", response.account.subscriptionEnd)
        assertEquals("Renew", response.account.actionLabel)
    }

    @Test
    fun `account error keeps the hub error code`() = runTest {
        val service =
            service(clientReturning(body = """{"detail": "Unknown", "code": "token_unknown"}""", code = 401))

        assertEquals(DragonHubAccountResponse.Error(401, "token_unknown"), service.getAccount("t"))
    }

    @Test
    fun `account error without a json body has no code`() = runTest {
        val service = service(clientReturning(body = "Bad Gateway", code = 502))

        assertEquals(DragonHubAccountResponse.Error(502, null), service.getAccount("t"))
    }

    @Test
    fun `network failure is unreachable`() = runTest {
        val client =
            OkHttpClient.Builder()
                .addInterceptor(Interceptor { throw IOException("offline") })
                .build()

        assertEquals(DragonHubAccountResponse.Unreachable, service(client).getAccount("t"))
    }

    private fun service(client: OkHttpClient) =
        DragonHubApiService(okHttpClient = client, json = json, baseUrl = "http://localhost:5001")

    private fun clientReturning(
        body: String,
        code: Int = 200,
        requests: MutableList<Request> = mutableListOf(),
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    requests += chain.request()
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
