package com.nothingwidget.openrouter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nothingwidget.openrouter.MainActivity
import com.nothingwidget.openrouter.R
import com.nothingwidget.openrouter.data.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetUpdater {

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, OpenRouterWidgetProvider::class.java))
        ids.forEach { update(context, mgr, it) }
    }

    fun update(context: Context, mgr: AppWidgetManager, id: Int) {
        val prefs = Prefs(context)
        val views = RemoteViews(context.packageName, R.layout.widget_openrouter)

        val balance   = prefs.cachedBalance
        val usage     = prefs.cachedUsage
        val threshold = prefs.threshold
        val lastUpd   = prefs.lastUpdate
        val error     = prefs.lastError

        val hasBalance = balance >= 0f
        val isLow      = hasBalance && balance < threshold

        // Saldo principale
        views.setTextViewText(
            R.id.widget_balance,
            if (hasBalance) "$" + String.format(Locale.US, "%.2f", balance) else "—"
        )
        views.setTextColor(
            R.id.widget_balance,
            if (isLow) 0xFFD71921.toInt() else 0xFFFFFFFF.toInt()
        )

        // Uso totale
        views.setTextViewText(
            R.id.widget_usage,
            if (usage >= 0f) "USO $" + String.format(Locale.US, "%.3f", usage) else "USO —"
        )

        // Timestamp / stato
        val status = when {
            error != null && hasBalance -> "OFFLINE · " + time(lastUpd)
            error != null               -> "ERRORE: " + error.take(28)
            lastUpd > 0                 -> "SYNC " + time(lastUpd)
            else                        -> "MAI SINCRONIZZATO"
        }
        views.setTextViewText(R.id.widget_status, status)

        // Dot di stato
        views.setImageViewResource(
            R.id.widget_dot,
            if (error != null || isLow) R.drawable.dot_red else R.drawable.dot_green
        )

        // Click sul widget → apre la config
        val openPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPi)

        // Click su refresh
        val refreshPi = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, OpenRouterWidgetProvider::class.java).apply {
                action = OpenRouterWidgetProvider.ACTION_MANUAL_REFRESH
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)

        mgr.updateAppWidget(id, views)
    }

    private fun time(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
}