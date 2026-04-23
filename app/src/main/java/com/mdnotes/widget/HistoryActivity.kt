package com.mdnotes.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoHistory: TextView
    private val historyItems = mutableListOf<MarkdownFileScanner.NoteContent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_history)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recycler_history)
        tvNoHistory = findViewById(R.id.tv_no_history)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = HistoryAdapter()

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val historyUris = PreferencesManager.getNoteHistory(this@HistoryActivity)

            if (historyUris.isEmpty()) {
                tvNoHistory.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                return@launch
            }

            tvNoHistory.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            val notes = withContext(Dispatchers.IO) {
                historyUris.mapNotNull { uriStr ->
                    try {
                        MarkdownFileScanner.readNoteContent(
                            this@HistoryActivity,
                            Uri.parse(uriStr)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            historyItems.clear()
            historyItems.addAll(notes)
            recyclerView.adapter?.notifyDataSetChanged()
        }
    }

    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
            val note = historyItems[position]
            holder.tvTitle.text = note.title
            holder.tvPreview.text = note.content.take(100)
            holder.tvFolder.text = if (note.folderName.isNotEmpty()) {
                getString(R.string.folder_label, note.folderName)
            } else ""

            holder.itemView.setOnClickListener {
                val intent = Intent(this@HistoryActivity, NoteViewerActivity::class.java).apply {
                    putExtra(NoteViewerActivity.EXTRA_NOTE_URI, note.uri.toString())
                }
                startActivity(intent)
            }
        }

        override fun getItemCount() = historyItems.size
    }
}
