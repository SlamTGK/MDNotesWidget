package com.mdnotes.widget

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

/** Per-folder incremental index. Zero timestamps are never considered reliable. */
object TagIndexManager {
    private fun file(context: Context) = PreferencesManager.folderFile(context, "tags.json")

    @Synchronized
    fun buildIndex(context: Context, fileUris: List<String>, onProgress: ((Int, Int) -> Unit)? = null) {
        val old = load(context)
        val next = JSONObject()
        fileUris.forEachIndexed { index, value ->
            onProgress?.invoke(index + 1, fileUris.size)
            val uri = Uri.parse(value)
            val doc = DocumentFile.fromSingleUri(context, uri)
            val modified = doc?.lastModified() ?: 0L
            val size = doc?.length() ?: 0L
            val previous = old.optJSONObject(value)
            if (modified > 0 && previous?.optInt("version") == 2 && previous.optLong("modified") == modified && previous.optLong("size") == size) {
                next.put(value, previous)
            } else {
                MarkdownFileScanner.readRawContent(context, uri)?.let { raw ->
                    next.put(value, record(raw, modified, size))
                }
            }
        }
        AtomicJsonStore.write(file(context), next.toString())
    }

    private fun record(raw: String, modified: Long, size: Long) = JSONObject()
        .put("version", 2)
        .put("modified", modified).put("size", size).put("tags", JSONArray(NoteMetadata.tags(raw).toList()))

    private fun load(context: Context): JSONObject = runCatching {
        JSONObject(AtomicJsonStore.read(file(context)) ?: "{}")
    }.getOrElse { JSONObject() }

    @Synchronized
    fun updateExistingIndexes(context: Context, uri: String, raw: String?) {
        java.io.File(context.filesDir, "indexes").walkTopDown().filter { it.name == "tags.json" }.forEach { file ->
            val root = runCatching { JSONObject(AtomicJsonStore.read(file) ?: "{}") }.getOrNull() ?: return@forEach
            if (root.has(uri)) {
                if (raw == null) root.remove(uri) else root.put(uri, record(raw, 0, 0))
                AtomicJsonStore.write(file, root.toString())
            }
        }
    }

    fun getFilesForTags(context: Context, tags: List<String>): List<String> {
        val index = load(context)
        val excluded = PreferencesManager.getExcludedTags(context)
        val all = PreferencesManager.getTagLogic(context) == PreferencesManager.TAG_LOGIC_AND
        val favorites = if (PreferencesManager.getOption(context, "favorites_only") == "true")
            PreferencesManager.getFavorites(context).toSet() else null
        return PreferencesManager.getCachedFileUris(context).filter { uri ->
            if (favorites != null && uri !in favorites) return@filter false
            val record = index.optJSONObject(uri)
            val actual = if (record != null) {
                val arr = record.getJSONArray("tags")
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } else {
                MarkdownFileScanner.readRawContent(context, Uri.parse(uri))?.let(NoteMetadata::tags) ?: emptySet()
            }
            NoteMetadata.matches(actual, tags, excluded, all)
        }
    }

    fun getAllKnownTags(context: Context): List<String> {
        val index = load(context)
        return index.keys().asSequence().flatMap { key ->
            val tags = index.getJSONObject(key).getJSONArray("tags")
            (0 until tags.length()).asSequence().map { tags.getString(it) }
        }.distinct().sorted().toList()
    }

    fun indexedTags(context: Context): Map<String, Set<String>> {
        val index = load(context)
        return index.keys().asSequence().associateWith { uri ->
            val tags = index.getJSONObject(uri).getJSONArray("tags")
            (0 until tags.length()).map { tags.getString(it) }.toSet()
        }
    }

    fun getFilteredCount(context: Context, tags: List<String>): Int = getFilesForTags(context, tags).size

    @Synchronized fun clearIndex(context: Context) { android.util.AtomicFile(file(context)).delete() }
}
