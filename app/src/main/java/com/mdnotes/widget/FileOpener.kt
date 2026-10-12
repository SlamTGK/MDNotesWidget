package com.mdnotes.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast

/**
 * Handles opening a .md file in either Obsidian, the system app chooser,
 * or the built-in NoteViewerActivity.
 */
object FileOpener {

    fun openFile(context: Context, fileUri: Uri) {
        val openWith = PreferencesManager.getOpenWith(context)

        when (openWith) {
            PreferencesManager.OPEN_WITH_VIEWER -> {
                openInViewer(context, fileUri)
                return
            }
            PreferencesManager.OPEN_WITH_OBSIDIAN -> {
                if (tryOpenWithObsidian(context, fileUri)) return
                Toast.makeText(
                    context,
                    context.getString(R.string.obsidian_not_installed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        openWithSystemChooser(context, fileUri)
    }

    fun openInViewer(context: Context, fileUri: Uri) {
        val intent = Intent(context, NoteViewerActivity::class.java).apply {
            putExtra(NoteViewerActivity.EXTRA_NOTE_URI, fileUri.toString())
            if (context is PreferencesManager.WidgetContext) putExtra(NoteViewerActivity.EXTRA_WIDGET_ID, context.widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Build and open an Obsidian deep link URI.
     * Centralized logic — used by both FileOpener and NoteViewerActivity.
     */
    fun tryOpenWithObsidian(context: Context, fileUri: Uri): Boolean {
        val folder = PreferencesManager.getFolderUri(context) ?: return false
        return try {
            val root = DocumentsContract.getTreeDocumentId(folder)
            val document = DocumentsContract.getDocumentId(fileUri)
            if (!document.startsWith("$root/")) return false
            val vault = PreferencesManager.getOption(context, "obsidian_vault")
                .ifBlank { root.substringAfterLast('/').substringAfter(':') }
            val relative = document.removePrefix("$root/").replace(Regex("(?i)\\.md$"), "")
            val uri = Uri.Builder().scheme("obsidian").authority("open")
                .appendQueryParameter("vault", vault).appendQueryParameter("file", relative).build()
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) { false }
    }

    fun openWithSystemChooser(context: Context, fileUri: Uri) {
        for (mimeType in listOf("text/markdown", "text/plain", "*/*")) {
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mimeType)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                val chooser = Intent.createChooser(
                    viewIntent,
                    context.getString(R.string.open_with)
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(chooser)
                return
            } catch (_: Exception) {
                // Try next MIME type
            }
        }
        Toast.makeText(context, context.getString(R.string.error_opening_file), Toast.LENGTH_SHORT).show()
    }

}
