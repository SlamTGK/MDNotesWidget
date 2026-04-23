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
                val absolutePath = resolveAbsolutePath(fileUri)
                if (absolutePath != null && tryOpenWithObsidian(context, absolutePath)) {
                    return
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.obsidian_not_installed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        openWithSystemChooser(context, fileUri)
    }

    private fun openInViewer(context: Context, fileUri: Uri) {
        val intent = Intent(context, NoteViewerActivity::class.java).apply {
            putExtra(NoteViewerActivity.EXTRA_NOTE_URI, fileUri.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun tryOpenWithObsidian(context: Context, absolutePath: String): Boolean {
        return try {
            // Use vault + path parameters for more reliable deep-linking
            val folderUri = absolutePath.substringBeforeLast('/')
            val fileName = absolutePath.substringAfterLast('/').removeSuffix(".md")

            // Try to extract vault name from path pattern: /storage/.../VaultName/...
            // Obsidian vaults are typically top-level folders
            val pathParts = absolutePath.removePrefix("/storage/emulated/0/").split("/")
            val vaultName = if (pathParts.size >= 2) pathParts[0] else null
            val relativePath = if (vaultName != null && pathParts.size >= 2) {
                pathParts.drop(1).joinToString("/").removeSuffix(".md")
            } else {
                fileName
            }

            val uriBuilder = StringBuilder("obsidian://open?")
            if (vaultName != null) {
                uriBuilder.append("vault=${Uri.encode(vaultName)}&")
            }
            uriBuilder.append("file=${Uri.encode(relativePath)}")

            val obsidianUri = Uri.parse(uriBuilder.toString())
            val intent = Intent(Intent.ACTION_VIEW, obsidianUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openWithSystemChooser(context: Context, fileUri: Uri) {
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
