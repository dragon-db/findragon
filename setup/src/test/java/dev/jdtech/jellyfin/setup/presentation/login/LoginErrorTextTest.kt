package dev.jdtech.jellyfin.setup.presentation.login

import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.setup.R
import java.io.IOException
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginErrorTextTest {
    @Test
    fun `401 is a wrong username or password`() {
        assertMessage(R.string.login_error_wrong_username_password, InvalidStatusException(401))
    }

    @Test
    fun `403 is a blocked account`() {
        assertMessage(R.string.login_error_account_blocked, InvalidStatusException(403))
    }

    @Test
    fun `5xx is a server error`() {
        assertMessage(R.string.login_error_server_error, InvalidStatusException(503))
    }

    @Test
    fun `other statuses stay unknown`() {
        assertMessage(CoreR.string.unknown_error, InvalidStatusException(404))
    }

    @Test
    fun `connection problems are an unreachable server`() {
        assertMessage(R.string.login_error_server_unreachable, TimeoutException("offline"))
        assertMessage(R.string.login_error_server_unreachable, SecureConnectionException("ssl"))
        assertMessage(R.string.login_error_server_unreachable, ApiClientException("io", IOException()))
    }

    @Test
    fun `anything else stays unknown`() {
        assertMessage(CoreR.string.unknown_error, IllegalStateException("boom"))
    }

    private fun assertMessage(expectedResId: Int, error: Throwable) {
        val text = loginErrorText(error)
        assertEquals(expectedResId, (text as UiText.StringResource).resId)
    }
}
