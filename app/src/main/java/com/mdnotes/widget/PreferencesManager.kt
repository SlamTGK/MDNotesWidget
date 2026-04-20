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

    // ── Widget Theme preference ───────────────────────────────────────────────

    const val THEME_DEFAULT = "default"
    const val THEME_DARK = "dark"
    const val THEME_TRANSPARENT = "transparent"

    fun getWidgetTheme(context: Context): String {
        return prefs(context).getString("widget_theme", THEME_DEFAULT) ?: THEME_DEFAULT
    }

    fun setWidgetTheme(context: Context, value: String) {
        prefs(context).edit().putString("widget_theme", value).apply()
    }

    // ── Font Size preference ──────────────────────────────────────────────────

    const val FONT_SIZE_SMALL = "small"
    const val FONT_SIZE_MEDIUM = "medium"
    const val FONT_SIZE_LARGE = "large"

    fun getFontSize(context: Context): String {
        return prefs(context).getString("widget_font_size", FONT_SIZE_MEDIUM) ?: FONT_SIZE_MEDIUM
    }

    fun setFontSize(context: Context, value: String) {
        prefs(context).edit().putString("widget_font_size", value).apply()
    }

    // ── Tag Filter preference ──────────────────────────────────────────────────

    fun getTagFilter(context: Context): String {
        return prefs(context).getString("tag_filter", "") ?: ""
    }

    fun setTagFilter(context: Context, value: String) {
        prefs(context).edit().putString("tag_filter", value.trim()).apply()
    }

    // ── Folder Blacklist preference ───────────────────────────────────────────

    fun getFolderBlacklist(context: Context): List<String> {
        val raw = prefs(context).getString("folder_blacklist", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun getFolderBlacklistRaw(context: Context): String {
        return prefs(context).getString("folder_blacklist", "") ?: ""
    }

    fun setFolderBlacklistRaw(context: Context, value: String) {
        prefs(context).edit().putString("folder_blacklist", value).apply()
    }

    // ── Cached file list (JSON Migration) ─────────────────────────────────────

    private fun getCacheFile(context: Context): java.io.File {
        return java.io.File(context.filesDir, "md_notes_cache.json")
    }

    /** Returns list of URI strings cached during last scan. */
    fun getCachedFileUris(context: Context): List<String> {
        val file = getCacheFile(context)
        if (!file.exists()) {
            // Fallback to legacy shared prefs if json doesn't exist yet
            val raw = prefs(context).getString(KEY_FILES_CACHE, "") ?: ""
            if (raw.isNotEmpty()) {
                val list = raw.split("\n").filter { it.isNotBlank() }
                // migrate
                setCachedFileUris(context, list)
                prefs(context).edit().remove(KEY_FILES_CACHE).apply()
                return list
            }
            return emptyList()
        }

        return try {
            val jsonStr = file.readText()
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Saves a new list of URI strings to JSON cache. */
    fun setCachedFileUris(context: Context, uris: List<String>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (uri in uris) {
                jsonArray.put(uri)
            }
            getCacheFile(context).writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
