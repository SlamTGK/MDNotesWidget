package com.mdnotes.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast

/**
 * Handles opening a .md file in either Obsidian or the system app chooser.
 *
 * Obsidian URI scheme: obsidian://open?path=<url-encoded-absolute-path>
 * We derive the absolute path from the SAF document ID when possible.
 */
object FileOpener {

    fun openFile(context: Context, fileUri: Uri) {
        val openWith = PreferencesManager.getOpenWith(context)

        if (openWith == PreferencesManager.OPEN_WITH_OBSIDIAN) {
            val absolutePath = resolveAbsolutePath(fileUri)
            if (absolutePath != null && tryOpenWithObsidian(context, absolutePath)) {
                return // Successfully opened in Obsidian
            }
            // Obsidian not installed or path not resolvable — fall through to system chooser
            Toast.makeText(
                context,
                context.getString(R.string.obsidian_not_installed),
                Toast.LENGTH_SHORT
            ).show()
        }

        openWithSystemChooser(context, fileUri)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun tryOpenWithObsidian(context: Context, absolutePath: String): Boolean {
        return try {
            val encodedPath = Uri.encode(absolutePath)
            val obsidianUri = Uri.parse("obsidian://open?path=$encodedPath")
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
        // Try text/markdown first, fall back to text/plain
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

    /**
     * Resolves the absolute filesystem path from a SAF document URI.
     *
     * Works for standard external storage URIs:
     *   content://com.android.externalstorage.documents/document/primary%3AFolder%2Ffile.md
     *   → /storage/emulated/0/Folder/file.md
     */
    private fun resolveAbsolutePath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            // Format: "primary:path/to/file.md" or "XXXX-XXXX:path/to/file.md"
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
