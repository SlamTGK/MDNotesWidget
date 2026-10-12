package com.mdnotes.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.net.Uri
import android.os.Bundle

/** Direct activity PendingIntent avoids launching activities from a widget broadcast. */
class OpenNoteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id in WidgetUpdateCoordinator.ids(this)) {
            val configured = PreferencesManager.widgetContext(this, id)
            PreferencesManager.getCurrentNoteUri(configured, id)?.let { FileOpener.openFile(configured, Uri.parse(it)) }
        }
        finish()
    }
}
