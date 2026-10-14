package com.nothingwidget.openrouter.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nothingwidget.openrouter.data.OpenRouterApi
import com.nothingwidget.openrouter.data.Prefs
import com.nothingwidget.openrouter.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        try {
            when (prefs.displayMode) {
                Prefs.MODE_KEY -> refreshKey(prefs)
                else -> refreshAccount(prefs)
            }
            Result.success()
        } catch (t: Throwable) {
            prefs.lastError = t.message ?: "Errore di rete"
            WidgetUpdater.updateAll(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    private suspend fun refreshAccount(prefs: Prefs) {
        if (prefs.userApiKey.isBlank()) {
            prefs.lastError = "Non configurato"
            WidgetUpdater.updateAll(applicationContext)
            return
        }
        val r = OpenRouterApi.fetchCredits(prefs.userApiKey)
        prefs.cachedBalance = r.balance
        prefs.cachedUsage = r.usage
        prefs.cachedLabel = "ACCOUNT"
        prefs.lastUpdate = System.currentTimeMillis()
        prefs.lastError = null
        WidgetUpdater.updateAll(applicationContext)
    }

    private suspend fun refreshKey(prefs: Prefs) {
        if (prefs.userApiKey.isBlank()) {
            prefs.lastError = "Non configurato"
            WidgetUpdater.updateAll(applicationContext)
            return
        }
        val k = OpenRouterApi.fetchKeyInfo(prefs.userApiKey)
        // In modalita KEY mostriamo l'usage cumulato della chiave
        prefs.cachedBalance = k.usage
        prefs.cachedUsage = k.usage
        prefs.cachedLabel = k.label.uppercase()
        prefs.lastUpdate = System.currentTimeMillis()
        prefs.lastError = null
        WidgetUpdater.updateAll(applicationContext)
    }

    companion object {
        private const val UNIQUE_PERIODIC = "openrouter_refresh_periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(45, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}