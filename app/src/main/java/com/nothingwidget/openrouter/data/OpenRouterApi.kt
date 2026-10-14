package com.nothingwidget.openrouter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CreditsResult(val balance: Float, val usage: Float, val total: Float)

object OpenRouterApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCredits(apiKey: String): CreditsResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API key mancante" }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/credits")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val msg = when (response.code) {
                    401 -> "API key non valida"
                    403 -> "Accesso negato"
                    else -> "HTTP ${response.code}"
                }
                throw RuntimeException(msg)
            }
            val body = response.body?.string().orEmpty()
            val data = JSONObject(body).getJSONObject("data")
            val total = data.getDouble("total_credits").toFloat()
            val usage = data.getDouble("total_usage").toFloat()
            CreditsResult(balance = total - usage, usage = usage, total = total)
        }
    }
}