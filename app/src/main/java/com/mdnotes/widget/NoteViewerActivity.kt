package com.mdnotes.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.noties.markwon.Markwon
import io.noties.markwon.LinkResolver
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

class NoteViewerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_NOTE_URI = "extra_note_uri"
        const val EXTRA_WIDGET_ID = "viewer_widget_id"
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
    private val pages = mutableListOf<MarkdownFileScanner.NoteContent>()
    private val editor by lazy { ViewModelProvider(this)[EditorViewModel::class.java] }
    private val repository by lazy { NoteRepository(this) }
    private val settings by lazy {
        val id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManagerId.INVALID)
        if (id == AppWidgetManagerId.INVALID) applicationContext else PreferencesManager.widgetContext(this, id)
    }
    private var isEditing = false
    private var isSearching = false
    private var randomMode = true
    private var loading = false
    private var searchJob: Job? = null
    private var pagesJob: Job? = null
    private var navigationPending = false
    private val adapter = NotePagerAdapter()
    private val markwon by lazy {
        Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: io.noties.markwon.MarkwonConfiguration.Builder) {
                    builder.linkResolver(LinkResolver { _, link -> openLink(link) })
                }
            }).build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        toolbar.setNavigationOnClickListener { leaveEditor { finish() } }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearching) showSearch(false) else leaveEditor { finish() }
            }
        })
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_discard -> discardEdits()
                else -> leaveEditor {
                    when (item.itemId) {
                        R.id.action_favorite -> toggleFavorite()
                        R.id.action_share -> current()?.let { note ->
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, note.title)
                                putExtra(Intent.EXTRA_TEXT, note.rawContent)
                            }, getString(R.string.share_note)))
                        }
                        R.id.action_open_obsidian -> current()?.let {
                            if (!FileOpener.tryOpenWithObsidian(settings, it.uri)) toast(R.string.obsidian_not_installed)
                        }
                        R.id.action_open_external -> current()?.let { FileOpener.openWithSystemChooser(this, it.uri) }
                        R.id.action_create_note -> createDialog(false)
                        R.id.action_daily_note -> createDialog(true)
                        R.id.action_trash -> showTrash()
                        R.id.action_delete -> confirmDelete()
                    }
                }
            }
            true
        }
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                refreshTitle()
                current()?.let { note -> lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { PreferencesManager.addToNoteHistory(applicationContext, note.uri.toString()) }
                        .onFailure { android.util.Log.w("NoteViewer", "History update failed", it) }
                } }
                if (randomMode && position >= pages.size - 2) loadMore()
            }
        })
        searchRecycler.layoutManager = LinearLayoutManager(this)
        searchEdit.doAfterTextChanged { search(it?.toString().orEmpty()) }
        bottomNav.setOnItemSelectedListener { item ->
            leaveEditor {
                showSearch(item.itemId == R.id.nav_search)
                when (item.itemId) {
                    R.id.nav_random -> {
                        randomMode = true
                        if (viewPager.currentItem + 1 < pages.size) viewPager.currentItem++
                        else loadMore(advance = true)
                    }
                    R.id.nav_history -> startActivity(Intent(this, HistoryActivity::class.java)
                        .putExtra(EXTRA_WIDGET_ID, intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManagerId.INVALID)))
                    R.id.nav_favorites -> startActivity(Intent(this, FavoritesActivity::class.java)
                        .putExtra(EXTRA_WIDGET_ID, intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManagerId.INVALID)))
                    R.id.nav_on_this_day -> showOnThisDay()
                }
            }
            true
        }
        fabEdit.setOnClickListener { if (isEditing) leaveEditor {} else startEditing() }
        lifecycleScope.launch {
            editor.error.collect { if (it != null) toast(if (it is NoteRepository.Conflict) R.string.edit_conflict else R.string.error_saving_note) }
        }
        val initial = savedInstanceState?.getString("current_uri") ?: intent.getStringExtra(EXTRA_NOTE_URI)
        if (initial != null) loadNote(Uri.parse(initial), restoreEditor = true) else loadMore()
        if (savedInstanceState == null && intent.getBooleanExtra("action_create", false)) createDialog(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("current_uri", current()?.uri?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onPause() { super.onPause(); if (isEditing) editor.flush() }

    private fun current() = pages.getOrNull(viewPager.currentItem)
    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun startEditing() {
        val note = current() ?: return
        if (navigationPending) return
        pagesJob?.cancel()
        loading = false
        navigationPending = true
        viewPager.isUserInputEnabled = false
        lifecycleScope.launch {
            try {
                val writable = withContext(Dispatchers.IO) { DocumentFile.fromSingleUri(this@NoteViewerActivity, note.uri)?.canWrite() == true }
                if (!writable) { toast(R.string.read_only_note); return@launch }
                editor.open(note.uri.toString(), note.rawContent)
                isEditing = true
                viewPager.isUserInputEnabled = false
                fabEdit.setImageResource(R.drawable.ic_chevron_right)
                adapter.notifyItemChanged(viewPager.currentItem)
                refreshTitle()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { toast(R.string.error_saving_note) }
            finally { navigationPending = false; viewPager.isUserInputEnabled = !isEditing; setEditorEnabled(true) }
        }
    }

    private fun leaveEditor(action: () -> Unit) {
        if (navigationPending) return
        if (!isEditing) { action(); return }
        navigationPending = true
        setEditorEnabled(false)
        lifecycleScope.launch {
            try {
            if (editor.save()) {
                val session = editor.session.value
                val pos = pages.indexOfFirst { it.uri.toString() == session?.uri }
                if (pos >= 0 && session != null) {
                    val updated = withContext(Dispatchers.IO) { MarkdownFileScanner.readNoteContent(applicationContext, Uri.parse(session.uri)) }
                    if (updated != null) pages[pos] = updated
                }
                editor.close()
                isEditing = false
                viewPager.isUserInputEnabled = true
                fabEdit.setImageResource(R.drawable.ic_edit)
                if (pos >= 0) adapter.notifyItemChanged(pos)
                refreshTitle()
                action()
            }
            } finally { navigationPending = false; setEditorEnabled(true) }
        }
    }

    private fun discardEdits() {
        if (!isEditing) return
        AlertDialog.Builder(this).setMessage(R.string.discard_confirm)
            .setPositiveButton(R.string.discard_edits) { _, _ -> lifecycleScope.launch {
                val uri = current()?.uri
                editor.discard()
                isEditing = false
                viewPager.isUserInputEnabled = true
                fabEdit.setImageResource(R.drawable.ic_edit)
                refreshTitle()
                if (uri != null) loadNote(uri)
            } }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun setEditorEnabled(enabled: Boolean) {
        val recycler = viewPager.getChildAt(0) as? RecyclerView
        (recycler?.findViewHolderForAdapterPosition(viewPager.currentItem) as? NotePagerAdapter.Holder)?.edit?.isEnabled = enabled
        fabEdit.isEnabled = enabled
    }

    private fun refreshTitle() {
        val note = current() ?: return
        toolbar.title = note.title
        toolbar.menu.findItem(R.id.action_discard)?.isVisible = isEditing
        lifecycleScope.launch {
            val favorite = withContext(Dispatchers.IO) { runCatching { PreferencesManager.isFavorite(applicationContext, note.uri.toString()) }.getOrDefault(false) }
            if (current()?.uri == note.uri) toolbar.menu.findItem(R.id.action_favorite)?.apply {
                setIcon(if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star)
                setTitle(if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites)
            }
        }
    }

    private fun toggleFavorite() {
        val note = current() ?: return
        lifecycleScope.launch {
            try {
            val added = withContext(Dispatchers.IO) { PreferencesManager.toggleFavorite(applicationContext, note.uri.toString()) }
            toast(if (added) R.string.added_to_favorites else R.string.removed_from_favorites)
            refreshTitle()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { toast(R.string.storage_error) }
        }
    }

    private fun showSearch(show: Boolean) {
        isSearching = show
        searchPanel.visibility = if (show) View.VISIBLE else View.GONE
        viewPager.visibility = if (show) View.GONE else View.VISIBLE
        fabEdit.visibility = if (show) View.GONE else View.VISIBLE
        if (show) searchEdit.requestFocus() else { searchJob?.cancel(); searchEdit.setText("") }
    }

    private fun search(query: String) {
        searchJob?.cancel()
        searchRecycler.adapter = null
        searchEmpty.visibility = View.GONE
        searchProgress.visibility = if (query.length >= 2) View.VISIBLE else View.GONE
        if (query.length < 2) return
        searchJob = lifecycleScope.launch {
            delay(300)
            try {
                val found = withContext(Dispatchers.IO) {
                    val result = mutableListOf<MarkdownFileScanner.NoteContent>()
                    for (value in PreferencesManager.getCachedFileUris(settings)) {
                        ensureActive()
                        val uri = Uri.parse(value)
                        val name = DocumentFile.fromSingleUri(this@NoteViewerActivity, uri)?.name.orEmpty()
                        val matches = name.contains(query, true) || try { contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                            val chunk = CharArray(8192)
                            var tail = ""
                            var matched = false
                            while (true) {
                                ensureActive()
                                val count = reader.read(chunk)
                                if (count < 0) break
                                val text = tail + String(chunk, 0, count)
                                if (text.contains(query, true)) { matched = true; break }
                                tail = text.takeLast(query.length - 1)
                            }
                            matched
                        } == true } catch (e: java.io.FileNotFoundException) { false }
                        if (matches) MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, uri)?.let(result::add)
                    }
                    result
                }
                searchRecycler.adapter = NoteListAdapter(found, { note -> showSearch(false); loadNote(note.uri) }, query)
                searchEmpty.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { toast(R.string.error_opening_file) }
            finally { if (query == searchEdit.text.toString()) searchProgress.visibility = View.GONE }
        }
    }

    private fun loadNote(uri: Uri, restoreEditor: Boolean = false) {
        pagesJob?.cancel()
        loading = true
        pagesJob = lifecycleScope.launch {
            try {
                val note = withContext(Dispatchers.IO) { MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, uri) }
                if (note == null) { toast(R.string.error_opening_file); return@launch }
                pages.clear(); pages.add(note)
                adapter.notifyDataSetChanged()
                viewPager.setCurrentItem(0, false)
                refreshTitle()
                withContext(Dispatchers.IO) { PreferencesManager.addToNoteHistory(applicationContext, uri.toString()) }
                if (restoreEditor && (editor.session.value?.uri == uri.toString() || repository.draft(uri.toString()) != null)) startEditing()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { toast(R.string.error_opening_file) }
            finally { loading = false }
            if (!isEditing && !navigationPending && randomMode) loadMore()
        }
    }

    private fun loadMore(advance: Boolean = false) {
        if (loading || !randomMode || isEditing) return
        val folder = PreferencesManager.getFolderUri(settings) ?: return
        loading = true
        pagesJob = lifecycleScope.launch {
            try {
                val notes = withContext(Dispatchers.IO) {
                    buildList { repeat(3) {
                        ensureActive()
                        MarkdownFileScanner.getRandomFile(settings, folder)?.let { uri ->
                            MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, uri)?.let(::add)
                        }
                    } }
                }
                val previous = pages.size
                pages.addAll(notes)
                adapter.notifyItemRangeInserted(previous, notes.size)
                if (advance && notes.isNotEmpty()) viewPager.setCurrentItem(previous, true)
                // Keep a bounded navigation window instead of every full note ever visited.
                if (pages.size > 30 && viewPager.currentItem > 10) {
                    val remove = minOf(pages.size - 30, viewPager.currentItem - 5)
                    val position = viewPager.currentItem - remove
                    repeat(remove) { pages.removeAt(0) }
                    adapter.notifyDataSetChanged()
                    viewPager.setCurrentItem(position, false)
                }
                refreshTitle()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { toast(R.string.saf_permission_lost) }
            finally { loading = false }
        }
    }

    private fun showOnThisDay() {
        pagesJob?.cancel()
        randomMode = false
        loading = true
        toolbar.setTitle(R.string.on_this_day_loading)
        pagesJob = lifecycleScope.launch {
            try {
                val notes = withContext(Dispatchers.IO) {
                    val today = LocalDate.now()
                    val source = PreferencesManager.getOption(settings, "date_source", "auto")
                    PreferencesManager.getCachedFileUris(settings).mapNotNull {
                        ensureActive()
                        val note = MarkdownFileScanner.readNoteContent(this@NoteViewerActivity, Uri.parse(it)) ?: return@mapNotNull null
                        val date = NoteMetadata.date(note.rawContent, note.fileName, source, note.lastModified)
                        note.takeIf { date != null && date.year < today.year && date.month == today.month && date.dayOfMonth == today.dayOfMonth }
                    }
                }
                if (notes.isEmpty()) { toast(R.string.no_on_this_day); refreshTitle() }
                else { pages.clear(); pages.addAll(notes); adapter.notifyDataSetChanged(); viewPager.setCurrentItem(0, false); refreshTitle() }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { toast(R.string.error_opening_file); refreshTitle() }
            finally { loading = false }
        }
    }

    private fun confirmDelete() {
        val uri = current()?.uri ?: return
        AlertDialog.Builder(this).setTitle(R.string.delete_note).setMessage(R.string.delete_note_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> lifecycleScope.launch {
                try {
                    repository.trash(uri)
                    pages.removeAll { it.uri == uri }
                    adapter.notifyDataSetChanged()
                    toast(R.string.note_deleted)
                    if (pages.isEmpty()) { randomMode = true; loadMore() } else refreshTitle()
                    WidgetUpdateCoordinator.refreshAll(this@NoteViewerActivity)
                } catch (e: Exception) { toast(R.string.error_deleting_file) }
            } }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showTrash() {
        lifecycleScope.launch {
            val entries = repository.trashEntries()
            if (entries.isEmpty()) { toast(R.string.trash_empty); return@launch }
            AlertDialog.Builder(this@NoteViewerActivity).setTitle(R.string.restore_note)
                .setItems(entries.map { it.second }.toTypedArray()) { _, index -> lifecycleScope.launch {
                    val folder = PreferencesManager.getFolderUri(settings) ?: return@launch
                    try {
                        val uri = repository.restore(entries[index].first, folder)
                        withContext(Dispatchers.IO) { MarkdownFileScanner.refreshCache(settings, folder) }
                        loadNote(uri)
                    } catch (e: Exception) { toast(R.string.restore_failed) }
                } }.show()
        }
    }

    private fun createDialog(daily: Boolean) {
        val folder = PreferencesManager.getFolderUri(settings) ?: run { toast(R.string.no_folder_selected); return }
        val today = LocalDate.now().toString()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val name = EditText(this).apply { setHint(R.string.note_name_hint); setSingleLine(); if (daily) setText(today) }
        val body = EditText(this).apply {
            setHint(R.string.note_body_hint); minLines = 4; gravity = android.view.Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(PreferencesManager.getOption(settings, "note_template").replace("{{date}}", today))
        }
        box.addView(name); box.addView(body)
        val dialog = AlertDialog.Builder(this).setTitle(if (daily) R.string.daily_note else R.string.create_note)
            .setView(box).setPositiveButton(R.string.create_note, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = name.text.toString().trim()
            if (title.isBlank() || title.contains('/') || title.contains('\\') || title == "." || title == "..") {
                name.error = getString(R.string.invalid_note_name); return@setOnClickListener
            }
            val text = body.text.toString().replace("{{title}}", title)
            lifecycleScope.launch {
                try {
                    val uri = withContext(Dispatchers.IO) {
                        var destination = DocumentFile.fromTreeUri(this@NoteViewerActivity, folder) ?: throw IOException()
                        val relative = PreferencesManager.getOption(settings, "notes_subfolder")
                        for (part in relative.split('/').filter { it.isNotBlank() }) {
                            require(part != ".." && part != "." && !part.contains('\\'))
                            destination = destination.findFile(part) ?: destination.createDirectory(part) ?: throw IOException()
                        }
                        val filename = if (title.endsWith(".md", true)) title else "$title.md"
                        if (destination.findFile(filename) != null) throw IOException("Already exists")
                        val file = destination.createFile("text/markdown", filename) ?: throw IOException()
                        try {
                            (contentResolver.openOutputStream(file.uri, "wt") ?: throw IOException()).use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            if (MarkdownFileScanner.readRawContent(this@NoteViewerActivity, file.uri) != text) throw IOException()
                            file.uri
                        } catch (e: Exception) { file.delete(); throw e }
                    }
                    dialog.dismiss()
                    withContext(Dispatchers.IO) { MarkdownFileScanner.refreshCache(settings, folder) }
                    loadNote(uri)
                    toast(R.string.note_created)
                } catch (e: Exception) { toast(R.string.error_creating_note) }
            }
        } }
        dialog.show()
    }

    private fun openLink(link: String) = leaveEditor {
        val uri = Uri.parse(link)
        if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "mailto") {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { toast(R.string.error_opening_file) }
        } else lifecycleScope.launch {
            val target = Uri.decode(if (uri.scheme == "note") link.removePrefix("note:") else link).substringBefore('#').removeSuffix(".md")
            val destination = withContext(Dispatchers.IO) { MarkdownFileScanner.resolveLink(settings, current()?.uri, target, true) }
            if (destination != null) loadNote(destination) else toast(R.string.link_not_found)
        }
    }

    private inner class NotePagerAdapter : RecyclerView.Adapter<NotePagerAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tv_note_title)
            val meta: TextView = view.findViewById(R.id.tv_note_meta)
            val content: TextView = view.findViewById(R.id.tv_note_content)
            val edit: EditText = view.findViewById(R.id.edit_note_content)
            val images: LinearLayout = view.findViewById(R.id.image_container)
            val imageScroll: View = view.findViewById(R.id.image_scroll)
            var boundUri: Uri? = null
            var binding = false
            var job: Job? = null
            init { edit.doAfterTextChanged {
                if (!binding && isEditing && editor.session.value?.uri == boundUri.toString()) editor.edit(it.toString())
            } }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_note_page, parent, false))
        override fun getItemCount() = pages.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.job?.cancel()
            val note = pages[position]
            holder.boundUri = note.uri
            holder.title.text = note.title
            holder.meta.text = listOf(note.folderName, if (note.lastModified > 0) MarkdownFileScanner.dateFormat.get()!!.format(java.util.Date(note.lastModified)) else "")
                .filter { it.isNotBlank() }.joinToString(" • ")
            val editing = isEditing && editor.session.value?.uri == note.uri.toString()
            holder.binding = true
            holder.edit.setText(if (editing) editor.session.value?.text else "")
            holder.binding = false
            holder.edit.visibility = if (editing) View.VISIBLE else View.GONE
            holder.edit.isEnabled = !navigationPending
            holder.content.visibility = if (editing) View.GONE else View.VISIBLE
            holder.images.removeAllViews()
            holder.imageScroll.visibility = View.GONE
            holder.job = lifecycleScope.launch {
                val rendered = withContext(Dispatchers.Default) {
                    val markdown = MarkdownFileScanner.body(note.rawContent)
                        .replace(Regex("!\\[\\[[^]]+]]"), "")
                        .replace(Regex("\\[\\[([^]|]+)(?:\\|([^]]+))?]]")) {
                            val label = it.groupValues[2].ifEmpty { it.groupValues[1] }
                            "[$label](note:${Uri.encode(it.groupValues[1])})"
                        }
                    markwon.toMarkdown(markdown)
                }
                if (holder.boundUri != note.uri) return@launch
                markwon.setParsedMarkdown(holder.content, rendered)
                val folder = PreferencesManager.getFolderUri(settings) ?: return@launch
                for (ref in note.imageRefs.take(5)) {
                    val bitmap = withContext(Dispatchers.IO) { findImage(folder, note.uri, ref) } ?: continue
                    if (holder.boundUri != note.uri) return@launch
                    holder.images.addView(ImageView(this@NoteViewerActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(200), dp(150)).apply { marginEnd = dp(8) }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(bitmap)
                    })
                    holder.imageScroll.visibility = View.VISIBLE
                }
            }
        }
        override fun onViewRecycled(holder: Holder) { holder.job?.cancel(); holder.boundUri = null; holder.images.removeAllViews() }
    }

    private fun findImage(folder: Uri, note: Uri, ref: String): android.graphics.Bitmap? {
        val uri = MarkdownFileScanner.resolveLink(settings, note, ref, false) ?: return null
        return MarkdownFileScanner.loadBitmapDownsampled(this, uri)
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private object AppWidgetManagerId { const val INVALID = android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID }
}
