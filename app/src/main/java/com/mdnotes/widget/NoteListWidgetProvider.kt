package com.mdnotes.widget

import android.content.Context

class NoteListWidgetProvider : NoteWidgetProvider() {
    companion object {
        fun updateAllListWidgets(context: Context) = WidgetUpdateCoordinator.refreshAll(context)
    }
}
