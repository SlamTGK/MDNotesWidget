package com.mdnotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.*

/**
 * Widget provider for the Stack/List widget that shows multiple notes
 * with swipe navigation.
 */
class NoteListWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_LIST_REFRESH = "com.mdnotes.widget.ACTION_LIST_REFRESH"

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun updateAllListWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NoteListWidgetProvider::class.java)
            )
            for (id in ids) {
                updateListWidget(context, manager, id)
            }
        }

        fun updateListWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_list)

            // Apply theme
            val theme = PreferencesManager.getWidgetTheme(context)
            when (theme) {
                PreferencesManager.THEME_CUSTOM -> {
                    val bgColor = PreferencesManager.getCustomWidgetBgColor(context)
                    views.setInt(R.id.widget_list_root, "setBackgroundColor", bgColor)
                }
                else -> {
                    val bgRes = when (theme) {
                        PreferencesManager.THEME_DARK -> R.drawable.widget_bg_dark
                        PreferencesManager.THEME_TRANSPARENT -> R.drawable.widget_bg_transparent
                        else -> R.drawable.widget_bg_default
                    }
                    views.setInt(R.id.widget_list_root, "setBackgroundResource", bgRes)
                }
            }

            // Set up the RemoteViews adapter for StackView
            val serviceIntent = Intent(context, NoteListWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_stack, serviceIntent)

            // Refresh button
            val refreshIntent = Intent(context, NoteListWidgetProvider::class.java).apply {
                action = ACTION_LIST_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val refreshPi = PendingIntent.getBroadcast(
                context, widgetId + 50000, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_list_refresh, refreshPi)

            // Click template for opening notes
            val openTemplate = Intent(context, NoteWidgetProvider::class.java).apply {
                action = NoteWidgetProvider.ACTION_OPEN_NOTE
            }
            val openPi = PendingIntent.getBroadcast(
                context, widgetId + 60000, openTemplate,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_stack, openPi)

            appWidgetManager.updateAppWidget(widgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_stack)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateListWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_LIST_REFRESH) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val manager = AppWidgetManager.getInstance(context)
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_stack)
            }
        }
    }
}
