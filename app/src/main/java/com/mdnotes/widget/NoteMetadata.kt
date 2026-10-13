package com.mdnotes.widget

import java.time.LocalDate

/** Pure parsing rules shared by indexing, filtering and the anniversary view. */
object NoteMetadata {
    val frontmatter = Regex("\\A\\uFEFF?---[ \\t]*\\r?\\n((?:[\\s\\S]*?\\r?\\n)?)---[ \\t]*(?:\\r?\\n|$)")
    private val code = Regex("(?s)```.*?```|~~~.*?~~~|`[^`\\n]*`")
    private val hashtag = Regex("(?<![\\p{L}\\p{N}_/#])#([\\p{L}\\p{N}_-]+(?:/[\\p{L}\\p{N}_-]+)*)")

    private fun uncomment(value: String): String {
        var quote: Char? = null
        value.forEachIndexed { index, char ->
            if ((char == '\'' || char == '"') && (index == 0 || value[index - 1] != '\\')) {
                if (quote == char) quote = null else if (quote == null) quote = char
            }
            if (char == '#' && quote == null && (index == 0 || value[index - 1].isWhitespace())) return value.take(index).trimEnd()
        }
        return value
    }

    fun tags(raw: String): Set<String> {
        val content = raw.replace("\r\n", "\n").replace('\r', '\n')
        val header = frontmatter.find(content)
        val result = mutableSetOf<String>()
        var inTags = false
        header?.groupValues?.get(1)?.lineSequence()?.forEach { line ->
            val trimmed = uncomment(line).trim()
            if (trimmed.isEmpty()) return@forEach
            val value = when {
                trimmed.startsWith("tags:") -> {
                    inTags = true
                    trimmed.substringAfter(':').trim()
                }
                inTags && trimmed.startsWith("- ") -> trimmed.removePrefix("- ")
                else -> { inTags = false; "" }
            }
            value.removeSurrounding("[", "]").split(',').forEach {
                val tag = it.trim().removeSurrounding("\"").removeSurrounding("'").removePrefix("#").lowercase()
                if (tag.isNotBlank()) result.add(tag)
            }
        }
        val body = if (header != null) content.substring(header.range.last + 1) else content
        hashtag.findAll(body.replace(code, "")).forEach { result.add(it.groupValues[1].lowercase()) }
        return result
    }

    fun matches(actual: Set<String>, included: List<String>, excluded: List<String>, all: Boolean): Boolean {
        fun contains(tag: String): Boolean {
            val normalized = tag.trim().removePrefix("#").lowercase()
            return actual.any { it == normalized || it.startsWith("$normalized/") }
        }
        return excluded.none(::contains) && (included.isEmpty() ||
            if (all) included.all(::contains) else included.any(::contains))
    }

    fun date(raw: String, name: String, source: String, modified: Long): LocalDate? {
        fun parse(value: String): LocalDate? = runCatching {
            LocalDate.parse(value.trim().trim('"', '\'').take(10))
        }.getOrNull()
        val header = frontmatter.find(raw)?.groupValues?.get(1)?.lines().orEmpty()
        val yamlDate = listOf("created", "date").firstNotNullOfOrNull { key ->
            header.firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.let { parse(uncomment(it)) }
        }
        val fileDate = Regex("\\d{4}-\\d{2}-\\d{2}").find(name)?.value?.let(::parse)
        val modifiedDate = if (modified > 0) java.time.Instant.ofEpochMilli(modified)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate() else null
        return when (source) {
            "yaml" -> yamlDate
            "filename" -> fileDate
            "modified" -> modifiedDate
            else -> yamlDate ?: fileDate
        }
    }
}
