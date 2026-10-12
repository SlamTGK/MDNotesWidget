package com.mdnotes.widget

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class NoteRepository(context: Context) {
    private val app = context.applicationContext
    companion object { private val writes = Mutex() }

    class Conflict : IOException("Note changed outside the editor")

    private fun draftFile(uri: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(app.filesDir, "drafts/$key.json")
    }

    suspend fun draft(uri: String): EditorSession? = withContext(Dispatchers.IO) {
        AtomicJsonStore.read(draftFile(uri))?.let {
            val json = JSONObject(it)
            EditorSession(uri, json.getString("original"), json.getString("text"))
        }
    }

    suspend fun keepDraft(session: EditorSession) = withContext(Dispatchers.IO) {
        AtomicJsonStore.write(draftFile(session.uri), JSONObject()
            .put("original", session.original).put("text", session.text).toString())
    }

    suspend fun discardDraft(uri: String) = withContext(Dispatchers.IO) {
        synchronized(AtomicJsonStore) { android.util.AtomicFile(draftFile(uri)).delete() }
    }

    suspend fun save(session: EditorSession): String = withContext(Dispatchers.IO) {
        writes.withLock {
            keepDraft(session)
            val uri = Uri.parse(session.uri)
            val current = MarkdownFileScanner.readRawContent(app, uri) ?: throw IOException("Cannot read note")
            if (current != session.original && current != session.text) throw Conflict()
            if (current != session.text) {
                val stream = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
                stream.use { it.write(session.text.toByteArray(Charsets.UTF_8)) }
                if (MarkdownFileScanner.readRawContent(app, uri) != session.text) throw IOException("Write verification failed")
            }
            // Update all configured trees containing this note, including overlapping roots.
            MarkdownFileScanner.clearSpannableCache()
            TagIndexManager.updateExistingIndexes(app, session.uri, session.text)
            discardDraft(session.uri)
            session.text
        }
    }

    suspend fun trash(uri: Uri): String = withContext(Dispatchers.IO) {
        writes.withLock {
            val note = MarkdownFileScanner.readNoteContent(app, uri) ?: throw IOException("Cannot read note")
            val id = java.util.UUID.randomUUID().toString()
            val json = JSONObject().put("uri", uri.toString()).put("name", note.fileName)
                .put("text", note.rawContent).put("time", System.currentTimeMillis())
            AtomicJsonStore.write(File(app.filesDir, "trash/$id.json"), json.toString())
            if (DocumentFile.fromSingleUri(app, uri)?.delete() != true) {
                File(app.filesDir, "trash/$id.json").delete()
                throw IOException("Cannot delete note")
            }
            discardDraft(uri.toString())
            // Deletion is already complete and its recovery copy must be kept even if cache cleanup fails.
            runCatching { PreferencesManager.removeNoteReferences(app, uri.toString()) }
                .onFailure { android.util.Log.w("NoteRepository", "Reference cleanup failed", it) }
            runCatching { TagIndexManager.updateExistingIndexes(app, uri.toString(), null) }
                .onFailure { android.util.Log.w("NoteRepository", "Index cleanup failed", it) }
            id
        }
    }

    suspend fun trashEntries(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        File(app.filesDir, "trash").listFiles()?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }?.mapNotNull { file ->
                runCatching { file.nameWithoutExtension to JSONObject(AtomicJsonStore.read(file)!!).getString("name") }.getOrNull()
            } ?: emptyList()
    }

    /** Restore to the selected folder without overwriting an existing file. */
    suspend fun restore(id: String, folderUri: Uri): Uri = withContext(Dispatchers.IO) {
        writes.withLock {
            val entry = File(app.filesDir, "trash/$id.json")
            val json = JSONObject(AtomicJsonStore.read(entry) ?: throw IOException("Missing trash entry"))
            val folder = DocumentFile.fromTreeUri(app, folderUri) ?: throw IOException("Missing folder")
            val name = json.getString("name")
            if (folder.findFile(name) != null) throw IOException("A note with this name already exists")
            val file = folder.createFile("text/markdown", name) ?: throw IOException("Cannot create note")
            try {
                (app.contentResolver.openOutputStream(file.uri, "wt") ?: throw IOException("No output stream"))
                    .use { it.write(json.getString("text").toByteArray(Charsets.UTF_8)) }
                if (MarkdownFileScanner.readRawContent(app, file.uri) != json.getString("text")) throw IOException("Write verification failed")
                entry.delete()
                file.uri
            } catch (e: Exception) { file.delete(); throw e }
        }
    }
}
