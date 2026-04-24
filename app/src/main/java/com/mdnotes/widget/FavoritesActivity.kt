package com.mdnotes.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays the user's favorite notes with swipe actions:
 *   ← Swipe left  → remove from favorites
 *   → Swipe right → open in Obsidian
 */
class FavoritesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar
    private val favItems = mutableListOf<MarkdownFileScanner.NoteContent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.favorites)
        toolbar.setNavigationIcon(R.drawable.ic_chevron_right)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recycler_list)
        emptyView = findViewById(R.id.tv_empty)
        progressBar = findViewById(R.id.progress_loading)
        emptyView.text = getString(R.string.no_favorites)

        recyclerView.layoutManager = LinearLayoutManager(this)
        loadFavorites()
    }

    private fun loadFavorites() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE

        lifecycleScope.launch {
            val favUris = PreferencesManager.getFavorites(this@FavoritesActivity)
            favItems.clear()

            val loaded = withContext(Dispatchers.IO) {
                favUris.mapNotNull { uriStr ->
                    try {
                        MarkdownFileScanner.readNoteContent(this@FavoritesActivity, Uri.parse(uriStr))
                    } catch (_: Exception) { null }
                }
            }

            favItems.addAll(loaded)
            progressBar.visibility = View.GONE

            if (favItems.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                return@launch
            }

            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            val adapter = NoteListAdapter(
                items = favItems,
                onItemClick = { note ->
                    FileOpener.openInViewer(this@FavoritesActivity, note.uri)
                }
            )
            recyclerView.adapter = adapter
            attachSwipeHelper()
        }
    }

    private fun attachSwipeHelper() {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (pos < 0 || pos >= favItems.size) return
                val note = favItems[pos]

                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        PreferencesManager.removeFavorite(this@FavoritesActivity, note.uri.toString())
                        favItems.removeAt(pos)
                        recyclerView.adapter?.notifyItemRemoved(pos)
                        Toast.makeText(this@FavoritesActivity, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show()
                        if (favItems.isEmpty()) {
                            emptyView.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        }
                    }
                    ItemTouchHelper.RIGHT -> {
                        recyclerView.adapter?.notifyItemChanged(pos)
                        if (!FileOpener.tryOpenWithObsidian(this@FavoritesActivity, note.uri)) {
                            Toast.makeText(this@FavoritesActivity, R.string.obsidian_not_installed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val paint = Paint()
                val radius = 12f * resources.displayMetrics.density

                if (dX > 0) {
                    paint.color = 0xFF7C3AED.toInt()
                    val rect = RectF(itemView.left.toFloat(), itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat())
                    c.drawRoundRect(rect, radius, radius, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 14f * resources.displayMetrics.density
                    paint.textAlign = Paint.Align.LEFT
                    c.drawText("Obsidian", itemView.left + 24f * resources.displayMetrics.density,
                        (itemView.top + itemView.bottom) / 2f + 5f * resources.displayMetrics.density, paint)
                } else if (dX < 0) {
                    paint.color = 0xFFEF4444.toInt()
                    val rect = RectF(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                    c.drawRoundRect(rect, radius, radius, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 14f * resources.displayMetrics.density
                    paint.textAlign = Paint.Align.RIGHT
                    c.drawText("Удалить", itemView.right - 24f * resources.displayMetrics.density,
                        (itemView.top + itemView.bottom) / 2f + 5f * resources.displayMetrics.density, paint)
                }
                super.onChildDraw(c, rv, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
    }
}
