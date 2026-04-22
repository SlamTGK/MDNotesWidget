package com.mdnotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import java.util.concurrent.TimeUnit

class NoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH   = "com.mdnotes.widget.ACTION_REFRESH"
        const val ACTION_OPEN_NOTE = "com.mdnotes.widget.ACTION_OPEN_NOTE"
        const val ACTION_PERIODIC_UPDATE = "com.mdnotes.widget.ACTION_PERIODIC_UPDATE"
        const val EXTRA_WIDGET_ID  = "extra_widget_id"
        const val EXTRA_NOTE_URI   = "extra_note_uri"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            Thread {
                updateWidgetSync(context, appWidgetManager, widgetId)
            }.start()
        }

        fun updateWidgetSync(
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

            try {
                val fileUri = MarkdownFileScanner.getRandomFile(context, folderUri)

                if (fileUri == null) {
                    appWidgetManager.updateAppWidget(widgetId, buildEmptyViews(context, widgetId))
                    return
                }

                PreferencesManager.setCurrentNoteUri(context, widgetId, fileUri.toString())

                val note = MarkdownFileScanner.readNoteContent(context, fileUri)

                if (note == null) {
                    appWidgetManager.updateAppWidget(widgetId, buildEmptyViews(context, widgetId))
                    return
                }

                appWidgetManager.updateAppWidget(widgetId, buildNoteViews(context, widgetId, note, appWidgetManager))
            } catch (e: Exception) {
                try {
                    appWidgetManager.updateAppWidget(widgetId, buildEmptyViews(context, widgetId))
                } catch (_: Exception) {}
            }
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
            note: MarkdownFileScanner.NoteContent,
            appWidgetManager: AppWidgetManager? = null
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

            // Calculate maxLines to avoid partial lines
            if (appWidgetManager != null) {
                try {
                    val options = appWidgetManager.getAppWidgetOptions(widgetId)
                    val isPortrait = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                    val heightDp = if (isPortrait) {
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    } else {
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    }
                    
                    if (heightDp > 0) {
                        // Total static height approx: Top bar (12+4) + Title (~30) + Meta (~14) + paddings (~22) = 82dp
                        val availableHeightDp = heightDp - 82
                        // lineSpacingMultiplier is 1.3 in XML. SP is roughly equal to DP here.
                        val lineHeightDp = spValue * 1.35f
                        var maxLines = (availableHeightDp / lineHeightDp).toInt()
                        if (maxLines < 1) maxLines = 1
                        views.setInt(R.id.widget_content, "setMaxLines", maxLines)
                    }
                } catch (_: Exception) {}
            }

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

        // ── AlarmManager ───────────────────────────────────────────────────────

        fun scheduleWork(context: Context) {
            val minutes = PreferencesManager.getIntervalMinutes(context).toLong()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, NoteWidgetProvider::class.java).apply {
                action = ACTION_PERIODIC_UPDATE
            }
            val pi = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (minutes < 0) {
                alarmManager.cancel(pi)
                return
            }

            val intervalMillis = minutes * 60 * 1000
            
            // Use setRepeating for consistent widget updates, it will wake the device or batch with other alarms
            alarmManager.setRepeating(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + intervalMillis,
                intervalMillis,
                pi
            )
        }

        fun cancelWork(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, NoteWidgetProvider::class.java).apply {
                action = ACTION_PERIODIC_UPDATE
            }
            val pi = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }

    // ── AppWidgetProvider callbacks ───────────────────────────────────────────

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        Thread {
            try {
                for (widgetId in appWidgetIds) {
                    updateWidgetSync(context, appWidgetManager, widgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        Thread {
            try {
                val currentUriStr = PreferencesManager.getCurrentNoteUri(context, appWidgetId)
                if (currentUriStr != null) {
                    val note = MarkdownFileScanner.readNoteContent(context, Uri.parse(currentUriStr))
                    if (note != null) {
                        appWidgetManager.updateAppWidget(appWidgetId, buildNoteViews(context, appWidgetId, note, appWidgetManager))
                    } else {
                        updateWidgetSync(context, appWidgetManager, appWidgetId)
                    }
                } else {
                    updateWidgetSync(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
            } finally {
                pendingResult.finish()
            }
        }.start()
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
                    val pendingResult = goAsync()
                    Thread {
                        try {
                            updateWidgetSync(context, AppWidgetManager.getInstance(context), id)
                        } finally {
                            pendingResult.finish()
                        }
                    }.start()
                }
            }
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.BOOT_COMPLETED" -> {
                scheduleWork(context)
            }
            ACTION_PERIODIC_UPDATE -> {
                val pendingResult = goAsync()
                Thread {
                    try {
                        val folderUri = PreferencesManager.getFolderUri(context)
                        if (folderUri != null) {
                            MarkdownFileScanner.refreshCache(context, folderUri)
                        }
                        val manager = AppWidgetManager.getInstance(context)
                        val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
                        for (widgetId in ids) {
                            updateWidgetSync(context, manager, widgetId)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }.start()
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
