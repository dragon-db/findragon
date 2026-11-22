package dev.jdtech.jellyfin.setup.domain

/**
 * Keeps the password of a successful sign-in, for Seerr and silent session recovery.
 *
 * [SetupRepository] calls it inside the sign-in, before it saves the new login: saving the login
 * switches the app from the setup flow to Home, which removes the login screen and cancels anything
 * that screen was still doing. Only the phone app binds one; on TV nothing is stored.
 */
interface LoginPasswordSaver {
    /** [password] is null for a Quick Connect sign-in: an older saved password is forgotten. */
    fun onSignedIn(serverId: String, username: String, password: String?)
}
