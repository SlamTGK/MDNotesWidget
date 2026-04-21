package com.mdnotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.work.*
import java.util.concurrent.TimeUnit

class NoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH   = "com.mdnotes.widget.ACTION_REFRESH"
        const val ACTION_OPEN_NOTE = "com.mdnotes.widget.ACTION_OPEN_NOTE"
        const val EXTRA_WIDGET_ID  = "extra_widget_id"
        const val EXTRA_NOTE_URI   = "extra_note_uri"
        const val WORK_NAME        = "md_notes_periodic_update"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val folderUri = PreferencesManager.getFolderUri(context)

            if (folderUri == null) {
                try {
                    appWidgetManager.updateAppWidget(widgetId, buildUnconfiguredViews(context, widgetId))
                } catch (_: Exception) {}
                return
            }

            // File I/O on background thread — appWidgetManager is safe to call from any thread
            Thread {
                try {
                    val fileUri = MarkdownFileScanner.getRandomFile(context, folderUri)

                    if (fileUri == null) {
                        appWidgetManager.updateAppWidget(widgetId, buildEmptyViews(context, widgetId))
                        return@Thread
                    }

                    PreferencesManager.setCurrentNoteUri(context, widgetId, fileUri.toString())

                    val note = MarkdownFileScanner.readNoteContent(context, fileUri)

                    if (note == null) {
                        appWidgetManager.updateAppWidget(widgetId, buildEmptyViews(context, widgetId))
                        return@Thread
                    }

                    appWidgetManager.updateAppWidget(widgetId, buildNoteViews(context, widgetId, note))
                } catch (e: Exception) {
                    // If building views fails for any reason, fall back to empty state
                    try {
                        appWidgetManager.updateAppWidget(widgetId, buildEmptyViews(context, widgetId))
                    } catch (_: Exception) {}
                }
            }.start()
        }

        // ── RemoteViews builders ──────────────────────────────────────────────

        private fun applyThemeToViews(context: Context, views: RemoteViews, viewId: Int) {
            val theme = PreferencesManager.getWidgetTheme(context)
            val bgRes = when (theme) {
                PreferencesManager.THEME_DARK -> R.drawable.widget_bg_dark
                PreferencesManager.THEME_TRANSPARENT -> R.drawable.widget_bg_transparent
                else -> R.drawable.widget_bg_default
            }
            // Use setInt to apply the background resource directly to the root layout tag
            views.setInt(viewId, "setBackgroundResource", bgRes)
        }

        private fun buildNoteViews(
            context: Context,
            widgetId: Int,
            note: MarkdownFileScanner.NoteContent
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_note)
            applyThemeToViews(context, views, R.id.widget_root)

            val truncatedContent = if (note.content.length > 2000) {
                note.content.take(2000) + "..."
            } else {
                note.content
            }

            views.setTextViewText(R.id.widget_title, note.title)
            views.setTextViewText(R.id.widget_content, truncatedContent)

            // Format date and folder name
            val dateFormat = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
            val dateString = if (note.lastModified > 0) dateFormat.format(java.util.Date(note.lastModified)) else ""
            val metaString = buildString {
                append(dateString)
                if (note.folderName.isNotEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append("Папка: ${note.folderName}")
                }
            }
            if (metaString.isNotEmpty()) {
                views.setTextViewText(R.id.widget_meta, metaString)
                views.setViewVisibility(R.id.widget_meta, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_meta, android.view.View.GONE)
            }

            // Apply font size preference
            val fontSizeParam = PreferencesManager.getFontSize(context)
            val spValue = when (fontSizeParam) {
                PreferencesManager.FONT_SIZE_SMALL -> 10f
                PreferencesManager.FONT_SIZE_LARGE -> 16f
                else -> 12f // FONT_SIZE_MEDIUM
            }
            views.setFloat(R.id.widget_content, "setTextSize", spValue)

            views.setOnClickPendingIntent(
                R.id.widget_root,
                buildOpenPendingIntent(context, widgetId, note.uri)
            )
            views.setOnClickPendingIntent(
                R.id.widget_refresh_btn,
                buildRefreshPendingIntent(context, widgetId)
            )

            return views
        }

        private fun buildUnconfiguredViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_unconfigured)
            // Note: widget_unconfigured has no border radius layout currently but we apply plain hex or rounded theme
            applyThemeToViews(context, views, R.id.widget_unconfigured_root)
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_unconfigured_root, pi)
            return views
        }

        private fun buildEmptyViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_empty)
            applyThemeToViews(context, views, R.id.widget_empty_root)

            views.setOnClickPendingIntent(
                R.id.widget_empty_refresh,
                buildRefreshPendingIntent(context, widgetId)
            )
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, widgetId + 20000, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_empty_root, pi)
            return views
        }

        // ── PendingIntent helpers ─────────────────────────────────────────────

        private fun buildRefreshPendingIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, NoteWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(EXTRA_WIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(
                context, widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildOpenPendingIntent(
            context: Context,
            widgetId: Int,
            fileUri: Uri
        ): PendingIntent {
            val intent = Intent(context, NoteWidgetProvider::class.java).apply {
                action = ACTION_OPEN_NOTE
                putExtra(EXTRA_WIDGET_ID, widgetId)
                putExtra(EXTRA_NOTE_URI, fileUri.toString())
            }
            return PendingIntent.getBroadcast(
                context, widgetId + 10000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // ── WorkManager ───────────────────────────────────────────────────────

        fun scheduleWork(context: Context) {
            val minutes = PreferencesManager.getIntervalMinutes(context).toLong()
            if (minutes < 0) {
                cancelWork(context)
                return
            }
            
            val constraints = Constraints.Builder().build()
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    // ── AppWidgetProvider callbacks ───────────────────────────────────────────

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelWork(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            PreferencesManager.clearWidgetNote(context, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH -> {
                val id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    updateWidget(context, AppWidgetManager.getInstance(context), id)
                }
            }
            ACTION_OPEN_NOTE -> {
                val uriStr = intent.getStringExtra(EXTRA_NOTE_URI)
                if (uriStr != null) {
                    FileOpener.openFile(context, Uri.parse(uriStr))
                }
            }
        }
    }
}
