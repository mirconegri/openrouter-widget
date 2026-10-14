package com.nothingwidget.openrouter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CreditsResult(val balance: Float, val usage: Float, val total: Float)

data class KeyInfo(
    val label: String,
    val usage: Float,
    val limit: Float?,
    val limitRemaining: Float?
)

object OpenRouterApi {

    private const val BASE = "https://openrouter.ai/api/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    /** GET /credits — returns the account-wide credit balance. */
    suspend fun fetchCredits(apiKey: String): CreditsResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API key missing" }
        val body = get("$BASE/credits", apiKey)
        val data = JSONObject(body).getJSONObject("data")
        val total = data.getDouble("total_credits").toFloat()
        val usage = data.getDouble("total_usage").toFloat()
        CreditsResult(balance = total - usage, usage = usage, total = total)
    }

    /**
     * GET /key — returns metadata for the API key used to authenticate the request:
     * its label, cumulative usage, spending limit and remaining budget.
     */
    suspend fun fetchKeyInfo(apiKey: String): KeyInfo = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API key missing" }
        val body = get("$BASE/key", apiKey)
        val data = JSONObject(body).getJSONObject("data")
        val label = data.optString("label").takeIf { it.isNotBlank() } ?: "API KEY"
        val usage = data.optDouble("usage", 0.0).toFloat()
        val limit = if (data.has("limit") && !data.isNull("limit"))
            data.optDouble("limit").toFloat() else null
        val remaining = if (data.has("limit_remaining") && !data.isNull("limit_remaining"))
            data.optDouble("limit_remaining").toFloat() else null
        KeyInfo(label = label, usage = usage, limit = limit, limitRemaining = remaining)
    }

    private fun get(url: String, bearer: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = when (resp.code) {
                    401 -> "Invalid API key"
                    403 -> "Access denied"
                    404 -> "Resource not found"
                    else -> "HTTP ${resp.code}"
                }
                throw RuntimeException(msg)
            }
            return text
        }
    }
}