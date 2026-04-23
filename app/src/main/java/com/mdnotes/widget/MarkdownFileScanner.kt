package com.mdnotes.widget

import android.content.Context
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.documentfile.provider.DocumentFile

/**
 * Scans a SAF document tree for .md files,
 * caches results, reads note content with Markdown processing.
 */
object MarkdownFileScanner {

    // Pre-compiled regex patterns for performance
    private val REGEX_CODE_BLOCK = Regex("```[\\s\\S]*?```")
    private val REGEX_FRONTMATTER = Regex("^---[\\s\\S]*?---\\s*", RegexOption.MULTILINE)
    private val REGEX_ATX_HEADING = Regex("^#{1,6}\\s+(.+)$", RegexOption.MULTILINE)
    private val REGEX_BOLD_ITALIC = Regex("\\*\\*\\*(.+?)\\*\\*\\*")
    private val REGEX_BOLD_STAR = Regex("\\*\\*(.+?)\\*\\*")
    private val REGEX_BOLD_UNDER = Regex("__(.+?)__")
    private val REGEX_ITALIC_STAR = Regex("\\*(.+?)\\*")
    private val REGEX_ITALIC_UNDER = Regex("_(.+?)_")
    private val REGEX_STRIKETHROUGH = Regex("~~(.+?)~~")
    private val REGEX_INLINE_CODE = Regex("`(.+?)`")
    private val REGEX_IMAGE_MD = Regex("!\\[.*?]\\(.*?\\)")
    private val REGEX_IMAGE_OBSIDIAN = Regex("!\\[\\[.*?]]")
    private val REGEX_LINK_OBSIDIAN = Regex("\\[\\[(.+?)]]")
    private val REGEX_LINK_MD = Regex("\\[(.+?)]\\(.*?\\)")
    private val REGEX_UNORDERED_LIST = Regex("^[*\\-+]\\s+", RegexOption.MULTILINE)
    private val REGEX_ORDERED_LIST = Regex("^\\d+\\.\\s+", RegexOption.MULTILINE)
    private val REGEX_BLOCKQUOTE = Regex("^>+\\s*", RegexOption.MULTILINE)
    private val REGEX_HORIZONTAL_RULE = Regex("^[-*_]{3,}\\s*$", RegexOption.MULTILINE)
    private val REGEX_MULTIPLE_NEWLINES = Regex("\\n{3,}")
    private val REGEX_CHECKBOX_UNCHECKED = Regex("^\\s*-\\s*\\[\\s]\\s*", RegexOption.MULTILINE)
    private val REGEX_CHECKBOX_CHECKED = Regex("^\\s*-\\s*\\[x]\\s*", RegexOption.MULTILINE)

    // Regex for extracting image references from markdown
    private val REGEX_IMAGE_REF_MD = Regex("!\\[([^]]*)]\\(([^)]+)\\)")
    private val REGEX_IMAGE_REF_OBSIDIAN = Regex("!\\[\\[([^]]+)]]")

    // ── Public API ────────────────────────────────────────────────────────────

    fun scanFolder(context: Context, folderUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val blacklist = PreferencesManager.getFolderBlacklist(context).map { it.lowercase() }
        val result = mutableListOf<Uri>()
        scanRecursive(root, result, blacklist)
        return result
    }

    /**
     * Returns a random .md file from the cached list, filtered by tags (AND/OR logic).
     * Uses pre-filtered list instead of random retries.
     */
    fun getRandomFile(context: Context, folderUri: Uri): Uri? {
        var cached = PreferencesManager.getCachedFileUris(context)
        if (cached.isEmpty()) {
            cached = scanFolder(context, folderUri).map { it.toString() }
            if (cached.isEmpty()) return null
            PreferencesManager.setCachedFileUris(context, cached)
        }

        val tags = PreferencesManager.getTagList(context)

        // If no filter, just return random
        if (tags.isEmpty()) {
            return Uri.parse(cached.random())
        }

        val tagLogic = PreferencesManager.getTagLogic(context)

        // Pre-filter: read all files and find those matching tags
        val matching = cached.filter { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                val rawContent = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                if (rawContent == null) return@filter false

                when (tagLogic) {
                    PreferencesManager.TAG_LOGIC_AND ->
                        tags.all { tag -> rawContent.contains(tag, ignoreCase = true) }
                    else -> // OR
                        tags.any { tag -> rawContent.contains(tag, ignoreCase = true) }
                }
            } catch (e: Exception) {
                false
            }
        }

        return if (matching.isNotEmpty()) {
            Uri.parse(matching.random())
        } else {
            // No matches found — return null to show "no notes" instead of silent fallback
            null
        }
    }

    fun refreshCache(context: Context, folderUri: Uri): Int {
        val uris = scanFolder(context, folderUri).map { it.toString() }
        PreferencesManager.setCachedFileUris(context, uris)
        return uris.size
    }

    fun readNoteContent(context: Context, fileUri: Uri): NoteContent? {
        return try {
            val file = DocumentFile.fromSingleUri(context, fileUri) ?: return null
            val name = file.name ?: return null
            val lastModified = file.lastModified()

            val rawContent = context.contentResolver.openInputStream(fileUri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return null

            val segment = fileUri.lastPathSegment ?: ""
            val parts = segment.split("/")
            val folderName = if (parts.size > 1) {
                parts[parts.size - 2].replace(Regex("^.*:"), "")
            } else {
                ""
            }

            // Extract image references before stripping markdown
            val imageRefs = extractImageReferences(rawContent, fileUri)

            NoteContent(
                title = name.removeSuffix(".md"),
                content = stripMarkdown(rawContent),
                rawContent = rawContent,
                fileName = name,
                uri = fileUri,
                lastModified = lastModified,
                folderName = folderName,
                imageRefs = imageRefs
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads raw content of a file (no stripping).
     */
    fun readRawContent(context: Context, fileUri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun scanRecursive(dir: DocumentFile, result: MutableList<Uri>, blacklist: List<String>) {
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
     * Extracts image references from markdown content.
     * Returns list of image paths/URIs found in the note.
     */
    private fun extractImageReferences(rawContent: String, noteUri: Uri): List<String> {
        val refs = mutableListOf<String>()

        // Standard markdown images: ![alt](path)
        REGEX_IMAGE_REF_MD.findAll(rawContent).forEach { match ->
            val path = match.groupValues[2].trim()
            if (path.isNotEmpty()) refs.add(path)
        }

        // Obsidian-style: ![[image.png]]
        REGEX_IMAGE_REF_OBSIDIAN.findAll(rawContent).forEach { match ->
            val path = match.groupValues[1].trim()
            if (path.isNotEmpty()) refs.add(path)
        }

        return refs
    }

    /**
     * Strips common Markdown syntax to produce clean plain text
     * suitable for display in RemoteViews TextViews.
     */
    fun stripMarkdown(text: String): String {
        return text
            .replace(REGEX_CODE_BLOCK, "")
            .replace(REGEX_FRONTMATTER, "")
            .replace(REGEX_ATX_HEADING, "$1")
            .replace(REGEX_BOLD_ITALIC, "$1")
            .replace(REGEX_BOLD_STAR, "$1")
            .replace(REGEX_BOLD_UNDER, "$1")
            .replace(REGEX_ITALIC_STAR, "$1")
            .replace(REGEX_ITALIC_UNDER, "$1")
            .replace(REGEX_STRIKETHROUGH, "$1")
            .replace(REGEX_INLINE_CODE, "$1")
            .replace(REGEX_IMAGE_MD, "")
            .replace(REGEX_IMAGE_OBSIDIAN, "")
            .replace(REGEX_LINK_OBSIDIAN, "$1")
            .replace(REGEX_LINK_MD, "$1")
            .replace(REGEX_CHECKBOX_UNCHECKED, "\u2610 ")  // ☐
            .replace(REGEX_CHECKBOX_CHECKED, "\u2611 ")    // ☑
            .replace(REGEX_UNORDERED_LIST, "\u2022 ")      // •
            .replace(REGEX_ORDERED_LIST, "\u2022 ")
            .replace(REGEX_BLOCKQUOTE, "")
            .replace(REGEX_HORIZONTAL_RULE, "")
            .replace(REGEX_MULTIPLE_NEWLINES, "\n\n")
            .trim()
    }

    /**
     * Renders markdown to SpannableStringBuilder with basic formatting
     * (headings, bold, italic) for use in the full-screen viewer.
     */
    fun renderMarkdownToSpannable(text: String): SpannableStringBuilder {
        // First strip frontmatter and code blocks
        var processed = text
            .replace(REGEX_FRONTMATTER, "")
            .replace(REGEX_CODE_BLOCK, "")
            .replace(REGEX_IMAGE_MD, "")
            .replace(REGEX_IMAGE_OBSIDIAN, "")

        val builder = SpannableStringBuilder()

        for (line in processed.lines()) {
            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val content = headingMatch.groupValues[2]
                val start = builder.length
                builder.append(content)
                builder.append("\n")
                val sizeMultiplier = when (level) {
                    1 -> 1.6f
                    2 -> 1.4f
                    3 -> 1.2f
                    else -> 1.1f
                }
                builder.setSpan(
                    RelativeSizeSpan(sizeMultiplier),
                    start, start + content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start, start + content.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                var processedLine = line
                    .replace(REGEX_CHECKBOX_UNCHECKED, "\u2610 ")
                    .replace(REGEX_CHECKBOX_CHECKED, "\u2611 ")
                    .replace(REGEX_UNORDERED_LIST, "\u2022 ")
                    .replace(REGEX_ORDERED_LIST, "\u2022 ")
                    .replace(REGEX_BLOCKQUOTE, "")
                    .replace(REGEX_LINK_OBSIDIAN, "$1")
                    .replace(REGEX_LINK_MD, "$1")

                // Handle bold
                val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
                val italicPattern = Regex("\\*(.+?)\\*")

                var cursor = 0
                val start = builder.length

                // Simple approach: strip formatting markers, add styled text
                processedLine = processedLine
                    .replace(REGEX_BOLD_ITALIC, "$1")
                    .replace(REGEX_BOLD_STAR, "$1")
                    .replace(REGEX_BOLD_UNDER, "$1")
                    .replace(REGEX_ITALIC_STAR, "$1")
                    .replace(REGEX_ITALIC_UNDER, "$1")
                    .replace(REGEX_STRIKETHROUGH, "$1")
                    .replace(REGEX_INLINE_CODE, "$1")

                builder.append(processedLine)
                builder.append("\n")
            }
        }

        // Collapse multiple blank lines
        val result = builder.toString()
            .replace(Regex("\\n{3,}"), "\n\n")
            .trimEnd()

        return SpannableStringBuilder(result)
    }

    // ── Data class ────────────────────────────────────────────────────────────

    data class NoteContent(
        val title: String,
        val content: String,
        val rawContent: String = "",
        val fileName: String,
        val uri: Uri,
        val lastModified: Long,
        val folderName: String,
        val imageRefs: List<String> = emptyList()
    )
}
