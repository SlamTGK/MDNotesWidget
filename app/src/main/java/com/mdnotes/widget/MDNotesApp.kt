package com.mdnotes.widget

import android.app.Application
import com.google.android.material.color.DynamicColors

class MDNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferencesManager.migrateSharedSettings(this, WidgetUpdateCoordinator.ids(this))
        // Apply Material You dynamic colors (Monet) on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
