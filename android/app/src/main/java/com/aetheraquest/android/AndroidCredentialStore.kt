package com.aetheraquest.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Device-local encrypted credential storage.
 *
 * Android Keystore protects the master key. EncryptedSharedPreferences encrypts
 * both preference keys and values; neither username nor password is written to
 * ordinary SharedPreferences, logs, source, backups, or intent extras.
 */
class AndroidCredentialStore(context: Context) {
    private val appContext = context.applicationContext

    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): SavedCredentials? {
        val username = preferences.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val password = preferences.getString(KEY_PASSWORD, null).orEmpty()
        return credentialsToSave(username, password)
    }

    fun save(credentials: SavedCredentials) {
        preferences.edit()
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_USERNAME).remove(KEY_PASSWORD).apply()
    }

    private companion object {
        const val FILE_NAME = "aethera_login"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}
