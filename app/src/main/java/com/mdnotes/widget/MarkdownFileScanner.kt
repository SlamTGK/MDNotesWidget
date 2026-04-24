package com.mdnotes.widget

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import androidx.documentfile.provider.DocumentFile

/**
 * Scans a SAF document tree for .md files,
 * caches results, reads note content with Markdown processing.
 */
object MarkdownFileScanner {

    // Pre-compiled regex patterns for performance
    private val REGEX_CODE_BLOCK = Regex("```[\\s\\S]*?```")
    private val REGEX_FRONTMATTER = Regex("^---[\\s\\S]*?---\\s*", RegexOption.MULTILINE)
    private val REGEX_FRONTMATTER_BLOCK = Regex("^---\\s*\\n([\\s\\S]*?)\\n---", RegexOption.MULTILINE)
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
    private val REGEX_ORDERED_LIST = Regex("^(\\d+)\\.\\s+", RegexOption.MULTILINE)
    private val REGEX_BLOCKQUOTE = Regex("^>+\\s*", RegexOption.MULTILINE)
    private val REGEX_HORIZONTAL_RULE = Regex("^[-*_]{3,}\\s*$", RegexOption.MULTILINE)
    private val REGEX_MULTIPLE_NEWLINES = Regex("\\n{3,}")
    private val REGEX_CHECKBOX_UNCHECKED = Regex("^\\s*-\\s*\\[\\s]\\s*", RegexOption.MULTILINE)
    private val REGEX_CHECKBOX_CHECKED = Regex("^\\s*-\\s*\\[xX]\\s*", RegexOption.MULTILINE)

    // For inline formatting
    private val INLINE_BOLD_ITALIC = Regex("\\*\\*\\*(.+?)\\*\\*\\*")
    private val INLINE_BOLD = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__")
    private val INLINE_ITALIC = Regex("(?<![*_])\\*(?![*])(.+?)(?<![*_])\\*(?![*])|(?<![*_])_(?![_])(.+?)(?<![*_])_(?![_])")
    private val INLINE_STRIKE = Regex("~~(.+?)~~")
    private val INLINE_CODE = Regex("`(.+?)`")

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
     * Supports YAML frontmatter tags and inline #hashtags.
     * Anti-repeat: excludes recently shown notes.
     */
    fun getRandomFile(context: Context, folderUri: Uri): Uri? {
        var cached = PreferencesManager.getCachedFileUris(context)
        if (cached.isEmpty()) {
            cached = scanFolder(context, folderUri).map { it.toString() }
            if (cached.isEmpty()) return null
            PreferencesManager.setCachedFileUris(context, cached)
        }

        val tags = PreferencesManager.getTagList(context)
        val tagLogic = PreferencesManager.getTagLogic(context)

        // Filter by tags if set
        val eligible: List<String> = if (tags.isEmpty()) {
            cached
        } else {
            val searchTags = tags.map { it.removePrefix("#").trim().lowercase() }
            cached.filter { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    val rawContent = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: return@filter false
                    val noteTags = extractFrontmatterTags(rawContent)
                    when (tagLogic) {
                        PreferencesManager.TAG_LOGIC_AND ->
                            searchTags.all { tag -> noteTags.contains(tag) }
                        else ->
                            searchTags.any { tag -> noteTags.contains(tag) }
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }

        if (eligible.isEmpty()) return null

        // Anti-repeat: exclude recently shown, unless pool is too small
        val recentlyShown = PreferencesManager.getRecentlyShown(context)
        val pool = if (eligible.size > 3) {
            eligible.filter { it !in recentlyShown }
        } else {
            eligible
        }
        val chosen = (if (pool.isNotEmpty()) pool else eligible).random()
        PreferencesManager.addToRecentlyShown(context, chosen)
        return Uri.parse(chosen)
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
            val imageRefs = extractImageReferences(rawContent)

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
     * Extracts tags from YAML frontmatter AND inline #hashtags in the body.
     * Returns a Set<String> of lowercase tags WITHOUT the '#' prefix.
     *
     * Supports:
     *   tags: [tag1, tag2]
     *   tags:
     *     - tag1
     *     - "#tag2"
     * And inline body #hashtags.
     */
    fun extractFrontmatterTags(rawContent: String): Set<String> {
        val tags = mutableSetOf<String>()

        // Normalize: remove BOM, unify line endings
        val content = rawContent.trimStart('\uFEFF').replace("\r\n", "\n").replace("\r", "\n")

        // Frontmatter must start at position 0 (or after BOM)
        if (!content.startsWith("---")) {
            // No frontmatter — only inline hashtags
            Regex("#([\\w\\-/а-яёА-ЯЁ]+)").findAll(content).forEach {
                tags.add(it.groupValues[1].lowercase())
            }
            return tags
        }

        // Find closing ---
        val closingIdx = content.indexOf("\n---", 3)
        val yaml = if (closingIdx > 0) content.substring(4, closingIdx) else ""
        val bodyStart = if (closingIdx > 0) closingIdx + 4 else content.length

        if (yaml.isNotEmpty()) {
            // Find the "tags:" line
            val lines = yaml.lines()
            var tagsLineIdx = -1
            for ((i, line) in lines.withIndex()) {
                if (line.trimStart().startsWith("tags:")) {
                    tagsLineIdx = i
                    break
                }
            }

            if (tagsLineIdx >= 0) {
                val tagsLine = lines[tagsLineIdx].trimStart().removePrefix("tags:").trim()

                if (tagsLine.startsWith("[")) {
                    // Inline array: tags: [tag1, tag2]
                    tagsLine.removeSurrounding("[", "]")
                        .split(",")
                        .map { it.trim().removeSurrounding("\"").removeSurrounding("'").removePrefix("#").trim().lowercase() }
                        .filter { it.isNotEmpty() }
                        .forEach { tags.add(it) }
                } else if (tagsLine.isEmpty()) {
                    // Block list:
                    // tags:
                    //   - tag1
                    var i = tagsLineIdx + 1
                    while (i < lines.size) {
                        val trimmed = lines[i].trimStart()
                        if (!trimmed.startsWith("-")) break
                        val tag = trimmed.removePrefix("-").trim()
                            .removeSurrounding("\"").removeSurrounding("'")
                            .removePrefix("#").trim().lowercase()
                        if (tag.isNotEmpty()) tags.add(tag)
                        i++
                    }
                } else {
                    // Single inline value: tags: mytag
                    tagsLine.split(",", " ")
                        .map { it.trim().removePrefix("#").trim().lowercase() }
                        .filter { it.isNotEmpty() }
                        .forEach { tags.add(it) }
                }
            }
        }

        // Also extract inline #hashtags from body
        val body = if (bodyStart < content.length) content.substring(bodyStart) else ""
        Regex("#([\\w\\-/а-яёА-ЯЁ]+)").findAll(body).forEach {
            tags.add(it.groupValues[1].lowercase())
        }

        return tags
    }


    /**
     * Extracts image references from markdown content.
     */
    private fun extractImageReferences(rawContent: String): List<String> {
        val refs = mutableListOf<String>()
        REGEX_IMAGE_REF_MD.findAll(rawContent).forEach { match ->
            val path = match.groupValues[2].trim()
            if (path.isNotEmpty() && !path.startsWith("http")) refs.add(path)
        }
        REGEX_IMAGE_REF_OBSIDIAN.findAll(rawContent).forEach { match ->
            val path = match.groupValues[1].trim()
            // Handle "image.png|200" syntax (Obsidian size hints)
            val cleanPath = path.substringBefore("|").trim()
            if (cleanPath.isNotEmpty()) refs.add(cleanPath)
        }
        return refs.distinct()
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
            .replace(REGEX_ORDERED_LIST, "$1. ")
            .replace(REGEX_BLOCKQUOTE, "")
            .replace(REGEX_HORIZONTAL_RULE, "")
            .replace(REGEX_MULTIPLE_NEWLINES, "\n\n")
            .trim()
    }

    /**
     * Renders markdown to SpannableStringBuilder with formatting
     * (headings bold+large, bold, italic, strikethrough, checkboxes, lists).
     * FIXED: spans are preserved — no SpannableStringBuilder(string) reconstruction.
     */
    fun renderMarkdownToSpannable(text: String): SpannableStringBuilder {
        val processed = text
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
                // Pre-process line-level markers
                val processedLine = line
                    .replace(REGEX_CHECKBOX_UNCHECKED, "\u2610 ")
                    .replace(REGEX_CHECKBOX_CHECKED, "\u2611 ")
                    .replace(REGEX_UNORDERED_LIST, "\u2022 ")
                    .replace(REGEX_ORDERED_LIST, "$1. ")
                    .replace(REGEX_BLOCKQUOTE, "\u2502 ")
                    .replace(REGEX_HORIZONTAL_RULE, "\u2500\u2500\u2500\u2500\u2500")
                    .replace(REGEX_LINK_OBSIDIAN, "$1")
                    .replace(REGEX_LINK_MD, "$1")
                    .replace(REGEX_INLINE_CODE, "$1")

                appendInlineFormatted(builder, processedLine)
                builder.append("\n")
            }
        }

        // Collapse multiple blank lines in-place (no span loss!)
        var i = 0
        while (i < builder.length - 2) {
            if (builder[i] == '\n' && builder[i + 1] == '\n' && builder[i + 2] == '\n') {
                var j = i + 2
                while (j < builder.length && builder[j] == '\n') j++
                builder.replace(i + 2, j, "")
            } else {
                i++
            }
        }
        // Trim trailing newlines
        while (builder.isNotEmpty() && builder[builder.length - 1] == '\n') {
            builder.delete(builder.length - 1, builder.length)
        }

        return builder
    }

    /**
     * Appends text with inline bold/italic/strikethrough spans to builder.
     */
    private fun appendInlineFormatted(builder: SpannableStringBuilder, text: String) {
        // Each entry: regex index -> MatchResult?
        val patterns = listOf(INLINE_BOLD_ITALIC, INLINE_BOLD, INLINE_ITALIC, INLINE_STRIKE)
        var remaining = text
        while (remaining.isNotEmpty()) {
            // Find earliest match across all patterns, keeping track of which pattern it came from
            var earliest: MatchResult? = null
            var earliestIdx = -1
            for ((idx, regex) in patterns.withIndex()) {
                val m = regex.find(remaining)
                if (m != null && (earliest == null || m.range.first < earliest.range.first)) {
                    earliest = m
                    earliestIdx = idx
                }
            }

            if (earliest == null) {
                builder.append(remaining)
                break
            }

            // Append text before the match
            if (earliest.range.first > 0) {
                builder.append(remaining.substring(0, earliest.range.first))
            }

            // Get inner content (first non-empty capture group)
            val content = earliest.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: earliest.value
            val spanStart = builder.length
            builder.append(content)

            when (earliestIdx) {
                0 -> builder.setSpan(StyleSpan(Typeface.BOLD_ITALIC), spanStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                1 -> builder.setSpan(StyleSpan(Typeface.BOLD), spanStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                2 -> builder.setSpan(StyleSpan(Typeface.ITALIC), spanStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                3 -> builder.setSpan(StrikethroughSpan(), spanStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            remaining = remaining.substring(earliest.range.last + 1)
        }
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
