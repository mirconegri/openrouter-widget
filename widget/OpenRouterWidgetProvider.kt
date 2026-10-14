package com.nothingwidget.openrouter.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nothingwidget.openrouter.R
import com.nothingwidget.openrouter.work.RefreshWorker

class OpenRouterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetUpdater.update(context, mgr, it) }
        RefreshWorker.schedule(context)
        RefreshWorker.runNow(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MANUAL_REFRESH) {
            showSyncing(context)
            RefreshWorker.runNow(context)
        }
    }

    /** Feedback visivo immediato: mette "SYNC..." su tutti i widget */
    private fun showSyncing(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, OpenRouterWidgetProvider::class.java))
        ids.forEach { id ->
            val v = RemoteViews(context.packageName, R.layout.widget_openrouter)
            v.setTextViewText(R.id.widget_status, "SYNC...")
            mgr.partiallyUpdateAppWidget(id, v)
        }
    }

    companion object {
        const val ACTION_MANUAL_REFRESH = "com.nothingwidget.openrouter.MANUAL_REFRESH"
    }
}