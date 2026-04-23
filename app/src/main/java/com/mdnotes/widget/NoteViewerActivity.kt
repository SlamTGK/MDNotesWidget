package com.mdnotes.widget

import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_URI = "extra_note_uri"
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private val notePages = mutableListOf<MarkdownFileScanner.NoteContent>()
    private var currentNoteUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_viewer)

        toolbar = findViewById(R.id.toolbar)
        viewPager = findViewById(R.id.viewpager_notes)
        bottomNav = findViewById(R.id.bottom_nav)

        // Back navigation
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open_external -> {
                    currentNoteUri?.let { openExternal(it) }
                    true
                }
                else -> false
            }
        }

        // Setup ViewPager adapter
        val adapter = NotePagerAdapter()
        viewPager.adapter = adapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position < notePages.size) {
                    val note = notePages[position]
                    toolbar.title = note.title
                    currentNoteUri = note.uri
                    PreferencesManager.addToNoteHistory(this@NoteViewerActivity, note.uri.toString())
                }
                // Load more pages when near the end
                if (position >= notePages.size - 2) {
                    loadMoreNotes()
                }
            }
        })

        // Bottom navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_random -> {
                    loadMoreNotes()
                    if (notePages.size > viewPager.currentItem + 1) {
                        viewPager.setCurrentItem(viewPager.currentItem + 1, true)
                    }
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                R.id.nav_open -> {
                    currentNoteUri?.let { openExternal(it) }
                    true
                }
                else -> false
            }
        }

        // Load initial note
        val initialUri = intent.getStringExtra(EXTRA_NOTE_URI)
        if (initialUri != null) {
            loadInitialNote(Uri.parse(initialUri))
        } else {
            loadMoreNotes()
        }
    }

    private fun loadInitialNote(uri: Uri) {
        lifecycleScope.launch {
            val note = withContext(Dispatchers.IO) {
                MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, uri)
            }
            if (note != null) {
                notePages.add(note)
                viewPager.adapter?.notifyItemInserted(notePages.size - 1)
                toolbar.title = note.title
                currentNoteUri = note.uri
                PreferencesManager.addToNoteHistory(this@NoteViewerActivity, note.uri.toString())
            }
            // Pre-load a few random notes
            loadMoreNotes()
        }
    }

    private fun loadMoreNotes() {
        val folderUri = PreferencesManager.getFolderUri(this) ?: return
        lifecycleScope.launch {
            repeat(3) {
                val note = withContext(Dispatchers.IO) {
                    val fileUri = MarkdownFileScanner.getRandomFile(this@NoteViewerActivity, folderUri)
                    if (fileUri != null) {
                        MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, fileUri)
                    } else null
                }
                if (note != null) {
                    notePages.add(note)
                    viewPager.adapter?.notifyItemInserted(notePages.size - 1)

                    // If this is the first note and nothing was loaded yet
                    if (notePages.size == 1 && currentNoteUri == null) {
                        toolbar.title = note.title
                        currentNoteUri = note.uri
                    }
                }
            }
        }
    }

    private fun openExternal(uri: Uri) {
        val openWith = PreferencesManager.getOpenWith(this)
        if (openWith == PreferencesManager.OPEN_WITH_OBSIDIAN) {
            val absolutePath = FileOpener.resolveAbsolutePath(uri)
            if (absolutePath != null) {
                try {
                    val pathParts = absolutePath.removePrefix("/storage/emulated/0/").split("/")
                    val vaultName = if (pathParts.size >= 2) pathParts[0] else null
                    val relativePath = if (vaultName != null && pathParts.size >= 2) {
                        pathParts.drop(1).joinToString("/").removeSuffix(".md")
                    } else {
                        absolutePath.substringAfterLast('/').removeSuffix(".md")
                    }
                    val uriBuilder = StringBuilder("obsidian://open?")
                    if (vaultName != null) uriBuilder.append("vault=${Uri.encode(vaultName)}&")
                    uriBuilder.append("file=${Uri.encode(relativePath)}")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriBuilder.toString())).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
        }

        // System chooser fallback
        for (mimeType in listOf("text/markdown", "text/plain", "*/*")) {
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(Intent.createChooser(viewIntent, getString(R.string.open_external)))
                return
            } catch (_: Exception) {}
        }
        Toast.makeText(this, getString(R.string.error_opening_file), Toast.LENGTH_SHORT).show()
    }

    // ── ViewPager Adapter ─────────────────────────────────────────────────────

    inner class NotePagerAdapter : RecyclerView.Adapter<NotePagerAdapter.NoteViewHolder>() {

        inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_note_title)
            val tvMeta: TextView = view.findViewById(R.id.tv_note_meta)
            val tvContent: TextView = view.findViewById(R.id.tv_note_content)
            val imageScroll: View = view.findViewById(R.id.image_scroll)
            val imageContainer: LinearLayout = view.findViewById(R.id.image_container)
            val tvSwipeHint: TextView = view.findViewById(R.id.tv_swipe_hint)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_note_page, parent, false)
            return NoteViewHolder(view)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            val note = notePages[position]

            holder.tvTitle.text = note.title

            // Meta
            val dateFormat = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
            val dateString = if (note.lastModified > 0) dateFormat.format(java.util.Date(note.lastModified)) else ""
            val metaString = buildString {
                append(dateString)
                if (note.folderName.isNotEmpty()) {
                    if (isNotEmpty()) append(" \u2022 ")
                    append(note.folderName)
                }
            }
            holder.tvMeta.text = metaString

            // Rendered markdown content
            val spannable = MarkdownFileScanner.renderMarkdownToSpannable(note.rawContent.ifEmpty { note.content })
            holder.tvContent.text = spannable

            // Images
            if (note.imageRefs.isNotEmpty()) {
                holder.imageScroll.visibility = View.VISIBLE
                holder.imageContainer.removeAllViews()
                loadImages(note, holder.imageContainer)
            } else {
                holder.imageScroll.visibility = View.GONE
            }
        }

        override fun getItemCount() = notePages.size
    }

    private fun loadImages(note: MarkdownFileScanner.NoteContent, container: LinearLayout) {
        lifecycleScope.launch {
            for (imageRef in note.imageRefs.take(5)) { // Limit to 5 images
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        // Try to resolve image relative to note's folder
                        val noteSegment = note.uri.lastPathSegment ?: return@withContext null
                        val noteFolder = noteSegment.substringBeforeLast('/')
                        val imagePath = "$noteFolder/$imageRef"

                        // Construct image URI from SAF tree
                        val folderUri = PreferencesManager.getFolderUri(this@NoteViewerActivity)
                            ?: return@withContext null
                        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
                        val imageDocId = "$treeDocId/${imageRef}"
                        val imageUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, imageDocId)

                        contentResolver.openInputStream(imageUri)?.use { stream ->
                            android.graphics.BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    val imageView = ImageView(this@NoteViewerActivity).apply {
                        val params = LinearLayout.LayoutParams(
                            dpToPx(200),
                            dpToPx(150)
                        ).apply {
                            marginEnd = dpToPx(8)
                        }
                        layoutParams = params
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(bitmap)
                        clipToOutline = true
                        outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, dpToPx(12).toFloat())
                            }
                        }
                    }
                    container.addView(imageView)
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
