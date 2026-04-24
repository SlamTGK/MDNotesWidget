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

class FavoritesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private val items = mutableListOf<MarkdownFileScanner.NoteContent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_history)
        toolbar.title = getString(R.string.favorites)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recycler_history)
        tvEmpty = findViewById(R.id.tv_no_history)
        tvEmpty.text = getString(R.string.no_favorites)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = FavAdapter()

        loadFavorites()
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            val favUris = PreferencesManager.getFavorites(this@FavoritesActivity)
            if (favUris.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                return@launch
            }
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            val notes = withContext(Dispatchers.IO) {
                favUris.mapNotNull { uriStr ->
                    try {
                        MarkdownFileScanner.readNoteContent(this@FavoritesActivity, Uri.parse(uriStr))
                    } catch (e: Exception) { null }
                }
            }
            items.clear()
            items.addAll(notes)
            recyclerView.adapter?.notifyDataSetChanged()
        }
    }

    inner class FavAdapter : RecyclerView.Adapter<FavAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_history_title)
            val tvPreview: TextView = view.findViewById(R.id.tv_history_preview)
            val tvFolder: TextView = view.findViewById(R.id.tv_history_folder)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = items[position]
            holder.tvTitle.text = note.title
            holder.tvPreview.text = note.content.take(100)
            holder.tvFolder.text = if (note.folderName.isNotEmpty()) getString(R.string.folder_label, note.folderName) else ""
            holder.itemView.setOnClickListener {
                val intent = Intent(this@FavoritesActivity, NoteViewerActivity::class.java).apply {
                    putExtra(NoteViewerActivity.EXTRA_NOTE_URI, note.uri.toString())
                }
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
    }
}
