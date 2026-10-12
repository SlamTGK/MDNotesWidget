package com.mdnotes.widget

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class NoteCollectionActivity : AppCompatActivity() {
    protected abstract val favorites: Boolean
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var progress: View
    private val notes = mutableListOf<MarkdownFileScanner.NoteContent>()
    private var load: Job? = null
    private val settings by lazy {
        val id = intent.getIntExtra(NoteViewerActivity.EXTRA_WIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) applicationContext else PreferencesManager.widgetContext(this, id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setTitle(if (favorites) R.string.favorites else R.string.history)
            setNavigationOnClickListener { finish() }
        }
        list = findViewById(R.id.recycler_list)
        empty = findViewById(R.id.tv_empty)
        progress = findViewById(R.id.progress_loading)
        empty.setText(if (favorites) R.string.no_favorites else R.string.no_history)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = NoteListAdapter(notes, { FileOpener.openInViewer(settings, it.uri) })
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                val position = holder.bindingAdapterPosition
                val note = notes.getOrNull(position) ?: return
                if (direction == ItemTouchHelper.RIGHT) {
                    list.adapter?.notifyItemChanged(position)
                    if (!FileOpener.tryOpenWithObsidian(settings, note.uri)) {
                        Toast.makeText(this@NoteCollectionActivity, R.string.obsidian_not_installed, Toast.LENGTH_LONG).show()
                    }
                } else lifecycleScope.launch {
                    try {
                    withContext(Dispatchers.IO) {
                        if (favorites) PreferencesManager.removeFromFavorites(applicationContext, note.uri.toString())
                        else PreferencesManager.removeFromHistory(applicationContext, note.uri.toString())
                    }
                    reload()
                    } catch (e: Exception) {
                        list.adapter?.notifyItemChanged(position)
                        Toast.makeText(this@NoteCollectionActivity, R.string.storage_error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }).attachToRecyclerView(list)
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() {
        load?.cancel()
        progress.visibility = View.VISIBLE
        load = lifecycleScope.launch {
            try {
            val loaded = withContext(Dispatchers.IO) {
                val uris = if (favorites) PreferencesManager.getFavorites(applicationContext) else PreferencesManager.getNoteHistory(applicationContext)
                uris.mapNotNull { MarkdownFileScanner.readNoteContent(applicationContext, Uri.parse(it)) }
            }
            notes.clear(); notes.addAll(loaded)
            list.adapter?.notifyDataSetChanged()
            list.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
            empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(this@NoteCollectionActivity, R.string.storage_error, Toast.LENGTH_LONG).show() }
            finally { progress.visibility = View.GONE }
        }
    }
}
