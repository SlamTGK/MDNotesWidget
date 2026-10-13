package com.mdnotes.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    internal lateinit var draft: SettingsDraft
    private lateinit var sections: LinearLayout
    private lateinit var status: TextView
    private lateinit var applyButton: MaterialButton
    private lateinit var folderLabel: TextView
    private lateinit var count: TextView
    private lateinit var excludedFolders: ChipGroup
    private lateinit var includeTags: ChipGroup
    private lateinit var excludeTags: ChipGroup
    private var catalog: SettingsCatalog? = null
    private var catalogJob: Job? = null
    private var applying = false
    private val configurationId get() = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (_: SecurityException) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    toast(R.string.read_only_folder)
                }
                if (uri != PreferencesManager.getFolderUri(draft)) {
                    PreferencesManager.setFolderUri(draft, uri)
                    PreferencesManager.setOption(draft, "excluded_folder_paths", "[]")
                    PreferencesManager.setOption(draft, "notes_subfolder", "")
                }
                folderLabel.text = folderName(uri)
                renderFolders()
                changed()
                loadCatalog()
            } catch (_: SecurityException) { toast(R.string.saf_permission_lost) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        draft = SettingsDraft(applicationContext, savedInstanceState?.getString("settings_draft"))
        setContentView(R.layout.activity_main)
        sections = findViewById(R.id.settings_sections)
        status = findViewById(R.id.settings_status)
        applyButton = findViewById(R.id.settings_apply)
        applyButton.setOnClickListener { applySettings() }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        label(sections, getString(R.string.settings), 26f)
        label(sections, getString(R.string.settings_shared_help))
        button(sections, R.string.open_notes) { leave { startActivity(Intent(this, NoteViewerActivity::class.java)); finish() } }
        buildFilters()
        buildAppearance()
        buildSchedule()
        buildOpening()
        buildCreation()
        buildAnniversary()
        label(sections, getString(R.string.author_credit_version, BuildConfig.VERSION_NAME), 12f)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave { finish() }
        })
        changed()
        loadCatalog()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("settings_draft", draft.serialize())
        super.onSaveInstanceState(outState)
    }

    private fun leave(action: () -> Unit) {
        if (applying) return
        if (!draft.dirty) { action(); return }
        MaterialAlertDialogBuilder(this).setTitle(R.string.settings_unsaved)
            .setMessage(R.string.settings_unsaved_help)
            .setPositiveButton(R.string.settings_apply) { _, _ -> applySettings(action) }
            .setNegativeButton(R.string.settings_discard) { _, _ -> action() }
            .setNeutralButton(android.R.string.cancel, null).show()
    }

    private fun buildFilters() {
        val box = section(R.string.settings_selection)
        folderLabel = label(box, folderName(PreferencesManager.getFolderUri(draft)))
        button(box, R.string.select_folder) { folderPicker.launch(PreferencesManager.getFolderUri(draft)) }
        label(box, getString(R.string.settings_folders_help))
        excludedFolders = ChipGroup(this).also(box::addView)
        renderFolders()
        button(box, R.string.settings_choose_folders) { chooseFolders() }

        label(box, getString(R.string.settings_include_tags), 16f)
        includeTags = ChipGroup(this).also(box::addView)
        button(box, R.string.settings_choose_tags) { chooseTags(false) }
        label(box, getString(R.string.settings_exclude_tags), 16f)
        excludeTags = ChipGroup(this).also(box::addView)
        button(box, R.string.settings_choose_tags) { chooseTags(true) }
        renderTags()
        label(box, getString(R.string.settings_tags_help), 12f)
        choices(box, R.string.tag_logic_label, listOf("or", "and"),
            listOf(getString(R.string.settings_any_tag), getString(R.string.settings_all_tags)), PreferencesManager.getTagLogic(draft)) {
            PreferencesManager.setTagLogic(draft, it); changed()
        }
        toggle(box, R.string.favorites_only, PreferencesManager.getOption(draft, "favorites_only") == "true") {
            PreferencesManager.setOption(draft, "favorites_only", it.toString()); changed()
        }
        count = label(box, getString(R.string.scanning_files))
        button(box, R.string.settings_show_matches) { showMatches() }
        button(box, R.string.refresh_file_list) { loadCatalog() }
    }

    private fun buildAppearance() {
        val box = section(R.string.settings_appearance, collapsed = true)
        val custom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        choices(box, R.string.theme_settings, listOf("default", "dark", "transparent", "custom"),
            listOf(R.string.theme_default, R.string.theme_dark, R.string.theme_transparent, R.string.theme_custom).map(::getString),
            PreferencesManager.getWidgetTheme(draft)) {
            PreferencesManager.setWidgetTheme(draft, it)
            custom.visibility = if (it == "custom") View.VISIBLE else View.GONE
            changed()
        }
        box.addView(custom)
        custom.visibility = if (PreferencesManager.getWidgetTheme(draft) == "custom") View.VISIBLE else View.GONE
        listOf("custom_widget_bg_color" to R.string.custom_bg_color, "custom_widget_title_color" to R.string.custom_title_color,
            "custom_widget_text_color" to R.string.custom_text_color).forEach { (key, title) ->
            button(custom, title) {
                val color = when (key) {
                    "custom_widget_bg_color" -> PreferencesManager.getCustomWidgetBgColor(draft)
                    "custom_widget_title_color" -> PreferencesManager.getCustomWidgetTitleColor(draft)
                    else -> PreferencesManager.getCustomWidgetTextColor(draft)
                }
                chooseColor(color) { draft.preferences.edit().putInt(key, it).apply(); changed() }
            }
        }
        choices(box, R.string.font_settings, listOf("small", "medium", "large"),
            listOf(R.string.font_small, R.string.font_medium, R.string.font_large).map(::getString), PreferencesManager.getFontSize(draft)) {
            PreferencesManager.setFontSize(draft, it); changed()
        }
        button(box, R.string.settings_preview) {
            val views = android.widget.RemoteViews(packageName, R.layout.widget_note)
            WidgetThemeHelper.applyTheme(draft, views, R.id.widget_root)
            WidgetThemeHelper.applyCustomTextColors(draft, views, R.id.widget_title, R.id.widget_content, R.id.widget_meta)
            NoteWidgetProvider.configureContent(views, R.id.widget_content, WidgetThemeHelper.getFontSizeSp(draft))
            views.setTextViewText(R.id.widget_title, getString(R.string.preview_title))
            views.setTextViewText(R.id.widget_content, getString(R.string.settings_preview_body))
            views.setTextViewText(R.id.widget_meta, getString(R.string.folder_settings))
            val frame = FrameLayout(this).apply { setBackgroundColor(Color.DKGRAY) }
            frame.addView(views.apply(this, frame), FrameLayout.LayoutParams(-1, dp(220)))
            MaterialAlertDialogBuilder(this).setTitle(R.string.settings_preview).setView(frame)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    private fun buildSchedule() {
        val box = section(R.string.settings_schedule, collapsed = true)
        val current = PreferencesManager.getIntervalMinutes(draft)
        val intervals = (listOf(-1, 15, 30, 60, 120, 360, 720, 1440) + current).distinct().sorted()
        choices(box, R.string.interval_settings, intervals.map(Int::toString), intervals.map {
            if (it < 0) getString(R.string.interval_off) else getString(R.string.minutes_format, it)
        }, current.toString()) { PreferencesManager.setIntervalMinutes(draft, it.toInt()); changed() }
        label(box, getString(R.string.settings_schedule_help), 12f)
        val times = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toggle(box, R.string.quiet_hours_enabled, PreferencesManager.isQuietHoursEnabled(draft)) {
            PreferencesManager.setQuietHoursEnabled(draft, it); times.visibility = if (it) View.VISIBLE else View.GONE; changed()
        }
        box.addView(times)
        times.visibility = if (PreferencesManager.isQuietHoursEnabled(draft)) View.VISIBLE else View.GONE
        listOf(true, false).forEach { start ->
            fun time() = if (start) PreferencesManager.getQuietHoursStart(draft) else PreferencesManager.getQuietHoursEnd(draft)
            val title = if (start) R.string.quiet_hours_from else R.string.quiet_hours_to
            val button = button(times, title) {}
            fun refresh() { val (h, m) = time(); button.text = "${getString(title)} %02d:%02d".format(h, m) }
            refresh()
            button.setOnClickListener {
                val (h, m) = time()
                val picker = MaterialTimePicker.Builder().setHour(h).setMinute(m).setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H).build()
                picker.addOnPositiveButtonClickListener {
                    if (start) PreferencesManager.setQuietHoursStart(draft, picker.hour, picker.minute)
                    else PreferencesManager.setQuietHoursEnd(draft, picker.hour, picker.minute)
                    refresh(); changed()
                }
                picker.show(supportFragmentManager, "quiet_time")
            }
        }
    }

    private fun buildOpening() {
        val box = section(R.string.open_with_settings, collapsed = true)
        val obsidian = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        choices(box, R.string.open_with_settings, listOf("viewer", "obsidian", "system"),
            listOf(R.string.built_in_viewer, R.string.obsidian, R.string.system_chooser).map(::getString), PreferencesManager.getOpenWith(draft)) {
            PreferencesManager.setOpenWith(draft, it); obsidian.visibility = if (it == "obsidian") View.VISIBLE else View.GONE; changed()
        }
        box.addView(obsidian)
        obsidian.visibility = if (PreferencesManager.getOpenWith(draft) == "obsidian") View.VISIBLE else View.GONE
        field(obsidian, R.string.settings_vault, "obsidian_vault", R.string.settings_vault_help)
    }

    private fun buildCreation() {
        val box = section(R.string.settings_creation, collapsed = true)
        val destination = field(box, R.string.settings_destination, "notes_subfolder", R.string.settings_destination_help)
        button(box, R.string.settings_choose_destination) {
            val data = readyCatalog() ?: return@button
            select(getString(R.string.settings_destination), listOf("" to getString(R.string.settings_root)) + data.folders.filterNot(FolderRules::isHidden).map { it to it },
                setOf(PreferencesManager.getOption(draft, "notes_subfolder")), multiple = false) {
                destination.setText(it.firstOrNull().orEmpty())
            }
        }
        field(box, R.string.settings_template, "note_template", R.string.settings_template_help, multiline = true)
        button(box, R.string.settings_template_preview) {
            val text = PreferencesManager.getOption(draft, "note_template")
                .replace("{{date}}", java.time.LocalDate.now().toString()).replace("{{title}}", getString(R.string.preview_title))
            MaterialAlertDialogBuilder(this).setTitle(R.string.settings_template_preview)
                .setMessage(text.ifBlank { getString(R.string.settings_empty_template) }).setPositiveButton(android.R.string.ok, null).show()
        }
    }

    private fun buildAnniversary() {
        val box = section(R.string.on_this_day, collapsed = true)
        label(box, getString(R.string.settings_anniversary_help))
        choices(box, R.string.date_source, listOf("auto", "yaml", "filename"), resources.getStringArray(R.array.settings_creation_dates).toList(),
            PreferencesManager.getOption(draft, "date_source", "auto")) {
            PreferencesManager.setOption(draft, "date_source", it); changed()
        }
    }

    private fun loadCatalog() {
        if (applying) return
        val folder = PreferencesManager.getFolderUri(draft) ?: return
        catalogJob?.cancel()
        catalog = null
        count.setText(R.string.scanning_files)
        catalogJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { SettingsCatalog.load(applicationContext, folder) }
                if (folder == PreferencesManager.getFolderUri(draft)) { catalog = result; changed() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { count.setText(R.string.settings_scan_failed) }
        }
    }

    private fun readyCatalog(): SettingsCatalog? {
        val data = catalog
        if (data == null) toast(if (PreferencesManager.getFolderUri(draft) == null) R.string.no_folder_selected else R.string.settings_catalog_wait)
        return data
    }

    private fun selectedFolders(): List<String> {
        val paths = JSONArray(PreferencesManager.getOption(draft, "excluded_folder_paths", "[]"))
        return (0 until paths.length()).map { paths.getString(it) }.filterNot(FolderRules::isHidden)
    }

    private fun renderFolders() {
        excludedFolders.removeAllViews()
        selectedFolders().forEach { path -> chip(excludedFolders, path) {
            PreferencesManager.setOption(draft, "excluded_folder_paths", JSONArray(selectedFolders().filterNot { it == path }).toString())
            renderFolders(); changed()
        } }
        val legacy = PreferencesManager.getFolderBlacklistRaw(draft).split(';').filter { it.isNotBlank() && !FolderRules.isHidden(it) }
        legacy.forEach { name -> chip(excludedFolders, getString(R.string.settings_legacy_folder, name)) {
            PreferencesManager.setFolderBlacklist(draft, legacy.filterNot { it == name }); renderFolders(); changed()
        } }
    }

    private fun chooseFolders() {
        val data = readyCatalog() ?: return
        val paths = (data.folders + selectedFolders()).filterNot(FolderRules::isHidden).distinct().sorted()
        select(getString(R.string.settings_choose_folders), paths.map { path ->
            val descendants = data.notes.count { it.path.startsWith("$path/") }
            path to "$path ($descendants)"
        }, selectedFolders().toSet()) {
            // Child selections are redundant when their parent is excluded.
            val compact = it.filter { path -> it.none { parent -> path != parent && path.startsWith("$parent/") } }
            PreferencesManager.setOption(draft, "excluded_folder_paths", JSONArray(compact).toString()); renderFolders(); changed()
        }
    }

    private fun chooseTags(exclude: Boolean) {
        val data = readyCatalog() ?: return
        val selected = if (exclude) PreferencesManager.getExcludedTags(draft) else PreferencesManager.getTagList(draft)
        val counts = data.available(draft).flatMap { it.tags }.groupingBy { it }.eachCount()
        val tags = (counts.keys + selected).sorted()
        select(getString(if (exclude) R.string.settings_exclude_tags else R.string.settings_include_tags),
            tags.map { it to "#$it (${counts[it] ?: 0})" }, selected.toSet()) {
            if (exclude) PreferencesManager.setOption(draft, "excluded_tags", it.joinToString(","))
            else PreferencesManager.setTagList(draft, it.toList())
            renderTags(); changed()
        }
    }

    private fun renderTags() {
        includeTags.removeAllViews(); excludeTags.removeAllViews()
        PreferencesManager.getTagList(draft).forEach { tag -> chip(includeTags, "#$tag") {
            PreferencesManager.setTagList(draft, PreferencesManager.getTagList(draft).filterNot { it == tag }); renderTags(); changed()
        } }
        PreferencesManager.getExcludedTags(draft).forEach { tag -> chip(excludeTags, "#$tag") {
            PreferencesManager.setOption(draft, "excluded_tags", PreferencesManager.getExcludedTags(draft).filterNot { it == tag }.joinToString(",")); renderTags(); changed()
        } }
    }

    private fun showMatches() {
        val data = readyCatalog() ?: return
        val selection = data.selection(draft)
        val matches = selection.matches
        val list = ListView(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, matches.map { it.path })
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.settings_match_count, matches.size, selection.available))
            .setView(list).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun changed() {
        status.setText(if (draft.dirty) R.string.settings_unsaved else R.string.settings_saved)
        applyButton.isEnabled = !applying && PreferencesManager.getFolderUri(draft) != null
        if (::count.isInitialized && PreferencesManager.getFolderUri(draft) == null) count.setText(R.string.no_folder_selected)
        catalog?.let { data ->
            val selection = data.selection(draft)
            val matches = selection.matches
            count.text = getString(R.string.settings_match_count, matches.size, selection.available) +
                "\n" + getString(R.string.settings_folder_count, selection.excludedByFolders, selection.total) +
                if (matches.isEmpty()) "\n" + getString(R.string.settings_no_matches_help) else ""
        }
    }

    private fun applySettings(after: (() -> Unit)? = null) {
        if (applying) return
        val folder = PreferencesManager.getFolderUri(draft) ?: run { toast(R.string.no_folder_selected); return }
        val destination = PreferencesManager.getOption(draft, "notes_subfolder").trim()
        if (destination.startsWith('/') || destination.contains('\\') || destination.split('/').any { it == "." || it == ".." }) {
            toast(R.string.settings_bad_destination); return
        }
        PreferencesManager.setOption(draft, "notes_subfolder", destination)
        applying = true
        applyButton.isEnabled = false
        setEditingEnabled(sections, false)
        status.setText(R.string.settings_applying)
        val snapshot = SettingsDraft(applicationContext, draft.serialize())
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { MarkdownFileScanner.refreshCache(snapshot, folder) }
                val filtersChanged = listOf("folder_uri", "folder_blacklist", "excluded_folder_paths", "tag_filter", "tag_logic", "excluded_tags", "favorites_only")
                    .any { PreferencesManager.settings(applicationContext)[it] != PreferencesManager.settings(snapshot)[it] }
                withContext(Dispatchers.IO) { WidgetUpdateCoordinator.applySettings(applicationContext, snapshot, filtersChanged) }
                draft = SettingsDraft(applicationContext)
                if (configurationId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configurationId))
                }
                toast(R.string.settings_applied)
                after?.invoke()
                if (after == null && intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) finish()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { toast(R.string.settings_apply_failed) }
            finally { applying = false; setEditingEnabled(sections, true); changed() }
        }
    }

    /** Search and selection are independent, so filtering never loses checked rows. */
    private fun select(title: String, items: List<Pair<String, String>>, initial: Set<String>, multiple: Boolean = true, done: (Set<String>) -> Unit) {
        val selected = initial.toMutableSet()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val search = TextInputEditText(this).apply { hint = getString(R.string.settings_search); inputType = InputType.TYPE_CLASS_TEXT }
        box.addView(search)
        val summary = label(box, "")
        val list = ListView(this).apply { choiceMode = if (multiple) ListView.CHOICE_MODE_MULTIPLE else ListView.CHOICE_MODE_SINGLE }
        box.addView(list, LinearLayout.LayoutParams(-1, dp(300)))
        var visible = items
        fun refresh() {
            val query = search.text.toString().trim()
            visible = items.filter { it.second.contains(query, ignoreCase = true) }
            list.adapter = ArrayAdapter(this, if (multiple) android.R.layout.simple_list_item_multiple_choice else android.R.layout.simple_list_item_single_choice, visible.map { it.second })
            list.clearChoices()
            visible.forEachIndexed { index, pair -> list.setItemChecked(index, pair.first in selected) }
            summary.text = if (visible.isEmpty()) getString(R.string.no_results) else getString(R.string.settings_selected_count, selected.size)
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val key = visible[position].first
            if (!multiple) selected.clear()
            if (list.isItemChecked(position)) selected.add(key) else selected.remove(key)
            summary.text = getString(R.string.settings_selected_count, selected.size)
        }
        search.doAfterTextChanged { refresh() }
        refresh()
        val dialog = MaterialAlertDialogBuilder(this).setTitle(title).setView(box)
            .setPositiveButton(R.string.settings_select) { _, _ -> done(selected.toSet()) }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.settings_clear, null).create()
        dialog.setOnShowListener { dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { selected.clear(); refresh() } }
        dialog.show()
    }

    private fun chooseColor(initial: Int, done: (Int) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val preview = View(this)
        box.addView(preview, LinearLayout.LayoutParams(-1, dp(48)))
        val edit = TextInputEditText(this).apply { setText("#%08X".format(initial)); hint = "#AARRGGBB" }
        box.addView(edit)
        val grid = GridLayout(this).apply { columnCount = 5 }
        box.addView(grid)
        listOf("#FF0D1117", "#FF1E1B4B", "#FF1E3A5F", "#FF1B4332", "#FF3B1F2B", "#FFFFFFFF", "#FFF8FAFC", "#FFB0BEC5", "#FF6366F1", "#FF10B981", "#FFF59E0B", "#FFEF4444", "#FFEC4899", "#FF06B6D4", "#00000000").forEach { hex ->
            val swatch = View(this).apply {
                setBackgroundColor(Color.parseColor(hex)); contentDescription = hex
                setOnClickListener { edit.setText(hex) }
            }
            grid.addView(swatch, GridLayout.LayoutParams().apply { width = dp(48); height = dp(48); setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        preview.setBackgroundColor(initial)
        edit.doAfterTextChanged { runCatching { Color.parseColor(it.toString()) }.onSuccess { preview.setBackgroundColor(it) } }
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.pick_color).setView(box)
            .setPositiveButton(R.string.settings_select, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener { dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            runCatching { Color.parseColor(edit.text.toString()) }.onSuccess { done(it); dialog.dismiss() }
                .onFailure { edit.error = getString(R.string.settings_bad_color) }
        } }
        dialog.show()
    }

    private fun section(title: Int, collapsed: Boolean = false): LinearLayout {
        val card = MaterialCardView(this).apply { radius = dp(16).toFloat(); cardElevation = 0f }
        sections.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(16)) }
        card.addView(outer)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (collapsed) View.GONE else View.VISIBLE }
        val header = button(outer, title) {}
        fun headerText() { header.text = getString(title) + if (body.visibility == View.VISIBLE) "  ▴" else "  ▾" }
        headerText()
        header.setOnClickListener { body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE; headerText() }
        outer.addView(body)
        return body
    }

    private fun label(parent: LinearLayout, text: String, size: Float = 14f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setPadding(0, dp(6), 0, dp(6)); parent.addView(this)
    }
    private fun button(parent: LinearLayout, title: Int, action: () -> Unit): MaterialButton = MaterialButton(this, null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        setText(title); minHeight = dp(48); setOnClickListener { action() }; parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun chip(parent: ChipGroup, title: String, remove: () -> Unit) {
        parent.addView(Chip(this).apply { text = title; isCloseIconVisible = true; setOnCloseIconClickListener { remove() } })
    }
    private fun toggle(parent: LinearLayout, title: Int, checked: Boolean, changed: (Boolean) -> Unit) {
        parent.addView(MaterialSwitch(this).apply { setText(title); minHeight = dp(48); isChecked = checked; setOnCheckedChangeListener { _, value -> changed(value) } })
    }
    private fun choices(parent: LinearLayout, title: Int, values: List<String>, labels: List<String>, value: String, changed: (String) -> Unit) {
        val button = button(parent, title) {}
        var selected = values.indexOf(value).coerceAtLeast(0)
        fun refresh() { button.text = getString(title) + "\n" + labels[selected] }
        refresh()
        button.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(title).setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, index ->
                selected = index; changed(values[index]); refresh(); dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
        }
    }
    private fun field(parent: LinearLayout, title: Int, key: String, help: Int, multiline: Boolean = false): TextInputEditText {
        val layout = TextInputLayout(this).apply { hint = getString(title); helperText = getString(help); setPadding(0, dp(8), 0, dp(8)) }
        val edit = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            minLines = if (multiline) 4 else 1
            setText(PreferencesManager.getOption(draft, key))
            doAfterTextChanged { PreferencesManager.setOption(draft, key, it.toString()); changed() }
        }
        layout.addView(edit); parent.addView(layout)
        return edit
    }
    private fun setEditingEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) for (i in 0 until view.childCount) setEditingEnabled(view.getChildAt(i), enabled)
    }
    private fun folderName(uri: Uri?) = uri?.lastPathSegment?.replace("primary:", "/") ?: getString(R.string.no_folder_selected)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
