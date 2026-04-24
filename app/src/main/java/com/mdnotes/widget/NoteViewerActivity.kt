package com.mdnotes.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_URI = "extra_note_uri"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var searchPanel: View
    private lateinit var searchEdit: EditText
    private lateinit var searchRecycler: RecyclerView
    private lateinit var searchProgress: View
    private lateinit var searchEmpty: TextView
    private lateinit var fabEdit: FloatingActionButton
    private val notePages = mutableListOf<MarkdownFileScanner.NoteContent>()
    private val searchResults = mutableListOf<MarkdownFileScanner.NoteContent>()
    private var currentNoteUri: Uri? = null
    private var isSearchMode = false
    private var isEditMode = false
    private var searchJob: Job? = null
    private var lastSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_viewer)

        toolbar = findViewById(R.id.toolbar)
        viewPager = findViewById(R.id.viewpager_notes)
        bottomNav = findViewById(R.id.bottom_nav)
        searchPanel = findViewById(R.id.search_panel)
        searchEdit = findViewById(R.id.search_edit)
        searchRecycler = findViewById(R.id.search_recycler)
        searchProgress = findViewById(R.id.search_progress)
        searchEmpty = findViewById(R.id.search_empty)
        fabEdit = findViewById(R.id.fab_edit)

        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_favorite -> {
                    toggleFavorite(); true
                }
                R.id.action_share -> {
                    shareCurrentNote(); true
                }
                R.id.action_open_obsidian -> {
                    currentNoteUri?.let { openInObsidian(it) }; true
                }
                R.id.action_open_external -> {
                    currentNoteUri?.let { FileOpener.openWithSystemChooser(this, it) }; true
                }
                R.id.action_create_note -> {
                    showCreateNoteDialog(); true
                }
                R.id.action_delete -> {
                    confirmDeleteCurrentNote(); true
                }
                else -> false
            }
        }

        // ViewPager
        val adapter = NotePagerAdapter()
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position < notePages.size) {
                    val note = notePages[position]
                    toolbar.title = note.title
                    currentNoteUri = note.uri
                    PreferencesManager.addToNoteHistory(this@NoteViewerActivity, note.uri.toString())
                    updateFavoriteMenuItem(note.uri.toString())
                    // Exit edit mode when switching notes
                    if (isEditMode) toggleEditMode()
                }
                if (position >= notePages.size - 2) loadMoreNotes()
            }
        })

        // Bottom navigation
        searchRecycler.layoutManager = LinearLayoutManager(this)

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                lastSearchQuery = query
                performDebouncedSearch(query)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_random -> {
                    exitSearchMode()
                    loadMoreNotes()
                    if (notePages.size > viewPager.currentItem + 1) {
                        viewPager.setCurrentItem(viewPager.currentItem + 1, true)
                    }
                    true
                }
                R.id.nav_history -> {
                    exitSearchMode()
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                R.id.nav_favorites -> {
                    exitSearchMode()
                    startActivity(Intent(this, FavoritesActivity::class.java))
                    true
                }
                R.id.nav_search -> {
                    toggleSearchMode()
                    true
                }
                R.id.nav_on_this_day -> {
                    exitSearchMode()
                    showOnThisDay()
                    true
                }
                else -> false
            }
        }

        // FAB: toggle edit mode
        fabEdit.setOnClickListener { toggleEditMode() }

        val initialUri = intent.getStringExtra(EXTRA_NOTE_URI)
        if (initialUri != null) {
            loadInitialNote(Uri.parse(initialUri))
        } else {
            loadMoreNotes()
        }

        // If launched from widget + button — open create dialog immediately
        if (intent.getBooleanExtra("action_create", false)) {
            viewPager.post { showCreateNoteDialog() }
        }
    }

    // ── Edit mode (Markdown Editor) ──────────────────────────────────────────

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        fabEdit.setImageResource(
            if (isEditMode) R.drawable.ic_chevron_right else R.drawable.ic_add
        )

        // Find the current page's view holder and toggle editor
        val currentPos = viewPager.currentItem
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return
        val holder = recyclerView.findViewHolderForAdapterPosition(currentPos) as? NotePagerAdapter.NoteViewHolder ?: return

        if (isEditMode) {
            val note = notePages.getOrNull(currentPos) ?: return
            holder.editContent.setText(note.rawContent)
            holder.editContent.visibility = View.VISIBLE
            holder.tvContent.visibility = View.GONE
            holder.editContent.requestFocus()
            fabEdit.setImageResource(R.drawable.ic_chevron_right)
            Toast.makeText(this, R.string.edit_mode_on, Toast.LENGTH_SHORT).show()
        } else {
            // Save changes
            val newContent = holder.editContent.text.toString()
            val note = notePages.getOrNull(currentPos)
            if (note != null && newContent != note.rawContent) {
                saveNoteContent(note.uri, newContent, currentPos)
            }
            holder.editContent.visibility = View.GONE
            holder.tvContent.visibility = View.VISIBLE
            fabEdit.setImageResource(R.drawable.ic_add)
        }
    }

    private fun saveNoteContent(uri: Uri, content: String, position: Int) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (success) {
                Toast.makeText(this@NoteViewerActivity, R.string.note_saved, Toast.LENGTH_SHORT).show()
                // Reload the note to refresh preview
                val updated = withContext(Dispatchers.IO) {
                    MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, uri)
                }
                if (updated != null && position < notePages.size) {
                    notePages[position] = updated
                    viewPager.adapter?.notifyItemChanged(position)
                }
            } else {
                Toast.makeText(this@NoteViewerActivity, R.string.error_saving_note, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Search (debounced, with highlights) ───────────────────────────────────

    private fun toggleSearchMode() {
        if (isSearchMode) exitSearchMode() else enterSearchMode()
    }

    private fun enterSearchMode() {
        isSearchMode = true
        searchPanel.visibility = View.VISIBLE
        viewPager.visibility = View.GONE
        fabEdit.visibility = View.GONE
        searchEdit.requestFocus()
    }

    private fun exitSearchMode() {
        isSearchMode = false
        searchPanel.visibility = View.GONE
        viewPager.visibility = View.VISIBLE
        fabEdit.visibility = View.VISIBLE
        searchEdit.setText("")
        lastSearchQuery = ""
        searchResults.clear()
        searchRecycler.adapter?.notifyDataSetChanged()
    }

    private fun performDebouncedSearch(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            searchProgress.visibility = View.GONE
            searchEmpty.visibility = View.GONE
            searchResults.clear()
            updateSearchAdapter()
            return
        }
        searchProgress.visibility = View.VISIBLE
        searchEmpty.visibility = View.GONE

        searchJob = lifecycleScope.launch {
            // Debounce
            delay(SEARCH_DEBOUNCE_MS)

            val results = withContext(Dispatchers.IO) {
                val cached = PreferencesManager.getCachedFileUris(this@NoteViewerActivity)
                cached.mapNotNull { uriStr ->
                    try {
                        val uri = Uri.parse(uriStr)
                        // Fast path: check filename first (no file open needed)
                        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: ""
                        if (fileName.contains(query, ignoreCase = true)) {
                            val docFile = DocumentFile.fromSingleUri(this@NoteViewerActivity, uri)
                            return@mapNotNull MarkdownFileScanner.NoteContent(
                                title = fileName.removeSuffix(".md"),
                                content = "",
                                rawContent = "",
                                fileName = fileName,
                                uri = uri,
                                lastModified = docFile?.lastModified() ?: 0L,
                                folderName = fileName
                            )
                        }
                        // Slow path: open file and scan first 4KB
                        val preview = contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText().take(4096)
                        } ?: return@mapNotNull null
                        if (preview.contains(query, ignoreCase = true)) {
                            MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, uri)
                        } else null
                    } catch (e: Exception) { null }
                }
            }
            searchProgress.visibility = View.GONE
            searchResults.clear()
            searchResults.addAll(results)
            updateSearchAdapter()
            searchEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateSearchAdapter() {
        searchRecycler.adapter = NoteListAdapter(
            items = searchResults,
            searchQuery = lastSearchQuery,
            onItemClick = { note ->
                exitSearchMode()
                notePages.clear()
                viewPager.adapter?.notifyDataSetChanged()
                loadInitialNote(note.uri)
            }
        )
    }

    // ── On This Day ──────────────────────────────────────────────────────────

    private fun showOnThisDay() {
        lifecycleScope.launch {
            val notes = withContext(Dispatchers.IO) {
                val cached = PreferencesManager.getCachedFileUris(this@NoteViewerActivity)
                val calendar = java.util.Calendar.getInstance()
                val todayMonth = calendar.get(java.util.Calendar.MONTH)
                val todayDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                val thisYear = calendar.get(java.util.Calendar.YEAR)

                cached.mapNotNull { uriStr ->
                    try {
                        val uri = Uri.parse(uriStr)
                        val docFile = DocumentFile.fromSingleUri(this@NoteViewerActivity, uri)
                        val lastMod = docFile?.lastModified() ?: return@mapNotNull null
                        if (lastMod <= 0) return@mapNotNull null

                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = lastMod }
                        val fileMonth = cal.get(java.util.Calendar.MONTH)
                        val fileDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
                        val fileYear = cal.get(java.util.Calendar.YEAR)

                        // Same day+month but different year
                        if (fileMonth == todayMonth && fileDay == todayDay && fileYear != thisYear) {
                            MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, uri)
                        } else null
                    } catch (e: Exception) { null }
                }.sortedByDescending { it.lastModified }
            }

            if (notes.isEmpty()) {
                Toast.makeText(this@NoteViewerActivity, R.string.no_on_this_day, Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Replace viewpager content with "On This Day" notes
            notePages.clear()
            notePages.addAll(notes)
            viewPager.adapter?.notifyDataSetChanged()
            viewPager.setCurrentItem(0, false)

            if (notes.isNotEmpty()) {
                toolbar.title = notes[0].title
                currentNoteUri = notes[0].uri
                updateFavoriteMenuItem(notes[0].uri.toString())
            }

            Toast.makeText(
                this@NoteViewerActivity,
                getString(R.string.on_this_day_found, notes.size),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    private fun toggleFavorite() {
        val uri = currentNoteUri?.toString() ?: return
        val added = PreferencesManager.toggleFavorite(this, uri)
        val msg = if (added) R.string.added_to_favorites else R.string.removed_from_favorites
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        updateFavoriteMenuItem(uri)
    }

    private fun updateFavoriteMenuItem(uri: String) {
        val item = toolbar.menu?.findItem(R.id.action_favorite) ?: return
        val isFav = PreferencesManager.isFavorite(this, uri)
        item.setIcon(if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star)
        item.title = getString(if (isFav) R.string.remove_from_favorites else R.string.add_to_favorites)
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    private fun shareCurrentNote() {
        val note = notePages.getOrNull(viewPager.currentItem) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, note.rawContent.ifEmpty { note.content })
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_note)))
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private fun confirmDeleteCurrentNote() {
        val uri = currentNoteUri ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_note)
            .setMessage(R.string.delete_note_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteNote(uri) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteNote(uri: Uri) {
        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                try {
                    DocumentFile.fromSingleUri(this@NoteViewerActivity, uri)?.delete() == true
                } catch (e: Exception) { false }
            }
            if (deleted) {
                Toast.makeText(this@NoteViewerActivity, R.string.note_deleted, Toast.LENGTH_SHORT).show()
                // Remove from cache
                val cached = PreferencesManager.getCachedFileUris(this@NoteViewerActivity).toMutableList()
                cached.remove(uri.toString())
                PreferencesManager.setCachedFileUris(this@NoteViewerActivity, cached)
                // Remove from page list
                val pos = notePages.indexOfFirst { it.uri == uri }
                if (pos >= 0) {
                    notePages.removeAt(pos)
                    viewPager.adapter?.notifyItemRemoved(pos)
                }
                if (notePages.isEmpty()) loadMoreNotes()
            } else {
                Toast.makeText(this@NoteViewerActivity, R.string.error_deleting_file, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Create note ───────────────────────────────────────────────────────────

    private fun showCreateNoteDialog() {
        val folderUri = PreferencesManager.getFolderUri(this)
        if (folderUri == null) {
            Toast.makeText(this, R.string.no_folder_selected, Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.note_name_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        val bodyInput = EditText(this).apply {
            hint = getString(R.string.note_body_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = android.view.Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        container.addView(nameInput)
        container.addView(bodyInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.create_note)
            .setView(container)
            .setPositiveButton(R.string.create_note) { _, _ ->
                val name = nameInput.text.toString().trim()
                val body = bodyInput.text.toString()
                if (name.isNotEmpty()) createNote(folderUri, name, body)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createNote(folderUri: Uri, name: String, body: String = "") {
        lifecycleScope.launch {
            val newUri = withContext(Dispatchers.IO) {
                try {
                    val folder = DocumentFile.fromTreeUri(this@NoteViewerActivity, folderUri)
                    val fileName = if (name.endsWith(".md")) name else "$name.md"
                    val newFile = folder?.createFile("text/markdown", fileName) ?: return@withContext null
                    if (body.isNotBlank()) {
                        contentResolver.openOutputStream(newFile.uri)?.use { out ->
                            out.write(body.toByteArray(Charsets.UTF_8))
                        }
                    }
                    newFile.uri
                } catch (e: Exception) { null }
            }
            if (newUri != null) {
                Toast.makeText(this@NoteViewerActivity, R.string.note_created, Toast.LENGTH_SHORT).show()
                withContext(Dispatchers.IO) {
                    MarkdownFileScanner.refreshCache(this@NoteViewerActivity, folderUri)
                }
                loadInitialNote(newUri)
            } else {
                Toast.makeText(this@NoteViewerActivity, R.string.error_creating_note, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Note loading ──────────────────────────────────────────────────────────

    private fun loadInitialNote(uri: Uri) {
        lifecycleScope.launch {
            val note = withContext(Dispatchers.IO) {
                MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, uri)
            }
            if (note != null) {
                notePages.add(0, note)
                viewPager.adapter?.notifyItemInserted(0)
                toolbar.title = note.title
                currentNoteUri = note.uri
                PreferencesManager.addToNoteHistory(this@NoteViewerActivity, note.uri.toString())
                updateFavoriteMenuItem(note.uri.toString())
            }
            loadMoreNotes()
        }
    }

    private fun loadMoreNotes() {
        val folderUri = PreferencesManager.getFolderUri(this) ?: return
        lifecycleScope.launch {
            repeat(3) {
                val note = withContext(Dispatchers.IO) {
                    val fileUri = MarkdownFileScanner.getRandomFile(this@NoteViewerActivity, folderUri)
                    if (fileUri != null) MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, fileUri)
                    else null
                }
                if (note != null) {
                    notePages.add(note)
                    viewPager.adapter?.notifyItemInserted(notePages.size - 1)
                    if (notePages.size == 1 && currentNoteUri == null) {
                        toolbar.title = note.title
                        currentNoteUri = note.uri
                        updateFavoriteMenuItem(note.uri.toString())
                    }
                }
            }
        }
    }

    // ── Open in Obsidian (uses centralized FileOpener) ────────────────────────

    private fun openInObsidian(uri: Uri) {
        if (!FileOpener.tryOpenWithObsidian(this, uri)) {
            Toast.makeText(this, getString(R.string.obsidian_not_installed), Toast.LENGTH_SHORT).show()
        }
    }

    // ── ViewPager Adapter ─────────────────────────────────────────────────────

    inner class NotePagerAdapter : RecyclerView.Adapter<NotePagerAdapter.NoteViewHolder>() {

        inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_note_title)
            val tvMeta: TextView = view.findViewById(R.id.tv_note_meta)
            val tvContent: TextView = view.findViewById(R.id.tv_note_content)
            val editContent: EditText = view.findViewById(R.id.edit_note_content)
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

            val dateString = if (note.lastModified > 0) {
                MarkdownFileScanner.dateFormat.get()?.format(java.util.Date(note.lastModified)) ?: ""
            } else ""
            val metaString = buildString {
                append(dateString)
                if (note.folderName.isNotEmpty()) {
                    if (isNotEmpty()) append(" \u2022 ")
                    append(note.folderName)
                }
            }
            holder.tvMeta.text = metaString

            // Defer heavy spannable rendering to avoid requestLayout() during layout pass
            holder.itemView.post {
                val spannable = MarkdownFileScanner.renderMarkdownToSpannable(
                    note.rawContent.ifEmpty { note.content }
                )
                holder.tvContent.text = spannable
            }

            // Editor is hidden by default
            holder.editContent.visibility = View.GONE

            // Images — set visibility before posting to avoid requestLayout during layout
            val hasImages = note.imageRefs.isNotEmpty()
            holder.imageScroll.visibility = if (hasImages) View.VISIBLE else View.GONE
            if (hasImages) {
                holder.imageContainer.removeAllViews()
                loadImages(note, holder.imageContainer)
            }
        }

        override fun getItemCount() = notePages.size
    }

    // ── Image loading ─────────────────────────────────────────────────────────

    private fun loadImages(note: MarkdownFileScanner.NoteContent, container: LinearLayout) {
        lifecycleScope.launch {
            val folderUri = PreferencesManager.getFolderUri(this@NoteViewerActivity) ?: return@launch
            for (imageRef in note.imageRefs.take(5)) {
                val bitmap = withContext(Dispatchers.IO) {
                    tryLoadImage(folderUri, note.uri, imageRef)
                }
                if (bitmap != null) {
                    val imageView = ImageView(this@NoteViewerActivity).apply {
                        val params = LinearLayout.LayoutParams(dpToPx(200), dpToPx(150)).apply {
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

    /**
     * Tries multiple URI strategies to load an image referenced in markdown.
     * Uses bitmap downsampling to avoid OOM.
     */
    private fun tryLoadImage(folderUri: Uri, noteUri: Uri, imageRef: String): android.graphics.Bitmap? {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val noteDocId = try { DocumentsContract.getDocumentId(noteUri) } catch (e: Exception) { null }
        val noteFolderDocId = noteDocId?.substringBeforeLast('/')

        // Strategy 1: relative to note's folder
        if (noteFolderDocId != null) {
            val imageDocId = "$noteFolderDocId/$imageRef"
            val imageUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, imageDocId)
            MarkdownFileScanner.loadBitmapDownsampled(this, imageUri)?.let { return it }
        }

        // Strategy 2: relative to vault root
        val imageDocId2 = "$treeDocId/$imageRef"
        val imageUri2 = DocumentsContract.buildDocumentUriUsingTree(folderUri, imageDocId2)
        MarkdownFileScanner.loadBitmapDownsampled(this, imageUri2)?.let { return it }

        // Strategy 3: search by filename anywhere in vault
        val fileName = imageRef.substringAfterLast('/')
        val root = DocumentFile.fromTreeUri(this, folderUri)
        return root?.let { findFileRecursive(it, fileName) }?.let {
            MarkdownFileScanner.loadBitmapDownsampled(this, it.uri)
        }
    }

    private fun findFileRecursive(dir: DocumentFile, name: String): DocumentFile? {
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                findFileRecursive(file, name)?.let { return it }
            } else if (file.name?.equals(name, ignoreCase = true) == true) {
                return file
            }
        }
        return null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
