package com.nothingwidget.openrouter.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

object OAuthHelper {

    const val CALLBACK_SCHEME = "openrouterwidget"
    const val CALLBACK_HOST = "oauth"
    const val REDIRECT_URI = "openrouterwidget://oauth/callback"
    const val AUTH_URL = "https://openrouter.ai/auth"
    const val TOKEN_URL = "https://openrouter.ai/api/v1/auth/keys"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun buildAuthUrl(challenge: String): String {
        val cb = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return "$AUTH_URL?callback_url=$cb&code_challenge=$challenge&code_challenge_method=S256"
    }

    data class TokenResult(val apiKey: String, val userId: String?)

    suspend fun exchangeCode(code: String, verifier: String): TokenResult = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("code", code)
            put("code_verifier", verifier)
            put("code_challenge_method", "S256")
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(TOKEN_URL).post(body).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("OAuth ${resp.code}: $text")
            val obj = JSONObject(text)
            val key = obj.optString("key")
            if (key.isBlank()) throw RuntimeException("Risposta OAuth senza key")
            TokenResult(apiKey = key, userId = obj.optString("user_id").takeIf { it.isNotBlank() })
        }
    }
}