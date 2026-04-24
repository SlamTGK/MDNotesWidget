package com.mdnotes.widget

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Manages a tag index for fast tag-based filtering.
 * Instead of reading every file on each widget update, we build an index
 * during scan time and use it for O(1) lookups.
 *
 * Index format (tag_index.json):
 * {
 *   "tags": { "tag1": ["uri1", "uri2"], "tag2": ["uri3"] },
 *   "allTags": ["tag1", "tag2", "tag3"]
 * }
 */
object TagIndexManager {

    private const val TAG = "TagIndexManager"
    private const val INDEX_FILE = "md_tag_index.json"

    data class TagIndex(
        val tagToUris: Map<String, List<String>>,
        val allTags: List<String>
    )

    /**
     * Build a tag index from all cached file URIs.
     * This reads every file once and extracts tags (frontmatter + inline #hashtags).
     * Should be called during refreshCache() on a background thread.
     *
     * @param onProgress callback with (current, total) for progress reporting
     */
    fun buildIndex(
        context: Context,
        fileUris: List<String>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): TagIndex {
        val tagMap = mutableMapOf<String, MutableList<String>>()
        val total = fileUris.size

        for ((index, uriStr) in fileUris.withIndex()) {
            onProgress?.invoke(index + 1, total)
            try {
                val uri = Uri.parse(uriStr)
                val rawContent = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: continue

                val tags = MarkdownFileScanner.extractFrontmatterTags(rawContent)
                for (tag in tags) {
                    tagMap.getOrPut(tag) { mutableListOf() }.add(uriStr)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to index tags for: $uriStr", e)
            }
        }

        val allTags = tagMap.keys.sorted()
        val index = TagIndex(tagMap, allTags)
        saveIndex(context, index)
        return index
    }

    /**
     * Get file URIs that match the given tags.
     *
     * @param tags list of tag names (without #)
     * @param logic "or" = any tag matches, "and" = all tags must match
     */
    fun getFilesForTags(context: Context, tags: List<String>, logic: String): List<String> {
        val index = loadIndex(context) ?: return emptyList()
        if (tags.isEmpty()) return emptyList()

        val normalizedTags = tags.map { it.removePrefix("#").trim().lowercase() }

        return when (logic) {
            PreferencesManager.TAG_LOGIC_AND -> {
                // Intersection: file must have ALL tags
                val sets = normalizedTags.mapNotNull { tag ->
                    index.tagToUris[tag]?.toSet()
                }
                if (sets.size < normalizedTags.size) {
                    // Some tags don't exist at all
                    emptyList()
                } else {
                    sets.reduce { acc, set -> acc.intersect(set) }.toList()
                }
            }
            else -> {
                // Union: file must have ANY tag
                normalizedTags.flatMap { tag ->
                    index.tagToUris[tag] ?: emptyList()
                }.distinct()
            }
        }
    }

    /**
     * Get all known tags from the index.
     * Useful for tag autocomplete / ChipGroup suggestions.
     */
    fun getAllKnownTags(context: Context): List<String> {
        val index = loadIndex(context) ?: return emptyList()
        return index.allTags
    }

    /**
     * Get the count of files matching given tags (for badge display).
     */
    fun getFilteredCount(context: Context, tags: List<String>, logic: String): Int {
        if (tags.isEmpty()) return PreferencesManager.getCachedFileUris(context).size
        return getFilesForTags(context, tags, logic).size
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private fun saveIndex(context: Context, index: TagIndex) {
        try {
            val root = org.json.JSONObject()
            val tagsObj = org.json.JSONObject()
            for ((tag, uris) in index.tagToUris) {
                tagsObj.put(tag, org.json.JSONArray(uris))
            }
            root.put("tags", tagsObj)
            root.put("allTags", org.json.JSONArray(index.allTags))
            java.io.File(context.filesDir, INDEX_FILE).writeText(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save tag index", e)
        }
    }

    private fun loadIndex(context: Context): TagIndex? {
        return try {
            val file = java.io.File(context.filesDir, INDEX_FILE)
            if (!file.exists()) return null
            val root = org.json.JSONObject(file.readText())
            val tagsObj = root.getJSONObject("tags")
            val tagMap = mutableMapOf<String, List<String>>()
            for (key in tagsObj.keys()) {
                val arr = tagsObj.getJSONArray(key)
                tagMap[key] = (0 until arr.length()).map { arr.getString(it) }
            }
            val allTagsArr = root.getJSONArray("allTags")
            val allTags = (0 until allTagsArr.length()).map { allTagsArr.getString(it) }
            TagIndex(tagMap, allTags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tag index", e)
            null
        }
    }

    /**
     * Clear the tag index file.
     */
    fun clearIndex(context: Context) {
        try {
            java.io.File(context.filesDir, INDEX_FILE).delete()
        } catch (_: Exception) {}
    }
}
