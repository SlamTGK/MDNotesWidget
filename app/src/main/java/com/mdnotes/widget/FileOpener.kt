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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Build and open an Obsidian deep link URI.
     * Centralized logic — used by both FileOpener and NoteViewerActivity.
     */
    fun tryOpenWithObsidian(context: Context, fileUri: Uri): Boolean {
        val absolutePath = resolveAbsolutePath(fileUri) ?: return false
        return tryOpenWithObsidianByPath(context, absolutePath)
    }

    fun tryOpenWithObsidianByPath(context: Context, absolutePath: String): Boolean {
        return try {
            val obsidianUri = buildObsidianUri(absolutePath) ?: return false
            val intent = Intent(Intent.ACTION_VIEW, obsidianUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Builds an obsidian://open?vault=...&file=... URI from an absolute file path.
     */
    fun buildObsidianUri(absolutePath: String): Uri? {
        return try {
            val pathParts = absolutePath.removePrefix("/storage/emulated/0/").split("/")
            val vaultName = if (pathParts.size >= 2) pathParts[0] else null
            val relativePath = if (vaultName != null && pathParts.size >= 2) {
                pathParts.drop(1).joinToString("/").removeSuffix(".md")
            } else {
                absolutePath.substringAfterLast('/').removeSuffix(".md")
            }

            val uriBuilder = StringBuilder("obsidian://open?")
            if (vaultName != null) {
                uriBuilder.append("vault=${Uri.encode(vaultName)}&")
            }
            uriBuilder.append("file=${Uri.encode(relativePath)}")
            Uri.parse(uriBuilder.toString())
        } catch (e: Exception) {
            null
        }
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

    fun resolveAbsolutePath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val colonIdx = docId.indexOf(':')
            if (colonIdx == -1) return null

            val volume = docId.substring(0, colonIdx)
            val path = docId.substring(colonIdx + 1)

            when (volume) {
                "primary" -> "/storage/emulated/0/$path"
                else -> "/storage/$volume/$path"
            }
        } catch (e: Exception) {
            null
        }
    }
}
