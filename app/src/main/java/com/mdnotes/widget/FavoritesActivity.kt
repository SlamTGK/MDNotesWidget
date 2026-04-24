package com.mdnotes.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Displays the user's favorite notes with swipe actions:
 *   ← Swipe left → remove from favorites
 *   → Swipe right → open in Obsidian
 */
class FavoritesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private val favItems = mutableListOf<MarkdownFileScanner.NoteContent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = getString(R.string.favorites)

        recyclerView = findViewById(R.id.recycler_list)
        emptyView = findViewById(R.id.tv_empty)
        emptyView.text = getString(R.string.no_favorites)

        recyclerView.layoutManager = LinearLayoutManager(this)
        loadFavorites()
    }

    private fun loadFavorites() {
        val favUris = PreferencesManager.getFavorites(this)
        favItems.clear()

        for (uriStr in favUris) {
            try {
                val note = MarkdownFileScanner.readNoteContent(this, android.net.Uri.parse(uriStr))
                if (note != null) favItems.add(note)
            } catch (_: Exception) {}
        }

        if (favItems.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        val adapter = NoteListAdapter(
            items = favItems,
            onItemClick = { note ->
                FileOpener.openInViewer(this, note.uri)
            }
        )
        recyclerView.adapter = adapter

        // Swipe actions
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                val note = favItems[pos]

                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        // Remove from favorites
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
                        // Open in Obsidian
                        recyclerView.adapter?.notifyItemChanged(pos) // Reset swipe
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
                    // Swipe right — Obsidian (purple)
                    paint.color = 0xFF7C3AED.toInt()
                    val rect = RectF(
                        itemView.left.toFloat(), itemView.top.toFloat(),
                        itemView.left + dX, itemView.bottom.toFloat()
                    )
                    c.drawRoundRect(rect, radius, radius, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 14f * resources.displayMetrics.density
                    paint.textAlign = Paint.Align.LEFT
                    c.drawText("Obsidian", itemView.left + 24f * resources.displayMetrics.density,
                        (itemView.top + itemView.bottom) / 2f + 5f * resources.displayMetrics.density, paint)
                } else if (dX < 0) {
                    // Swipe left — remove (red)
                    paint.color = 0xFFEF4444.toInt()
                    val rect = RectF(
                        itemView.right + dX, itemView.top.toFloat(),
                        itemView.right.toFloat(), itemView.bottom.toFloat()
                    )
                    c.drawRoundRect(rect, radius, radius, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 14f * resources.displayMetrics.density
                    paint.textAlign = Paint.Align.RIGHT
                    c.drawText("★✕", itemView.right - 24f * resources.displayMetrics.density,
                        (itemView.top + itemView.bottom) / 2f + 5f * resources.displayMetrics.density, paint)
                }
                super.onChildDraw(c, rv, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
    }
}
