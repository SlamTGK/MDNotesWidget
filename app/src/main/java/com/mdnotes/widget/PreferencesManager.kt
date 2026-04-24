package com.mdnotes.widget

import android.content.Context
import android.net.Uri

/**
 * Central manager for all SharedPreferences storage.
 */
object PreferencesManager {

    private const val PREFS_NAME = "md_notes_prefs"

    // Keys
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_INTERVAL_MINUTES = "interval_minutes"
    private const val KEY_INTERVAL_HOURS = "interval_hours" // Legacy
    private const val KEY_OPEN_WITH = "open_with"
    private const val KEY_FILES_CACHE = "files_cache"

    // Open-with values
    const val OPEN_WITH_OBSIDIAN = "obsidian"
    const val OPEN_WITH_SYSTEM = "system"
    const val OPEN_WITH_VIEWER = "viewer"

    // Theme values
    const val THEME_DEFAULT = "default"
    const val THEME_DARK = "dark"
    const val THEME_TRANSPARENT = "transparent"
    const val THEME_CUSTOM = "custom"

    // Font size values
    const val FONT_SIZE_SMALL = "small"
    const val FONT_SIZE_MEDIUM = "medium"
    const val FONT_SIZE_LARGE = "large"

    // Tag logic
    const val TAG_LOGIC_OR = "or"
    const val TAG_LOGIC_AND = "and"

    private const val MAX_HISTORY_SIZE = 50
    private const val MAX_RECENTLY_SHOWN = 20

    // ── Folder URI ────────────────────────────────────────────────────────────

    fun getFolderUri(context: Context): Uri? {
        val str = prefs(context).getString(KEY_FOLDER_URI, null)
        return str?.let { Uri.parse(it) }
    }

    fun setFolderUri(context: Context, uri: Uri?) {
        prefs(context).edit().putString(KEY_FOLDER_URI, uri?.toString()).apply()
    }

    // ── Update interval ───────────────────────────────────────────────────────

    fun getIntervalMinutes(context: Context): Int {
        val prefs = prefs(context)
        if (prefs.contains(KEY_INTERVAL_MINUTES)) {
            return prefs.getInt(KEY_INTERVAL_MINUTES, 60)
        }
        if (prefs.contains(KEY_INTERVAL_HOURS)) {
            val hours = prefs.getInt(KEY_INTERVAL_HOURS, 1)
            val minutes = hours * 60
            setIntervalMinutes(context, minutes)
            return minutes
        }
        return 60
    }

    fun setIntervalMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_INTERVAL_MINUTES, minutes).apply()
    }

    // ── Open-with preference ──────────────────────────────────────────────────

    fun getOpenWith(context: Context): String {
        return prefs(context).getString(KEY_OPEN_WITH, OPEN_WITH_VIEWER) ?: OPEN_WITH_VIEWER
    }

    fun setOpenWith(context: Context, value: String) {
        prefs(context).edit().putString(KEY_OPEN_WITH, value).apply()
    }

    // ── Widget Theme preference ───────────────────────────────────────────────

    fun getWidgetTheme(context: Context): String {
        return prefs(context).getString("widget_theme", THEME_DEFAULT) ?: THEME_DEFAULT
    }

    fun setWidgetTheme(context: Context, value: String) {
        prefs(context).edit().putString("widget_theme", value).apply()
    }

    // ── Custom widget colors ──────────────────────────────────────────────────

    fun getCustomWidgetBgColor(context: Context): Int {
        return prefs(context).getInt("custom_widget_bg_color", 0xFF1E1B4B.toInt())
    }

    fun setCustomWidgetBgColor(context: Context, color: Int) {
        prefs(context).edit().putInt("custom_widget_bg_color", color).apply()
    }

    fun getCustomWidgetTextColor(context: Context): Int {
        return prefs(context).getInt("custom_widget_text_color", 0xFFF8FAFC.toInt())
    }

    fun setCustomWidgetTextColor(context: Context, color: Int) {
        prefs(context).edit().putInt("custom_widget_text_color", color).apply()
    }

    fun getCustomWidgetTitleColor(context: Context): Int {
        return prefs(context).getInt("custom_widget_title_color", 0xFFFFFFFF.toInt())
    }

    fun setCustomWidgetTitleColor(context: Context, color: Int) {
        prefs(context).edit().putInt("custom_widget_title_color", color).apply()
    }

    // ── Font Size preference ──────────────────────────────────────────────────

    fun getFontSize(context: Context): String {
        return prefs(context).getString("widget_font_size", FONT_SIZE_MEDIUM) ?: FONT_SIZE_MEDIUM
    }

    fun setFontSize(context: Context, value: String) {
        prefs(context).edit().putString("widget_font_size", value).apply()
    }

    // ── Tag Filter preferences ────────────────────────────────────────────────

    fun getTagFilter(context: Context): String {
        return prefs(context).getString("tag_filter", "") ?: ""
    }

    fun setTagFilter(context: Context, value: String) {
        prefs(context).edit().putString("tag_filter", value.trim()).apply()
    }

    fun getTagList(context: Context): List<String> {
        val raw = getTagFilter(context)
        if (raw.isBlank()) return emptyList()
        return raw.split(";", ",").map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
    }

    /**
     * Set tag list from a list of strings (used by ChipGroup).
     */
    fun setTagList(context: Context, tags: List<String>) {
        setTagFilter(context, tags.joinToString(", "))
    }

    fun getTagLogic(context: Context): String {
        return prefs(context).getString("tag_logic", TAG_LOGIC_OR) ?: TAG_LOGIC_OR
    }

    fun setTagLogic(context: Context, value: String) {
        prefs(context).edit().putString("tag_logic", value).apply()
    }

    // ── Folder Blacklist preference ───────────────────────────────────────────

    /** Folders that are always skipped regardless of user settings. */
    private val SYSTEM_BLACKLIST = listOf(
        ".stversions",   // Syncthing version history
        ".obsidian",     // Obsidian config
        ".trash",        // Obsidian trash
        ".git",          // Git repository
        ".github"        // GitHub config
    )

    fun getFolderBlacklist(context: Context): List<String> {
        val raw = prefs(context).getString("folder_blacklist", "") ?: ""
        val user = if (raw.isBlank()) emptyList()
                   else raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        // Merge user list with built-in system blacklist (deduplicated)
        return (SYSTEM_BLACKLIST + user).distinct()
    }

    fun setFolderBlacklist(context: Context, folders: List<String>) {
        prefs(context).edit().putString("folder_blacklist", folders.joinToString(";")).apply()
    }

    fun getFolderBlacklistRaw(context: Context): String {
        return prefs(context).getString("folder_blacklist", "") ?: ""
    }

    fun setFolderBlacklistRaw(context: Context, value: String) {
        prefs(context).edit().putString("folder_blacklist", value).apply()
    }

    // ── Quiet Hours ───────────────────────────────────────────────────────────

    fun isQuietHoursEnabled(context: Context): Boolean {
        return prefs(context).getBoolean("quiet_hours_enabled", false)
    }

    fun setQuietHoursEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("quiet_hours_enabled", enabled).apply()
    }

    /**
     * Get quiet hours start time as Pair(hour, minute).
     */
    fun getQuietHoursStart(context: Context): Pair<Int, Int> {
        val minutes = prefs(context).getInt("quiet_hours_start", 23 * 60)
        return Pair(minutes / 60, minutes % 60)
    }

    fun setQuietHoursStart(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt("quiet_hours_start", hour * 60 + minute).apply()
    }

    /**
     * Get quiet hours end time as Pair(hour, minute).
     */
    fun getQuietHoursEnd(context: Context): Pair<Int, Int> {
        val minutes = prefs(context).getInt("quiet_hours_end", 7 * 60)
        return Pair(minutes / 60, minutes % 60)
    }

    fun setQuietHoursEnd(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt("quiet_hours_end", hour * 60 + minute).apply()
    }

    fun isInQuietHours(context: Context): Boolean {
        if (!isQuietHoursEnabled(context)) return false
        val now = java.util.Calendar.getInstance()
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val (startH, startM) = getQuietHoursStart(context)
        val start = startH * 60 + startM
        val (endH, endM) = getQuietHoursEnd(context)
        val end = endH * 60 + endM
        return if (start <= end) {
            currentMinutes in start..end
        } else {
            currentMinutes >= start || currentMinutes <= end
        }
    }

    // ── Cached file list (JSON) ───────────────────────────────────────────────

    private fun getCacheFile(context: Context): java.io.File {
        return java.io.File(context.filesDir, "md_notes_cache.json")
    }

    fun getCachedFileUris(context: Context): List<String> {
        val file = getCacheFile(context)
        if (!file.exists()) {
            val raw = prefs(context).getString(KEY_FILES_CACHE, "") ?: ""
            if (raw.isNotEmpty()) {
                val list = raw.split("\n").filter { it.isNotBlank() }
                setCachedFileUris(context, list)
                prefs(context).edit().remove(KEY_FILES_CACHE).apply()
                return list
            }
            return emptyList()
        }
        return try {
            val jsonArray = org.json.JSONArray(file.readText())
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setCachedFileUris(context: Context, uris: List<String>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (uri in uris) jsonArray.put(uri)
            getCacheFile(context).writeText(jsonArray.toString())
        } catch (e: Exception) {
            android.util.Log.e("PreferencesManager", "Failed to save cache", e)
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

    // ── Per-widget pinned state ───────────────────────────────────────────────

    fun isNotePinned(context: Context, widgetId: Int): Boolean {
        return prefs(context).getBoolean("pinned_$widgetId", false)
    }

    fun setNotePinned(context: Context, widgetId: Int, pinned: Boolean) {
        prefs(context).edit().putBoolean("pinned_$widgetId", pinned).apply()
    }

    fun clearWidgetPinned(context: Context, widgetId: Int) {
        prefs(context).edit().remove("pinned_$widgetId").apply()
    }

    // ── Note history ──────────────────────────────────────────────────────────

    fun getNoteHistory(context: Context): List<String> {
        return readJsonList(context, "note_history.json")
    }

    fun addToNoteHistory(context: Context, noteUri: String) {
        val history = getNoteHistory(context).toMutableList()
        history.remove(noteUri)
        history.add(0, noteUri)
        writeJsonList(context, "note_history.json", history.take(MAX_HISTORY_SIZE))
    }

    /**
     * Remove a specific URI from history.
     */
    fun removeFromHistory(context: Context, noteUri: String) {
        val history = getNoteHistory(context).toMutableList()
        history.remove(noteUri)
        writeJsonList(context, "note_history.json", history)
    }

    // ── Favorites ────────────────────────────────────────────────────────────

    fun getFavorites(context: Context): List<String> {
        return readJsonList(context, "favorites.json")
    }

    fun isFavorite(context: Context, noteUri: String): Boolean {
        return getFavorites(context).contains(noteUri)
    }

    fun addToFavorites(context: Context, noteUri: String) {
        val favs = getFavorites(context).toMutableList()
        if (!favs.contains(noteUri)) {
            favs.add(0, noteUri)
            writeJsonList(context, "favorites.json", favs)
        }
    }

    fun removeFromFavorites(context: Context, noteUri: String) {
        val favs = getFavorites(context).toMutableList()
        favs.remove(noteUri)
        writeJsonList(context, "favorites.json", favs)
    }

    /**
     * Alias for removeFromFavorites (used in swipe actions).
     */
    fun removeFavorite(context: Context, noteUri: String) {
        removeFromFavorites(context, noteUri)
    }

    fun toggleFavorite(context: Context, noteUri: String): Boolean {
        return if (isFavorite(context, noteUri)) {
            removeFromFavorites(context, noteUri)
            false
        } else {
            addToFavorites(context, noteUri)
            true
        }
    }

    // ── Recently shown (anti-repeat) ──────────────────────────────────────────

    fun getRecentlyShown(context: Context): List<String> {
        return readJsonList(context, "recently_shown.json")
    }

    fun addToRecentlyShown(context: Context, noteUri: String) {
        val recent = getRecentlyShown(context).toMutableList()
        recent.remove(noteUri)
        recent.add(0, noteUri)
        writeJsonList(context, "recently_shown.json", recent.take(MAX_RECENTLY_SHOWN))
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────

    private fun readJsonList(context: Context, filename: String): List<String> {
        return try {
            val file = java.io.File(context.filesDir, filename)
            if (!file.exists()) return emptyList()
            val jsonArray = org.json.JSONArray(file.readText())
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeJsonList(context: Context, filename: String, list: List<String>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (item in list) jsonArray.put(item)
            java.io.File(context.filesDir, filename).writeText(jsonArray.toString())
        } catch (e: Exception) {
            android.util.Log.e("PreferencesManager", "Failed to write $filename", e)
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
