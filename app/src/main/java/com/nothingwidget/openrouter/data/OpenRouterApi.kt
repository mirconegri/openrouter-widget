package com.nothingwidget.openrouter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CreditsResult(val balance: Float, val usage: Float, val total: Float)

data class Workspace(
    val id: String,
    val name: String,
    val slug: String?,
    val defaultBudget: Float?
)

data class ApiKeyInfo(
    val hash: String,
    val name: String,
    val usage: Float,
    val limit: Float?,
    val workspaceId: String?
)

object OpenRouterApi {

    private const val BASE = "https://openrouter.ai/api/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCredits(apiKey: String): CreditsResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API key mancante" }
        val body = get("$BASE/credits", apiKey)
        val data = JSONObject(body).getJSONObject("data")
        val total = data.getDouble("total_credits").toFloat()
        val usage = data.getDouble("total_usage").toFloat()
        CreditsResult(balance = total - usage, usage = usage, total = total)
    }

    suspend fun fetchWorkspaces(managementKey: String): List<Workspace> = withContext(Dispatchers.IO) {
        require(managementKey.isNotBlank()) { "Management key mancante" }
        val body = get("$BASE/workspaces", managementKey)
        val root = JSONObject(body)
        val arr: JSONArray = root.optJSONArray("data") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Workspace(
                id = o.optString("id"),
                name = o.optString("name").ifBlank { o.optString("slug") },
                slug = o.optString("slug").takeIf { it.isNotBlank() },
                defaultBudget = if (o.has("default_budget") && !o.isNull("default_budget"))
                    o.optDouble("default_budget").toFloat() else null
            )
        }
    }

    suspend fun fetchWorkspaceKeys(managementKey: String, workspaceId: String): List<ApiKeyInfo> =
        withContext(Dispatchers.IO) {
            require(managementKey.isNotBlank()) { "Management key mancante" }
            val body = get("$BASE/keys?workspace_id=$workspaceId", managementKey)
            parseKeys(body)
        }

    private fun parseKeys(body: String): List<ApiKeyInfo> {
        val root = JSONObject(body)
        val arr: JSONArray = root.optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ApiKeyInfo(
                hash = o.optString("hash"),
                name = o.optString("name").ifBlank { o.optString("label").ifBlank { o.optString("hash").take(12) } },
                usage = o.optDouble("usage", 0.0).toFloat(),
                limit = if (o.has("limit") && !o.isNull("limit"))
                    o.optDouble("limit").toFloat() else null,
                workspaceId = o.optString("workspace_id").takeIf { it.isNotBlank() }
            )
        }
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
                    401 -> "Chiave non valida"
                    403 -> "Piano insufficiente / accesso negato"
                    404 -> "Risorsa non trovata"
                    else -> "HTTP ${resp.code}"
                }
                throw RuntimeException(msg)
            }
            return text
        }
    }
}