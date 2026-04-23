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
import kotlinx.coroutines.*

class NoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.mdnotes.widget.ACTION_REFRESH"
        const val ACTION_OPEN_NOTE = "com.mdnotes.widget.ACTION_OPEN_NOTE"
        const val ACTION_PERIODIC_UPDATE = "com.mdnotes.widget.ACTION_PERIODIC_UPDATE"
        const val ACTION_PIN_NOTE = "com.mdnotes.widget.ACTION_PIN_NOTE"
        const val EXTRA_WIDGET_ID = "extra_widget_id"
        const val EXTRA_NOTE_URI = "extra_note_uri"

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            scope.launch {
                updateWidgetSync(context, appWidgetManager, widgetId)
            }
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
                // If note is pinned, re-render current note
                val isPinned = PreferencesManager.isNotePinned(context, widgetId)
                val currentUri = PreferencesManager.getCurrentNoteUri(context, widgetId)

                val fileUri: Uri? = if (isPinned && currentUri != null) {
                    Uri.parse(currentUri)
                } else {
                    MarkdownFileScanner.getRandomFile(context, folderUri)
                }

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

                // Add to history
                PreferencesManager.addToNoteHistory(context, fileUri.toString())

                appWidgetManager.updateAppWidget(
                    widgetId,
                    buildNoteViews(context, widgetId, note, appWidgetManager)
                )
            } catch (e: Exception) {
                try {
                    appWidgetManager.updateAppWidget(widgetId, buildEmptyViews(context, widgetId))
                } catch (_: Exception) {}
            }
        }

        // ── RemoteViews builders ──────────────────────────────────────────────

        private fun applyThemeToViews(context: Context, views: RemoteViews, viewId: Int) {
            val theme = PreferencesManager.getWidgetTheme(context)
            when (theme) {
                PreferencesManager.THEME_CUSTOM -> {
                    val bgColor = PreferencesManager.getCustomWidgetBgColor(context)
                    views.setInt(viewId, "setBackgroundColor", bgColor)
                }
                else -> {
                    val bgRes = when (theme) {
                        PreferencesManager.THEME_DARK -> R.drawable.widget_bg_dark
                        PreferencesManager.THEME_TRANSPARENT -> R.drawable.widget_bg_transparent
                        else -> R.drawable.widget_bg_default
                    }
                    views.setInt(viewId, "setBackgroundResource", bgRes)
                }
            }
        }

        private fun applyCustomTextColors(context: Context, views: RemoteViews) {
            val theme = PreferencesManager.getWidgetTheme(context)
            if (theme == PreferencesManager.THEME_CUSTOM) {
                val titleColor = PreferencesManager.getCustomWidgetTitleColor(context)
                val textColor = PreferencesManager.getCustomWidgetTextColor(context)
                views.setTextColor(R.id.widget_title, titleColor)
                views.setTextColor(R.id.widget_content, textColor)
                views.setTextColor(R.id.widget_meta, (textColor and 0x00FFFFFF) or 0x99000000.toInt())
            }
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
                    append(context.getString(R.string.folder_label, note.folderName))
                }
            }
            if (metaString.isNotEmpty()) {
                views.setTextViewText(R.id.widget_meta, metaString)
                views.setViewVisibility(R.id.widget_meta, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_meta, android.view.View.GONE)
            }

            // Pin state
            val isPinned = PreferencesManager.isNotePinned(context, widgetId)
            views.setImageViewResource(
                R.id.widget_pin_btn,
                if (isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin_outline
            )

            // Apply font size preference
            val fontSizeParam = PreferencesManager.getFontSize(context)
            val spValue = when (fontSizeParam) {
                PreferencesManager.FONT_SIZE_SMALL -> 10f
                PreferencesManager.FONT_SIZE_LARGE -> 16f
                else -> 12f
            }
            views.setFloat(R.id.widget_content, "setTextSize", spValue)

            // Apply custom text colors
            applyCustomTextColors(context, views)

            // Calculate maxLines to avoid partial lines
            if (appWidgetManager != null) {
                try {
                    val options = appWidgetManager.getAppWidgetOptions(widgetId)
                    val isPortrait = context.resources.configuration.orientation ==
                            android.content.res.Configuration.ORIENTATION_PORTRAIT
                    val heightDp = if (isPortrait) {
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    } else {
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    }

                    if (heightDp > 0) {
                        val availableHeightDp = heightDp - 82
                        val lineHeightDp = spValue * 1.35f
                        var maxLines = (availableHeightDp / lineHeightDp).toInt()
                        if (maxLines < 1) maxLines = 1
                        views.setInt(R.id.widget_content, "setMaxLines", maxLines)
                    }
                } catch (_: Exception) {}
            }

            // Click: open note
            views.setOnClickPendingIntent(
                R.id.widget_content,
                buildOpenPendingIntent(context, widgetId, note.uri)
            )
            // Click: refresh
            views.setOnClickPendingIntent(
                R.id.widget_refresh_btn,
                buildRefreshPendingIntent(context, widgetId)
            )
            // Click: pin/unpin
            views.setOnClickPendingIntent(
                R.id.widget_pin_btn,
                buildPinPendingIntent(context, widgetId)
            )

            return views
        }

        private fun buildUnconfiguredViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_unconfigured)
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

        private fun buildPinPendingIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, NoteWidgetProvider::class.java).apply {
                action = ACTION_PIN_NOTE
                putExtra(EXTRA_WIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(
                context, widgetId + 30000,
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
        scope.launch {
            try {
                for (widgetId in appWidgetIds) {
                    updateWidgetSync(context, appWidgetManager, widgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        scope.launch {
            try {
                val currentUriStr = PreferencesManager.getCurrentNoteUri(context, appWidgetId)
                if (currentUriStr != null) {
                    val note = MarkdownFileScanner.readNoteContent(context, Uri.parse(currentUriStr))
                    if (note != null) {
                        appWidgetManager.updateAppWidget(
                            appWidgetId,
                            buildNoteViews(context, appWidgetId, note, appWidgetManager)
                        )
                    } else {
                        updateWidgetSync(context, appWidgetManager, appWidgetId)
                    }
                } else {
                    updateWidgetSync(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                pendingResult.finish()
            }
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
            PreferencesManager.clearWidgetPinned(context, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH -> {
                val id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    // Unpin when user manually refreshes
                    PreferencesManager.setNotePinned(context, id, false)
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            updateWidgetSync(context, AppWidgetManager.getInstance(context), id)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            ACTION_PIN_NOTE -> {
                val id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val currentlyPinned = PreferencesManager.isNotePinned(context, id)
                    PreferencesManager.setNotePinned(context, id, !currentlyPinned)
                    // Re-render to update pin icon
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            val currentUriStr = PreferencesManager.getCurrentNoteUri(context, id)
                            if (currentUriStr != null) {
                                val note = MarkdownFileScanner.readNoteContent(
                                    context, Uri.parse(currentUriStr)
                                )
                                if (note != null) {
                                    val manager = AppWidgetManager.getInstance(context)
                                    manager.updateAppWidget(
                                        id,
                                        buildNoteViews(context, id, note, manager)
                                    )
                                }
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.BOOT_COMPLETED" -> {
                scheduleWork(context)
            }
            ACTION_PERIODIC_UPDATE -> {
                // Check quiet hours
                if (PreferencesManager.isInQuietHours(context)) {
                    return
                }

                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val folderUri = PreferencesManager.getFolderUri(context)
                        if (folderUri != null) {
                            MarkdownFileScanner.refreshCache(context, folderUri)
                        }
                        val manager = AppWidgetManager.getInstance(context)
                        val ids = manager.getAppWidgetIds(
                            ComponentName(context, NoteWidgetProvider::class.java)
                        )
                        for (widgetId in ids) {
                            updateWidgetSync(context, manager, widgetId)
                        }
                    } finally {
                        pendingResult.finish()
                    }
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
