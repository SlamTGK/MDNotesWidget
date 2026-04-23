package com.mdnotes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * RemoteViewsService that provides data for the StackView list widget.
 */
class NoteListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NoteListRemoteViewsFactory(applicationContext)
    }
}

class NoteListRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private val notes = mutableListOf<MarkdownFileScanner.NoteContent>()

    override fun onCreate() {
        loadNotes()
    }

    override fun onDataSetChanged() {
        loadNotes()
    }

    private fun loadNotes() {
        notes.clear()
        val folderUri = PreferencesManager.getFolderUri(context) ?: return
        val cached = PreferencesManager.getCachedFileUris(context)
        if (cached.isEmpty()) return

        // Load up to 10 random notes for the stack
        val shuffled = cached.shuffled().take(10)
        for (uriStr in shuffled) {
            try {
                val note = MarkdownFileScanner.readNoteContent(
                    context,
                    android.net.Uri.parse(uriStr)
                )
                if (note != null) {
                    notes.add(note)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        notes.clear()
    }

    override fun getCount() = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_stack_item)

        if (position < notes.size) {
            val note = notes[position]
            views.setTextViewText(R.id.widget_stack_title, note.title)

            val content = if (note.content.length > 500) {
                note.content.take(500) + "..."
            } else {
                note.content
            }
            views.setTextViewText(R.id.widget_stack_content, content)

            val dateFormat = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            val dateString = if (note.lastModified > 0) {
                dateFormat.format(java.util.Date(note.lastModified))
            } else ""
            views.setTextViewText(R.id.widget_stack_meta, dateString)

            // Fill-in intent for click handling
            val fillInIntent = Intent().apply {
                putExtra(NoteWidgetProvider.EXTRA_NOTE_URI, note.uri.toString())
            }
            views.setOnClickFillInIntent(R.id.widget_stack_item_root, fillInIntent)
        }

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
}
