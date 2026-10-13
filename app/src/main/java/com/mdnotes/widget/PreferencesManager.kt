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
        if (context is ScanContext) return context.folder
        val str = prefs(context).getString(KEY_FOLDER_URI, null)
        return str?.let { Uri.parse(it) }
    }

    fun setFolderUri(context: Context, uri: Uri?) {
        val preferences = prefs(context)
        val edit = preferences.edit().putString(KEY_FOLDER_URI, uri?.toString())
        if (getFolderUri(context) != uri) preferences.all.keys
            .filter { it.matches(Regex("(?:note|pinned)_\\d+")) }.forEach(edit::remove)
        edit.apply()
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
    val SYSTEM_BLACKLIST = listOf(
        ".stversions",   // Syncthing version history
        ".obsidian",     // Obsidian config
        ".trash",        // Obsidian trash
        ".git",          // Git repository
        ".github"        // GitHub config
    )

    fun getFolderBlacklist(context: Context): List<String> {
        if (context is ScanContext) return context.blacklist
        val raw = prefs(context).getString("folder_blacklist", "") ?: ""
        val user = if (raw.isBlank()) emptyList()
                   else raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        // Merge user list with built-in system blacklist (deduplicated)
        val paths = org.json.JSONArray(getOption(context, "excluded_folder_paths", "[]"))
        return (SYSTEM_BLACKLIST + user + (0 until paths.length()).map { "/" + paths.getString(it) }).distinct()
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
        return if (start == end) false else if (start < end) {
            currentMinutes >= start && currentMinutes < end
        } else {
            currentMinutes >= start || currentMinutes < end
        }
    }

    // ── Cached file list (JSON) ───────────────────────────────────────────────

    private fun getCacheFile(context: Context): java.io.File {
        return folderFile(context, "files.json")
    }

    fun folderFile(context: Context, name: String): java.io.File {
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest(("hidden-folders-v1\n" + (getFolderUri(context)?.toString() ?: "none") + "\n" +
                getFolderBlacklist(context).map { if (it.startsWith('/')) it else it.lowercase() }.distinct().sorted().joinToString("\n")).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return java.io.File(context.filesDir, "indexes/$key/$name")
    }

    fun getCachedFileUris(context: Context): List<String> {
        val file = getCacheFile(context)
        if (!file.exists() && !java.io.File(file.path + ".bak").exists()) {
            // Older caches may contain hidden folders or belong to different exclusion rules.
            // Rebuild from the selected tree instead of importing an unfiltered URI list.
            return emptyList()
        }
        return try {
            val jsonArray = org.json.JSONArray(AtomicJsonStore.read(file) ?: return emptyList())
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setCachedFileUris(context: Context, uris: List<String>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (uri in uris) jsonArray.put(uri)
            AtomicJsonStore.write(getCacheFile(context), jsonArray.toString())
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

    fun addToNoteHistory(context: Context, noteUri: String) = AtomicJsonStore.transaction {
        val history = getNoteHistory(context).toMutableList()
        history.remove(noteUri)
        history.add(0, noteUri)
        writeJsonList(context, "note_history.json", history.take(MAX_HISTORY_SIZE))
    }

    /**
     * Remove a specific URI from history.
     */
    fun removeFromHistory(context: Context, noteUri: String) = AtomicJsonStore.transaction {
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

    fun addToFavorites(context: Context, noteUri: String) = AtomicJsonStore.transaction {
        val favs = getFavorites(context).toMutableList()
        if (!favs.contains(noteUri)) {
            favs.add(0, noteUri)
            writeJsonList(context, "favorites.json", favs)
        }
    }

    fun removeFromFavorites(context: Context, noteUri: String) = AtomicJsonStore.transaction {
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

    fun toggleFavorite(context: Context, noteUri: String): Boolean = AtomicJsonStore.transaction {
        if (isFavorite(context, noteUri)) {
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

    fun addToRecentlyShown(context: Context, noteUri: String) = AtomicJsonStore.transaction {
        val recent = getRecentlyShown(context).toMutableList()
        recent.remove(noteUri)
        recent.add(0, noteUri)
        writeJsonList(context, "recently_shown.json", recent.take(MAX_RECENTLY_SHOWN))
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────

    private fun readJsonList(context: Context, filename: String): List<String> {
        return try {
            val file = java.io.File(context.filesDir, filename)
            val jsonArray = org.json.JSONArray(AtomicJsonStore.read(file) ?: return emptyList())
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            throw java.io.IOException("Cannot read $filename; existing data preserved", e)
        }
    }

    private fun writeJsonList(context: Context, filename: String, list: List<String>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (item in list) jsonArray.put(item)
            AtomicJsonStore.write(java.io.File(context.filesDir, filename), jsonArray.toString())
        } catch (e: Exception) {
            android.util.Log.e("PreferencesManager", "Failed to write $filename", e)
            throw java.io.IOException("Cannot write $filename", e)
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    fun widgetContext(context: Context, widgetId: Int): Context = WidgetContext(context.applicationContext, widgetId)

    class WidgetContext(base: Context, val widgetId: Int) : android.content.ContextWrapper(base)

    class ScanContext(base: Context, val folder: Uri, val blacklist: List<String>) : android.content.ContextWrapper(base)

    private fun prefs(context: Context): android.content.SharedPreferences {
        if (context is ScanContext) return prefs(context.baseContext)
        if (context is SettingsDraft) return context.preferences
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    internal val settingKeys = setOf("folder_uri", "interval_minutes", "interval_hours", "open_with",
        "widget_theme", "custom_widget_bg_color", "custom_widget_text_color", "custom_widget_title_color",
        "widget_font_size", "tag_filter", "tag_logic", "folder_blacklist", "excluded_folder_paths",
        "quiet_hours_enabled", "quiet_hours_start", "quiet_hours_end", "excluded_tags", "favorites_only",
        "obsidian_vault", "notes_subfolder", "note_template", "date_source")

    internal fun settings(context: Context): Map<String, *> = prefs(context).all.filterKeys { it in settingKeys }

    internal fun writeSettings(context: Context, values: Map<String, *>) {
        val edit = prefs(context).edit()
        settingKeys.forEach(edit::remove)
        values.filterKeys { it in settingKeys }.forEach { (key, value) ->
            when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Boolean -> edit.putBoolean(key, value)
            }
        }
        edit.apply()
    }

    /** Preserve the existing widget configuration once, then use one shared configuration. */
    @Synchronized
    fun migrateSharedSettings(context: Context, ids: List<Int>) {
        val global = prefs(context)
        if (global.getBoolean("shared_settings_v1", false)) return
        val locals = ids.sorted().map { it to context.getSharedPreferences("widget_settings_$it", Context.MODE_PRIVATE) }
        locals.firstOrNull { it.second.getBoolean("initialized", false) }?.let {
            writeSettings(context, it.second.all)
        }
        val edit = global.edit()
        locals.forEach { (id, local) ->
            local.getString("note_$id", null)?.let { edit.putString("note_$id", it) }
            if (local.contains("pinned_$id")) edit.putBoolean("pinned_$id", local.getBoolean("pinned_$id", false))
        }
        // Modification time is not creation time.
        if (global.getString("date_source", "auto") == "modified") edit.putString("date_source", "auto")
        edit.putBoolean("shared_settings_v1", true).apply()
    }

    fun deleteWidgetSettings(context: Context, id: Int) {
        clearWidgetNote(context, id)
        clearWidgetPinned(context, id)
        context.deleteSharedPreferences("widget_settings_$id")
    }

    fun getExcludedTags(context: Context): List<String> = getOption(context, "excluded_tags", "")
        .split(',', ';').map { it.trim().removePrefix("#").lowercase() }.filter { it.isNotBlank() }

    fun getOption(context: Context, key: String, default: String = ""): String = prefs(context).getString(key, default) ?: default
    fun setOption(context: Context, key: String, value: String) { prefs(context).edit().putString(key, value).apply() }

    fun removeNoteReferences(context: Context, uri: String) = AtomicJsonStore.transaction {
        removeFromHistory(context, uri)
        removeFromFavorites(context, uri)
        writeJsonList(context, "recently_shown.json", getRecentlyShown(context).filterNot { it == uri })
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        for (provider in listOf(NoteWidgetProvider::class.java, NoteListWidgetProvider::class.java)) {
            for (id in manager.getAppWidgetIds(android.content.ComponentName(context, provider))) {
                val configured = widgetContext(context, id)
                if (getCurrentNoteUri(configured, id) == uri) {
                    clearWidgetNote(configured, id)
                    clearWidgetPinned(configured, id)
                }
            }
        }
        // Different widgets can use overlapping trees. Invalidate each cached index.
        java.io.File(context.filesDir, "indexes").walkTopDown().filter { it.name == "files.json" }.forEach { file ->
            val values = runCatching { org.json.JSONArray(AtomicJsonStore.read(file)) }.getOrNull() ?: return@forEach
            val remaining = (0 until values.length()).map { values.getString(it) }.filterNot { it == uri }
            AtomicJsonStore.write(file, org.json.JSONArray(remaining).toString())
        }
    }
}
