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

        val balance = prefs.cachedBalance
        val label = prefs.cachedLabel ?: "OPENROUTER"
        val threshold = prefs.threshold
        val lastUpd = prefs.lastUpdate
        val error = prefs.lastError

        val hasBalance = balance >= 0f
        val isLow = hasBalance && balance < threshold

        views.setTextViewText(
            R.id.widget_balance,
            if (hasBalance) "$" + String.format(Locale.US, "%.2f", balance) else "—"
        )
        views.setTextColor(
            R.id.widget_balance,
            if (isLow) 0xFFD71921.toInt() else 0xFFFFFFFF.toInt()
        )
        views.setTextViewText(R.id.widget_label, label)

        val status = when {
            error != null && hasBalance -> "OFFLINE · " + time(lastUpd)
            error != null -> "ERRORE: " + error.take(28)
            lastUpd > 0 -> "SYNC " + time(lastUpd)
            else -> "NON CONFIGURATO"
        }
        views.setTextViewText(R.id.widget_status, status)

        views.setImageViewResource(
            R.id.widget_dot,
            if (error != null || isLow) R.drawable.dot_red else R.drawable.dot_green
        )

        val openPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPi)

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