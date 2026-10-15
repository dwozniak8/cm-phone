package org.fossify.phone.cm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for the CM pairing state (access / refresh tokens, account
 * name). Backed by [EncryptedSharedPreferences] so the bearer token is not left
 * in plain text.
 */
class CmAuthStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_ACCESS_TOKEN, value) }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_REFRESH_TOKEN, value) }

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit { putString(KEY_USER_NAME, value) }

    val isPaired: Boolean
        get() = !accessToken.isNullOrEmpty()

    fun save(response: PairResponse) {
        val user = response.user
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, response.accessToken)
            putString(KEY_REFRESH_TOKEN, response.refreshToken)
            // user.name may be "" for a CM user with no name set
            putString(KEY_USER_NAME, user?.name?.takeIf { it.isNotBlank() } ?: user?.id)
        }
    }

    fun save(response: RefreshResponse) {
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, response.accessToken)
            // the server rotates the refresh token on every call - always persist it
            if (!response.refreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, response.refreshToken)
            }
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREFS_NAME = "cm_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_NAME = "user_name"
    }
}
