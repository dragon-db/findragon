package dev.jdtech.jellyfin.setup.data

import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.setup.domain.LoginPasswordSaver
import javax.inject.Inject
import timber.log.Timber

/** Stores the sign-in password in the encrypted credentials store (phone only). */
class SecureLoginPasswordSaver
@Inject
constructor(private val secureCredentialsStore: SecureCredentialsStore) : LoginPasswordSaver {
    override fun onSignedIn(serverId: String, username: String, password: String?) {
        // A failure here must not fail the sign-in itself: only Seerr and session recovery need it.
        try {
            if (password != null) {
                secureCredentialsStore.saveUserPassword(serverId, username, password)
            } else {
                secureCredentialsStore.clearUserPassword(serverId, username)
            }
            // A Seerr session from an older sign-in may belong to another password.
            secureCredentialsStore.clearJellyseerrSession(serverId, username)
        } catch (e: Exception) {
            Timber.e(e, "Could not store the sign-in password")
        }
    }
}
