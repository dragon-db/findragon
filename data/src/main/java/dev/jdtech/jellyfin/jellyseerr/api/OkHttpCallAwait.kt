package dev.jdtech.jellyfin.jellyseerr.api

import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Runs the call without blocking a thread and cancels the HTTP request when the coroutine is
 * cancelled (for example when the user types a new search or leaves a screen). A blocking
 * `execute()` would keep downloading until the server answered or timed out.
 */
internal suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            }
        )
    }
