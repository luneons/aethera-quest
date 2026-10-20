package com.aetheraquest.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local encrypted credential storage.
 *
 * Android Keystore protects the master key. EncryptedSharedPreferences encrypts
 * both preference keys and values; neither username nor password is written to
 * ordinary SharedPreferences, logs, source, backups, or intent extras.
 *
 * Two independent records live here: the primary login (the account with a UI),
 * and the companion roster (accounts 2..4) that run headless.
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

    /**
     * Loads the companion roster. Returns an empty list when nothing was saved,
     * and never throws on corrupt data: a broken blob must not brick login.
     */
    fun loadCompanions(): List<AuxiliaryAccount> {
        val raw = preferences.getString(KEY_COMPANIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val username = obj.optString("username").trim()
                if (username.isEmpty()) return@mapNotNull null
                AuxiliaryAccount(
                    id = obj.optInt("id", index + 1),
                    username = username,
                    password = obj.optString("password"),
                    role = runCatching { CompanionRole.valueOf(obj.optString("role")) }
                        .getOrDefault(CompanionRole.TAUNTER_A),
                    enabled = obj.optBoolean("enabled", false),
                    consumableItemId = obj.optInt("consumableItemId", 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveCompanions(accounts: List<AuxiliaryAccount>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("id", account.id)
                    put("username", account.username)
                    put("password", account.password)
                    put("role", account.role.name)
                    put("enabled", account.enabled)
                    put("consumableItemId", account.consumableItemId)
                },
            )
        }
        preferences.edit().putString(KEY_COMPANIONS, array.toString()).apply()
    }

    private companion object {
        const val FILE_NAME = "aethera_login"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_COMPANIONS = "companions"
    }
}
