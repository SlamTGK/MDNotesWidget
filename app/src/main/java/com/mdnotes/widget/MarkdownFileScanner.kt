package com.mdnotes.widget

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Scans a SAF (Storage Access Framework) document tree for .md files,
 * caches the results, and reads note content as plain text.
 */
object MarkdownFileScanner {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Scans the given SAF folder tree for .md files recursively.
     * Returns a list of content URIs.
     */
    fun scanFolder(context: Context, folderUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val blacklist = PreferencesManager.getFolderBlacklist(context).map { it.lowercase() }
        val result = mutableListOf<Uri>()
        scanRecursive(root, result, blacklist)
        return result
    }

    /**
     * Returns a random .md file from the cached list (or rescans if cache is empty).
     * Returns null if no .md files exist in the folder.
     */
    fun getRandomFile(context: Context, folderUri: Uri): Uri? {
        var cached = PreferencesManager.getCachedFileUris(context)
        if (cached.isEmpty()) {
            cached = scanFolder(context, folderUri).map { it.toString() }
            if (cached.isEmpty()) return null
            PreferencesManager.setCachedFileUris(context, cached)
        }

        val tagFilter = PreferencesManager.getTagFilter(context)
        
        // If no filter, just return random
        if (tagFilter.isBlank()) {
            return Uri.parse(cached.random())
        }

        // If tag filter is set, try up to 30 random files to find the tag
        for (i in 0 until 30) {
            val randomUri = Uri.parse(cached.random())
            try {
                val rawContent = context.contentResolver.openInputStream(randomUri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                if (rawContent != null && rawContent.contains(tagFilter, ignoreCase = true)) {
                    return randomUri
                }
            } catch (e: Exception) {
                // Ignore read errors
            }
        }

        // If not found after 30 tries, return any random file safely to prevent showing empty widgets
        return Uri.parse(cached.random())
    }

    /**
     * Forces a rescan of the folder and updates the cache.
     * Should be called periodically (e.g., from WorkManager) to pick up new files.
     */
    fun refreshCache(context: Context, folderUri: Uri): Int {
        val uris = scanFolder(context, folderUri).map { it.toString() }
        PreferencesManager.setCachedFileUris(context, uris)
        return uris.size
    }

    /**
     * Reads a .md file and returns a [NoteContent] with plain text extracted.
     * Returns null if the file cannot be read.
     */
    fun readNoteContent(context: Context, fileUri: Uri): NoteContent? {
        return try {
            val file = DocumentFile.fromSingleUri(context, fileUri) ?: return null
            val name = file.name ?: return null

            val rawContent = context.contentResolver.openInputStream(fileUri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return null

            NoteContent(
                title = name.removeSuffix(".md"),
                content = stripMarkdown(rawContent),
                fileName = name,
                uri = fileUri
            )
        } catch (e: Exception) {
            null
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun scanRecursive(dir: DocumentFile, result: MutableList<Uri>, blacklist: List<String>) {
        // Stop scanning if current folder name is exactly in the blacklist
        val dirName = dir.name?.lowercase() ?: ""
        if (blacklist.any { it == dirName }) return
        
        for (file in dir.listFiles()) {
            when {
                file.isDirectory -> scanRecursive(file, result, blacklist)
                file.isFile && file.name?.endsWith(".md", ignoreCase = true) == true ->
                    result.add(file.uri)
            }
        }
    }

    /**
     * Strips common Markdown syntax to produce clean plain text
     * suitable for display in RemoteViews TextViews.
     */
    private fun stripMarkdown(text: String): String {
        return text
            // Code blocks (must come before inline code)
            .replace(Regex("```[\\s\\S]*?```"), "")
            // Obsidian-style frontmatter (--- ... ---)
            .replace(Regex("^---[\\s\\S]*?---\\s*", RegexOption.MULTILINE), "")
            // ATX headings: # Title → Title
            .replace(Regex("^#{1,6}\\s+(.+)$", RegexOption.MULTILINE), "$1")
            // Bold + italic: ***text***
            .replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "$1")
            // Bold: **text** or __text__
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("__(.+?)__"), "$1")
            // Italic: *text* or _text_
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("_(.+?)_"), "$1")
            // Strikethrough
            .replace(Regex("~~(.+?)~~"), "$1")
            // Inline code
            .replace(Regex("`(.+?)`"), "$1")
            // Images
            .replace(Regex("!\\[.*?]\\(.*?\\)"), "")
            // Obsidian internal images ![[...]]
            .replace(Regex("!\\[\\[.*?]]"), "")
            // Obsidian internal links [[...]]
            .replace(Regex("\\[\\[(.+?)]]"), "$1")
            // Links [text](url)
            .replace(Regex("\\[(.+?)]\\(.*?\\)"), "$1")
            // Unordered list markers
            .replace(Regex("^[*\\-+]\\s+", RegexOption.MULTILINE), "• ")
            // Ordered list markers
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "• ")
            // Blockquotes
            .replace(Regex("^>+\\s*", RegexOption.MULTILINE), "")
            // Horizontal rules
            .replace(Regex("^[-*_]{3,}\\s*$", RegexOption.MULTILINE), "")
            // Collapse multiple blank lines into one
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    // ── Data class ────────────────────────────────────────────────────────────

    data class NoteContent(
        val title: String,
        val content: String,
        val fileName: String,
        val uri: Uri
    )
}
