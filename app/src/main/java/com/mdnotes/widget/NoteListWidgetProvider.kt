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
 * Widget that shows a single random note with full content (as much as fits).
 * Tap the refresh icon to load a different random note.
 * Tap the open icon / content area to open the note in the viewer.
 */
class NoteListWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SCROLL_REFRESH = "com.mdnotes.widget.ACTION_SCROLL_REFRESH"

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun updateAllListWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NoteListWidgetProvider::class.java)
            )
            for (id in ids) {
                // Show loading state first, then update async
                showLoading(context, manager, id)
                scope.launch { updateListWidgetSync(context, manager, id) }
            }
        }

        fun updateListWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            showLoading(context, appWidgetManager, widgetId)
            scope.launch { updateListWidgetSync(context, appWidgetManager, widgetId) }
        }

        /**
         * Show a loading placeholder while the note is being read.
         */
        private fun showLoading(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_list)
                applyTheme(context, views)
                views.setTextViewText(R.id.widget_scroll_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_scroll_content, "⏳")
                appWidgetManager.updateAppWidget(widgetId, views)
            } catch (_: Exception) {}
        }

        /**
         * Synchronous update — call from a coroutine or background thread.
         */
        fun updateListWidgetSync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val folderUri = PreferencesManager.getFolderUri(context)
            if (folderUri == null) {
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_list)
                    applyTheme(context, views)
                    views.setTextViewText(R.id.widget_scroll_title, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_scroll_content, context.getString(R.string.no_folder_selected))
                    appWidgetManager.updateAppWidget(widgetId, views)
                } catch (_: Exception) {}
                return
            }

            try {
                // Pick a random note
                val noteUri = MarkdownFileScanner.getRandomFile(context, folderUri)
                if (noteUri == null) {
                    val views = RemoteViews(context.packageName, R.layout.widget_list)
                    applyTheme(context, views)
                    views.setTextViewText(R.id.widget_scroll_title, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_scroll_content, context.getString(R.string.no_notes_found))
                    appWidgetManager.updateAppWidget(widgetId, views)
                    return
                }

                val note = MarkdownFileScanner.readNoteContent(context, noteUri) ?: return

                val views = RemoteViews(context.packageName, R.layout.widget_list)
                applyTheme(context, views)

                // Title
                views.setTextViewText(R.id.widget_scroll_title, note.title)

                // Content — show as much as possible
                views.setTextViewText(R.id.widget_scroll_content, note.content.take(4000))

                // Font size
                val fontSizeParam = PreferencesManager.getFontSize(context)
                val spValue = when (fontSizeParam) {
                    PreferencesManager.FONT_SIZE_SMALL  -> 10f
                    PreferencesManager.FONT_SIZE_LARGE  -> 16f
                    else                                -> 12f
                }
                views.setFloat(R.id.widget_scroll_content, "setTextSize", spValue)

                // Custom text colors
                val theme = PreferencesManager.getWidgetTheme(context)
                if (theme == PreferencesManager.THEME_CUSTOM) {
                    views.setTextColor(R.id.widget_scroll_title, PreferencesManager.getCustomWidgetTitleColor(context))
                    views.setTextColor(R.id.widget_scroll_content, PreferencesManager.getCustomWidgetTextColor(context))
                }

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

            } catch (e: Exception) {
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_list)
                    applyTheme(context, views)
                    views.setTextViewText(R.id.widget_scroll_title, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_scroll_content, context.getString(R.string.no_notes_found))
                    appWidgetManager.updateAppWidget(widgetId, views)
                } catch (_: Exception) {}
            }
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
        val pendingResult = goAsync()
        scope.launch {
            try {
                for (widgetId in appWidgetIds) {
                    updateListWidgetSync(context, appWidgetManager, widgetId)
                }
            } finally {
                pendingResult.finish()
            }
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
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            updateListWidgetSync(context, AppWidgetManager.getInstance(context), widgetId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
