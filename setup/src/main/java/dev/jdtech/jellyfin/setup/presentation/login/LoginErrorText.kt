package dev.jdtech.jellyfin.setup.presentation.login

import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.setup.R
import java.io.IOException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException

/**
 * A sign-in error message per failure. Upstream shows "unknown error" for everything except a wrong
 * password. Jellyfin never says *why* it refuses an account (production servers only send "Error
 * processing request."), so 403 gets one message covering disabled and other blocked accounts.
 */
internal fun loginErrorText(error: Throwable): UiText {
    val resId =
        when {
            error is InvalidStatusException ->
                when (error.status) {
                    401 -> R.string.login_error_wrong_username_password
                    403 -> R.string.login_error_account_blocked
                    in 500..599 -> R.string.login_error_server_error
                    else -> CoreR.string.unknown_error
                }
            error is TimeoutException ||
                error is SecureConnectionException ||
                error.cause is IOException -> R.string.login_error_server_unreachable
            else -> CoreR.string.unknown_error
        }
    return UiText.StringResource(resId)
}
