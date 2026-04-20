package com.mdnotes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager Worker that periodically refreshes all widgets.
 *
 * Lifecycle:
 *  1. Rescans the selected folder for new/deleted .md files.
 *  2. Picks a new random note for each widget instance.
 *  3. Updates the AppWidgetManager so the home screen reflects the change.
 *
 * Interval is set by [NoteWidgetProvider.scheduleWork] based on user preference.
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext

        // Refresh file cache first (picks up added/removed files)
        val folderUri = PreferencesManager.getFolderUri(context)
        if (folderUri != null) {
            MarkdownFileScanner.refreshCache(context, folderUri)
        }

        // Update all running widget instances
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, NoteWidgetProvider::class.java)
        )

        for (widgetId in ids) {
            NoteWidgetProvider.updateWidget(context, manager, widgetId)
        }

        return Result.success()
    }
}
