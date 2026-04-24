package com.mdnotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget that shows a single random note with full scrollable content.
 * Tap the refresh icon to load a different random note.
 * Tap the open icon to open the note in the viewer.
 */
class NoteListWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SCROLL_REFRESH = "com.mdnotes.widget.ACTION_SCROLL_REFRESH"
        const val ACTION_SCROLL_OPEN   = "com.mdnotes.widget.ACTION_SCROLL_OPEN"
        private const val PREF_SCROLL_URI = "scroll_widget_uri_"

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun updateAllListWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NoteListWidgetProvider::class.java)
            )
            for (id in ids) {
                scope.launch { updateListWidgetAsync(context, manager, id) }
            }
        }

        fun updateListWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            scope.launch { updateListWidgetAsync(context, appWidgetManager, widgetId) }
        }

        private fun updateListWidgetAsync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val folderUri = PreferencesManager.getFolderUri(context) ?: return

            // Pick a random note
            val noteUri = MarkdownFileScanner.getRandomFile(context, folderUri) ?: return
            // Save it so open button knows which note
            context.getSharedPreferences("md_notes_prefs", Context.MODE_PRIVATE)
                .edit().putString(PREF_SCROLL_URI + widgetId, noteUri.toString()).apply()

            val note = MarkdownFileScanner.readNoteContent(context, noteUri) ?: return

            val views = RemoteViews(context.packageName, R.layout.widget_list)

            // Theme
            applyTheme(context, views)

            // Title
            views.setTextViewText(R.id.widget_scroll_title, note.title)

            // Content (plain stripped text)
            val body = note.content.take(3000)
            views.setTextViewText(R.id.widget_scroll_content, body)

            // Refresh button → load different random note
            val refreshIntent = Intent(context, NoteListWidgetProvider::class.java).apply {
                action = ACTION_SCROLL_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val refreshPi = PendingIntent.getBroadcast(
                context, widgetId + 50000, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_scroll_random_btn, refreshPi)

            // Open button → open note in viewer
            val openIntent = Intent(context, NoteViewerActivity::class.java).apply {
                putExtra(NoteViewerActivity.EXTRA_NOTE_URI, noteUri.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val openPi = PendingIntent.getActivity(
                context, widgetId + 60000, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_scroll_open_btn, openPi)

            // Tap on content area → open note
            views.setOnClickPendingIntent(R.id.widget_scroll_content, openPi)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun applyTheme(context: Context, views: RemoteViews) {
            val theme = PreferencesManager.getWidgetTheme(context)
            when (theme) {
                PreferencesManager.THEME_CUSTOM -> {
                    views.setInt(R.id.widget_scroll_root, "setBackgroundColor",
                        PreferencesManager.getCustomWidgetBgColor(context))
                }
                else -> {
                    val bgRes = when (theme) {
                        PreferencesManager.THEME_DARK        -> R.drawable.widget_bg_dark
                        PreferencesManager.THEME_TRANSPARENT -> R.drawable.widget_bg_transparent
                        else                                 -> R.drawable.widget_bg_default
                    }
                    views.setInt(R.id.widget_scroll_root, "setBackgroundResource", bgRes)
                }
            }
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
        when (intent.action) {
            ACTION_SCROLL_REFRESH -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    updateListWidget(context, AppWidgetManager.getInstance(context), widgetId)
                }
            }
        }
    }
}
