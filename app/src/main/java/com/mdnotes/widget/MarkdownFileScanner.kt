package com.mdnotes.widget

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser

object MarkdownFileScanner {
    private const val TAG = "MDFileScanner"
    private val parser = Parser.builder().build()
    val dateFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
        override fun initialValue() = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
    }

    fun scanFolder(context: Context, folderUri: Uri): List<Uri> {
        val result = linkedSetOf<Uri>()
        val excluded = PreferencesManager.getFolderBlacklist(context).map { it.lowercase() }.toSet()
        val pending = java.util.ArrayDeque<Pair<String, String>>()
        val visited = mutableSetOf<String>()
        val paths = org.json.JSONObject()
        try {
            pending.add(DocumentsContract.getTreeDocumentId(folderUri) to "")
            while (pending.isNotEmpty()) {
                val (parent, parentPath) = pending.removeFirst()
                if (!visited.add(parent)) continue
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, parent)
                val cursor = context.contentResolver.query(children, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ), null, null, null) ?: throw java.io.IOException("Cannot enumerate folder")
                cursor.use {
                    while (it.moveToNext()) {
                        val id = it.getString(0) ?: continue
                        val name = it.getString(1) ?: continue
                        val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
                        if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (name.lowercase() !in excluded) pending.add(id to path)
                        } else {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
                            paths.put(path, uri.toString())
                            if (name.endsWith(".md", true)) result.add(uri)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fast SAF scan failed; using DocumentFile fallback", e)
            result.clear()
            paths.keys().asSequence().toList().forEach(paths::remove)
            fun visit(directory: DocumentFile, prefix: String) {
                directory.listFiles().forEach { file ->
                    val name = file.name ?: return@forEach
                    val path = if (prefix.isBlank()) name else "$prefix/$name"
                    when {
                        file.isDirectory && name.lowercase() !in excluded -> visit(file, path)
                        file.isFile -> {
                            paths.put(path, file.uri.toString())
                            if (name.endsWith(".md", true)) result.add(file.uri)
                        }
                    }
                }
            }
            val root = DocumentFile.fromTreeUri(context, folderUri) ?: throw e
            visit(root, "")
        }
        AtomicJsonStore.write(PreferencesManager.folderFile(context, "paths.json"), paths.toString())
        return result.toList()
    }

    fun getRandomFile(context: Context, folderUri: Uri): Uri? {
        var cached = PreferencesManager.getCachedFileUris(context)
        if (cached.isEmpty()) {
            cached = scanFolder(context, folderUri).map(Uri::toString)
            PreferencesManager.setCachedFileUris(context, cached)
        }
        val tags = PreferencesManager.getTagList(context)
        val eligible = if (tags.isEmpty() && PreferencesManager.getExcludedTags(context).isEmpty() &&
            PreferencesManager.getOption(context, "favorites_only") != "true") cached
            else TagIndexManager.getFilesForTags(context, tags)
        if (eligible.isEmpty()) return null
        val recent = PreferencesManager.getRecentlyShown(context).take((eligible.size - 1).coerceAtLeast(0)).toSet()
        val pool = eligible.filterNot { it in recent }
        val chosen = (pool.ifEmpty { eligible }).random()
        PreferencesManager.addToRecentlyShown(context, chosen)
        return Uri.parse(chosen)
    }

    fun refreshCache(context: Context, folderUri: Uri): Int = refreshCacheWithProgress(context, folderUri).first

    @Synchronized
    fun refreshCacheWithProgress(context: Context, folderUri: Uri,
        onScanProgress: ((String, Int, Int) -> Unit)? = null): Pair<Int, Int> {
        val snapshot = PreferencesManager.ScanContext(context, folderUri, PreferencesManager.getFolderBlacklist(context))
        onScanProgress?.invoke("scan", 0, 0)
        val uris = scanFolder(snapshot, folderUri).map(Uri::toString)
        TagIndexManager.buildIndex(snapshot, uris) { current, total -> onScanProgress?.invoke("index", current, total) }
        PreferencesManager.setCachedFileUris(snapshot, uris)
        return uris.size to TagIndexManager.getFilteredCount(snapshot, PreferencesManager.getTagList(snapshot))
    }

    fun readRawContent(context: Context, fileUri: Uri): String? = try {
        context.contentResolver.openInputStream(fileUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    } catch (e: Exception) { Log.w(TAG, "Cannot read $fileUri", e); null }

    fun readNoteContent(context: Context, fileUri: Uri): NoteContent? = try {
        val file = DocumentFile.fromSingleUri(context, fileUri)
        val name = file?.name
        val raw = readRawContent(context, fileUri)
        if (name == null || raw == null) null else {
            val parent = DocumentsContract.getDocumentId(fileUri).substringBeforeLast('/', "").substringAfterLast('/').substringAfter(':')
            NoteContent(name.replace(Regex("(?i)\\.md$"), ""), stripMarkdown(raw), raw, name,
                fileUri, file.lastModified(), parent, extractImageReferences(raw))
        }
    } catch (e: Exception) { Log.w(TAG, "Cannot read metadata $fileUri", e); null }

    fun extractFrontmatterTags(rawContent: String): Set<String> = NoteMetadata.tags(rawContent)

    fun body(raw: String): String = raw.replace(NoteMetadata.frontmatter, "")

    fun stripMarkdown(text: String): String {
        val out = StringBuilder()
        parser.parse(body(text).replace(Regex("!\\[\\[[^]]+]]"), "")
            .replace(Regex("\\[\\[([^]|]+)(?:\\|([^]]+))?]]")) { it.groupValues[2].ifEmpty { it.groupValues[1] } })
            .accept(object : AbstractVisitor() {
                override fun visit(text: Text) { out.append(text.literal) }
                override fun visit(code: Code) { out.append(code.literal) }
                override fun visit(block: FencedCodeBlock) { out.append(block.literal).append('\n') }
                override fun visit(image: Image) { /* Widget previews omit images. */ }
                override fun visit(lineBreak: SoftLineBreak) { out.append('\n') }
                override fun visit(lineBreak: HardLineBreak) { out.append('\n') }
                override fun visit(paragraph: Paragraph) { visitChildren(paragraph); out.append("\n\n") }
                override fun visit(heading: Heading) { visitChildren(heading); out.append('\n') }
            })
        return out.toString().trim()
    }

    private fun extractImageReferences(raw: String): List<String> = buildList {
        Regex("!\\[[^]]*]\\(([^)]+)\\)").findAll(body(raw)).forEach {
            val path = it.groupValues[1].trim().substringBefore(" \"").removeSurrounding("<", ">")
            if (!path.contains("://")) add(Uri.decode(path))
        }
        Regex("!\\[\\[([^]]+)]]").findAll(body(raw)).forEach { add(it.groupValues[1].substringBefore('|').trim()) }
    }.distinct()

    fun loadBitmapDownsampled(context: Context, uri: Uri, maxWidth: Int = 600, maxHeight: Int = 450): android.graphics.Bitmap? = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = 1
        while (options.outWidth / options.inSampleSize > maxWidth * 2 || options.outHeight / options.inSampleSize > maxHeight * 2) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (e: Exception) { null }

    fun clearSpannableCache() { /* Rendering is now owned by Markwon, with no hash-only text cache. */ }

    fun resolveLink(context: Context, from: Uri?, reference: String, markdown: Boolean): Uri? {
        val index = runCatching { org.json.JSONObject(AtomicJsonStore.read(PreferencesManager.folderFile(context, "paths.json")) ?: "{}") }
            .getOrNull() ?: return null
        val paths = index.keys().asSequence().toList()
        val notePath = paths.firstOrNull { index.getString(it) == from?.toString() }
        val ref = Uri.decode(reference).substringBefore('#')
        fun normalize(value: String): String? {
            val stack = mutableListOf<String>()
            for (part in value.split('/')) when (part) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.lastIndex)
                else -> stack.add(part)
            }
            return stack.joinToString("/")
        }
        val filename = if (markdown && !ref.endsWith(".md", true)) "$ref.md" else ref
        val parent = notePath?.substringBeforeLast('/', "").orEmpty()
        val candidates = listOf(normalize(if (parent.isBlank()) filename else "$parent/$filename"), normalize(filename))
        for (path in candidates) if (path != null && index.has(path)) return Uri.parse(index.getString(path))
        val matches = paths.filter { it.substringAfterLast('/').equals(filename.substringAfterLast('/'), true) }
        return matches.singleOrNull()?.let { Uri.parse(index.getString(it)) }
    }

    data class NoteContent(val title: String, val content: String, val rawContent: String = "", val fileName: String,
        val uri: Uri, val lastModified: Long, val folderName: String, val imageRefs: List<String> = emptyList())
}
