package com.mdnotes.widget

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Full catalog excludes only service folders. User filters are applied to this snapshot. */
data class SettingsCatalog(val folder: Uri, val folders: List<String>, val notes: List<Entry>) {
    data class Entry(val uri: String, val path: String, val tags: Set<String>)

    data class Selection(val total: Int, val available: Int, val matches: List<Entry>) {
        val excludedByFolders: Int get() = total - available
    }

    fun available(context: Context) = notes.filterNot {
        FolderRules.excludes(it.path.substringBeforeLast('/', ""), PreferencesManager.getFolderBlacklist(context))
    }

    fun selection(context: Context): Selection {
        val included = PreferencesManager.getTagList(context)
        val excluded = PreferencesManager.getExcludedTags(context)
        val all = PreferencesManager.getTagLogic(context) == PreferencesManager.TAG_LOGIC_AND
        val favorites = if (PreferencesManager.getOption(context, "favorites_only") == "true")
            PreferencesManager.getFavorites(context).toSet() else null
        val visible = notes.filterNot { FolderRules.isHidden(it.path.substringBeforeLast('/', "")) }
        val available = available(context)
        val matches = available.filter { (favorites == null || it.uri in favorites) && NoteMetadata.matches(it.tags, included, excluded, all) }
        return Selection(visible.size, available.size, matches)
    }

    fun matching(context: Context): List<Entry> = selection(context).matches

    companion object {
        fun load(context: Context, folder: Uri): SettingsCatalog {
            val all = PreferencesManager.ScanContext(context, folder, PreferencesManager.SYSTEM_BLACKLIST)
            MarkdownFileScanner.refreshCache(all, folder)
            val paths = JSONObject(AtomicJsonStore.read(PreferencesManager.folderFile(all, "paths.json")) ?: "{}")
            val tags = TagIndexManager.indexedTags(all)
            val uris = PreferencesManager.getCachedFileUris(all).toSet()
            val notes = paths.keys().asSequence().mapNotNull { path ->
                val uri = paths.getString(path)
                if (uri in uris) Entry(uri, path, tags[uri].orEmpty()) else null
            }.sortedBy { it.path.lowercase() }.toList()
            val folders = JSONArray(AtomicJsonStore.read(PreferencesManager.folderFile(all, "folders.json")) ?: "[]")
            return SettingsCatalog(folder, (0 until folders.length()).map { folders.getString(it) }.sorted(), notes)
        }
    }
}
