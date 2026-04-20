package com.mdnotes.widget

import android.content.Context
import android.net.Uri

/**
 * Central manager for all SharedPreferences storage.
 * Stores folder URI, update interval, open-with preference,
 * cached file list, and per-widget current note URI.
 */
object PreferencesManager {

    private const val PREFS_NAME = "md_notes_prefs"

    // Keys
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_INTERVAL_HOURS = "interval_hours"
    private const val KEY_OPEN_WITH = "open_with"
    private const val KEY_FILES_CACHE = "files_cache"

    // Open-with values
    const val OPEN_WITH_OBSIDIAN = "obsidian"
    const val OPEN_WITH_SYSTEM = "system"

    // ── Folder URI ────────────────────────────────────────────────────────────

    fun getFolderUri(context: Context): Uri? {
        val str = prefs(context).getString(KEY_FOLDER_URI, null)
        return str?.let { Uri.parse(it) }
    }

    fun setFolderUri(context: Context, uri: Uri?) {
        prefs(context).edit().putString(KEY_FOLDER_URI, uri?.toString()).apply()
    }

    // ── Update interval ───────────────────────────────────────────────────────

    /** Returns the configured interval in hours (default 1, range 1–24). */
    fun getIntervalHours(context: Context): Int {
        return prefs(context).getInt(KEY_INTERVAL_HOURS, 1).coerceIn(1, 24)
    }

    fun setIntervalHours(context: Context, hours: Int) {
        prefs(context).edit().putInt(KEY_INTERVAL_HOURS, hours.coerceIn(1, 24)).apply()
    }

    // ── Open-with preference ──────────────────────────────────────────────────

    fun getOpenWith(context: Context): String {
        return prefs(context).getString(KEY_OPEN_WITH, OPEN_WITH_SYSTEM) ?: OPEN_WITH_SYSTEM
    }

    fun setOpenWith(context: Context, value: String) {
        prefs(context).edit().putString(KEY_OPEN_WITH, value).apply()
    }

    // ── Cached file list ──────────────────────────────────────────────────────

    /** Returns list of URI strings cached during last scan. */
    fun getCachedFileUris(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_FILES_CACHE, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun setCachedFileUris(context: Context, uris: List<String>) {
        prefs(context).edit().putString(KEY_FILES_CACHE, uris.joinToString("\n")).apply()
    }

    // ── Per-widget current note ───────────────────────────────────────────────

    fun getCurrentNoteUri(context: Context, widgetId: Int): String? {
        return prefs(context).getString("note_$widgetId", null)
    }

    fun setCurrentNoteUri(context: Context, widgetId: Int, uri: String) {
        prefs(context).edit().putString("note_$widgetId", uri).apply()
    }

    fun clearWidgetNote(context: Context, widgetId: Int) {
        prefs(context).edit().remove("note_$widgetId").apply()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
