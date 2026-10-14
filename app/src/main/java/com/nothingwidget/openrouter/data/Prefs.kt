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

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_API_KEY, v).apply()

    var threshold: Float
        get() = prefs.getFloat(KEY_THRESHOLD, 2.0f)
        set(v) = prefs.edit().putFloat(KEY_THRESHOLD, v).apply()

    /** -1f = nessun dato in cache */
    var cachedBalance: Float
        get() = prefs.getFloat(KEY_BALANCE, -1f)
        set(v) = prefs.edit().putFloat(KEY_BALANCE, v).apply()

    var cachedUsage: Float
        get() = prefs.getFloat(KEY_USAGE, -1f)
        set(v) = prefs.edit().putFloat(KEY_USAGE, v).apply()

    var lastUpdate: Long
        get() = prefs.getLong(KEY_LAST_UPDATE, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_UPDATE, v).apply()

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(v) = prefs.edit().putString(KEY_LAST_ERROR, v).apply()

    companion object {
        private const val KEY_API_KEY    = "api_key"
        private const val KEY_THRESHOLD  = "threshold"
        private const val KEY_BALANCE    = "balance"
        private const val KEY_USAGE      = "usage"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_LAST_ERROR  = "last_error"
    }
}