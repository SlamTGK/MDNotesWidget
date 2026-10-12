package com.mdnotes.widget

import android.content.Context
import android.content.ComponentName
import android.appwidget.AppWidgetManager
import androidx.work.*
import java.util.concurrent.TimeUnit

object WidgetUpdateCoordinator {
    fun ids(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(NoteWidgetProvider::class.java, NoteListWidgetProvider::class.java).flatMap {
            manager.getAppWidgetIds(ComponentName(context, it)).toList()
        }
    }

    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        for (id in ids(context)) {
            val configured = PreferencesManager.widgetContext(context, id)
            val minutes = PreferencesManager.getIntervalMinutes(configured)
            if (minutes < 15) work.cancelUniqueWork("widget-periodic-$id") else {
                val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(minutes.toLong(), TimeUnit.MINUTES)
                    .setInputData(workDataOf("id" to id, "periodic" to true)).build()
                work.enqueueUniquePeriodicWork("widget-periodic-$id", ExistingPeriodicWorkPolicy.UPDATE, request)
            }
        }
        // Remove the pre-WorkManager alarm when upgrading an existing installation.
        val old = android.app.PendingIntent.getBroadcast(context, 0,
            android.content.Intent(context, NoteWidgetProvider::class.java).setAction("com.mdnotes.widget.ACTION_PERIODIC_UPDATE"),
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE)
        if (old != null) (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).cancel(old)
    }

    fun refresh(context: Context, id: Int, retain: Boolean = false) {
        if (id !in ids(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork("widget-update-$id", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WidgetUpdateWorker>().setInputData(workDataOf("id" to id, "retain" to retain)).build())
    }

    fun refreshAll(context: Context) { ids(context).forEach { refresh(context, it, retain = true) } }

    fun remove(context: Context, id: Int) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("widget-periodic-$id")
            cancelUniqueWork("widget-update-$id")
        }
        PreferencesManager.deleteWidgetSettings(context, id)
    }
}

class WidgetUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getInt("id", AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id !in WidgetUpdateCoordinator.ids(applicationContext)) return Result.success()
        val configured = PreferencesManager.widgetContext(applicationContext, id)
        val periodic = inputData.getBoolean("periodic", false)
        if (periodic && PreferencesManager.isInQuietHours(configured)) return Result.success()
        return try {
            val folder = PreferencesManager.getFolderUri(configured)
            if (folder != null && (periodic || PreferencesManager.getCachedFileUris(configured).isEmpty())) {
                MarkdownFileScanner.refreshCache(configured, folder)
            }
            if (!isStopped) NoteWidgetProvider.render(configured, id, inputData.getBoolean("retain", false))
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("WidgetUpdate", "Cannot update widget $id", e)
            NoteWidgetProvider.showError(configured, id, R.string.saf_permission_lost)
            if (e is SecurityException || runAttemptCount >= 2) Result.failure() else Result.retry()
        }
    }
}
