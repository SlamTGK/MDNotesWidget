package com.mdnotes.widget

import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Universal RecyclerView adapter for note lists.
 * Used by HistoryActivity, FavoritesActivity, and search results in NoteViewerActivity.
 * Eliminates code duplication across three nearly identical adapters.
 */
class NoteListAdapter(
    private val items: List<MarkdownFileScanner.NoteContent>,
    private val onItemClick: (MarkdownFileScanner.NoteContent) -> Unit,
    private val searchQuery: String = "",
    private val showFolder: Boolean = true,
    private val highlightColor: Int = 0x55FFEB3B
) : RecyclerView.Adapter<NoteListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_history_title)
        val tvPreview: TextView = view.findViewById(R.id.tv_history_preview)
        val tvFolder: TextView = view.findViewById(R.id.tv_history_folder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = items[position]
        val context = holder.itemView.context

        // Title with optional search highlight
        if (searchQuery.isNotEmpty()) {
            holder.tvTitle.text = highlightText(note.title, searchQuery)
        } else {
            holder.tvTitle.text = note.title
        }

        // Preview with optional search highlight
        val preview = note.content.take(120)
        if (searchQuery.isNotEmpty()) {
            holder.tvPreview.text = highlightText(preview, searchQuery)
        } else {
            holder.tvPreview.text = preview
        }

        // Folder info
        if (showFolder && note.folderName.isNotEmpty()) {
            holder.tvFolder.text = context.getString(R.string.folder_label, note.folderName)
            holder.tvFolder.visibility = View.VISIBLE
        } else {
            holder.tvFolder.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(note)
        }
    }

    override fun getItemCount() = items.size

    /**
     * Highlights all occurrences of [query] in [text] with the highlight color.
     */
    private fun highlightText(text: String, query: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        if (query.length < 2) return builder

        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var start = lowerText.indexOf(lowerQuery)
        while (start >= 0) {
            builder.setSpan(
                BackgroundColorSpan(highlightColor),
                start,
                start + query.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            start = lowerText.indexOf(lowerQuery, start + query.length)
        }
        return builder
    }
}
