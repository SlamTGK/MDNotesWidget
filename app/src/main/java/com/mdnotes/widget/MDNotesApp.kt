package com.mdnotes.widget

import android.app.Application
import androidx.work.Configuration

/**
 * Custom Application class that initializes WorkManager with a custom configuration.
 * Required on some devices to prevent "WorkManager is not initialized properly" crashes.
 */
class MDNotesApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
