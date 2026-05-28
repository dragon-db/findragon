package dev.jdtech.jellyfin.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

class SecureCredentialsStore(context: Context) {
    private val preferences: SharedPreferences? by lazy {
        try {
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            @Suppress("DEPRECATION")
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize secure credentials store")
            null
        }
    }

    fun saveUserPassword(serverId: String, username: String, password: String) {
        preferences?.edit()?.putString(passwordKey(serverId, username), password)?.apply()
    }

    fun getUserPassword(serverId: String, username: String): String? {
        return preferences?.getString(passwordKey(serverId, username), null)
    }

    fun clearUserPassword(serverId: String, username: String) {
        preferences?.edit()?.remove(passwordKey(serverId, username))?.apply()
    }

    fun saveJellyseerrSession(serverId: String, username: String, sessionCookie: String) {
        preferences
            ?.edit()
            ?.remove(legacySessionKey(serverId))
            ?.putString(sessionKey(serverId, username), sessionCookie)
            ?.apply()
    }

    fun getJellyseerrSession(serverId: String, username: String): String? {
        val prefs = preferences ?: return null
        val legacyKey = legacySessionKey(serverId)
        if (prefs.contains(legacyKey)) {
            prefs.edit().remove(legacyKey).apply()
        }

        return prefs.getString(sessionKey(serverId, username), null)
    }

    fun clearJellyseerrSession(serverId: String, username: String) {
        preferences
            ?.edit()
            ?.remove(sessionKey(serverId, username))
            ?.remove(legacySessionKey(serverId))
            ?.apply()
    }

    fun clearSecretsForServer(serverId: String) {
        val prefs = preferences ?: return
        val keysToRemove =
            prefs.all.keys.filter { key ->
                key.startsWith(passwordKeyPrefix(serverId)) ||
                    key.startsWith(sessionKeyPrefix(serverId)) ||
                    key == legacySessionKey(serverId)
            }

        if (keysToRemove.isEmpty()) {
            return
        }

        prefs.edit().apply {
            keysToRemove.forEach(::remove)
        }.apply()
    }

    private fun passwordKey(serverId: String, username: String): String {
        return "password::$serverId::$username"
    }

    private fun passwordKeyPrefix(serverId: String): String {
        return "password::$serverId::"
    }

    private fun sessionKey(serverId: String, username: String): String {
        return "jellyseerr_session::$serverId::$username"
    }

    private fun sessionKeyPrefix(serverId: String): String {
        return "jellyseerr_session::$serverId::"
    }

    private fun legacySessionKey(serverId: String): String {
        return "jellyseerr_session::$serverId"
    }

    companion object {
        const val FILE_NAME = "findroid_secure_credentials"
    }
}
