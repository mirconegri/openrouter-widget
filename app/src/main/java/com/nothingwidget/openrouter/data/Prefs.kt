package com.nothingwidget.openrouter.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Prefs(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "openrouter_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var userApiKey: String
        get() = prefs.getString(KEY_USER_API_KEY, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_USER_API_KEY, v).apply()

    var displayMode: String
        get() = prefs.getString(KEY_MODE, MODE_ACCOUNT) ?: MODE_ACCOUNT
        set(v) = prefs.edit().putString(KEY_MODE, v).apply()

    var threshold: Float
        get() = prefs.getFloat(KEY_THRESHOLD, 2.0f)
        set(v) = prefs.edit().putFloat(KEY_THRESHOLD, v).apply()

    var cachedBalance: Float
        get() = prefs.getFloat(KEY_BALANCE, -1f)
        set(v) = prefs.edit().putFloat(KEY_BALANCE, v).apply()

    var cachedUsage: Float
        get() = prefs.getFloat(KEY_USAGE, -1f)
        set(v) = prefs.edit().putFloat(KEY_USAGE, v).apply()

    var cachedLabel: String?
        get() = prefs.getString(KEY_LABEL, null)
        set(v) = prefs.edit().putString(KEY_LABEL, v).apply()

    var lastUpdate: Long
        get() = prefs.getLong(KEY_LAST_UPDATE, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_UPDATE, v).apply()

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(v) = prefs.edit().putString(KEY_LAST_ERROR, v).apply()

    var oauthCodeVerifier: String?
        get() = prefs.getString(KEY_CODE_VERIFIER, null)
        set(v) = prefs.edit().putString(KEY_CODE_VERIFIER, v).apply()

    fun logout() { prefs.edit().clear().apply() }

    companion object {
        const val MODE_ACCOUNT = "ACCOUNT"
        const val MODE_KEY = "KEY"

        private const val KEY_USER_API_KEY = "user_api_key"
        private const val KEY_MODE = "mode"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_BALANCE = "balance"
        private const val KEY_USAGE = "usage"
        private const val KEY_LABEL = "label"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_CODE_VERIFIER = "code_verifier"
    }
}