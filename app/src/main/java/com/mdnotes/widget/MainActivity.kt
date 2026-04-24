package com.mdnotes.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_FOLDER = 1001
        private val INTERVAL_VALUES = intArrayOf(
            -1, 15, 30, 45, 60, 90, 120, 180, 240, 300, 360, 420,
            480, 540, 600, 660, 720, 780, 840, 900, 960, 1020, 1080, 1140, 1200, 1440
        )
    }

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var tvFolderPath: TextView
    private lateinit var tvFileCount: TextView
    private lateinit var btnSelectFolder: MaterialButton
    private lateinit var seekInterval: SeekBar
    private lateinit var tvIntervalValue: TextView
    private lateinit var radioGroupOpenWith: RadioGroup
    private lateinit var radioGroupTheme: RadioGroup
    private lateinit var radioGroupFont: RadioGroup
    private lateinit var chipGroupTags: ChipGroup
    private lateinit var editTagInput: EditText
    private lateinit var btnAddTag: MaterialButton
    private lateinit var editFolderBlacklist: EditText
    private lateinit var switchQuietHours: MaterialSwitch
    private lateinit var quietHoursPanel: LinearLayout
    private lateinit var btnQuietStart: MaterialButton
    private lateinit var btnQuietEnd: MaterialButton
    private lateinit var customColorsPanel: LinearLayout
    private lateinit var colorPreviewBg: View
    private lateinit var colorPreviewText: View
    private lateinit var colorPreviewTitle: View
    private lateinit var scanningIndicator: View
    private lateinit var tvScanningStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        tvFolderPath = findViewById(R.id.tv_folder_path)
        tvFileCount = findViewById(R.id.tv_file_count)
        btnSelectFolder = findViewById(R.id.btn_select_folder)
        seekInterval = findViewById(R.id.seekbar_interval)
        tvIntervalValue = findViewById(R.id.tv_interval_value)
        radioGroupOpenWith = findViewById(R.id.radio_group_open_with)
        radioGroupTheme = findViewById(R.id.radio_group_theme)
        radioGroupFont = findViewById(R.id.radio_group_font)
        chipGroupTags = findViewById(R.id.chip_group_tags)
        editTagInput = findViewById(R.id.edit_tag_input)
        btnAddTag = findViewById(R.id.btn_add_tag)
        editFolderBlacklist = findViewById(R.id.edit_folder_blacklist)
        switchQuietHours = findViewById(R.id.switch_quiet_hours)
        quietHoursPanel = findViewById(R.id.quiet_hours_time_panel)
        btnQuietStart = findViewById(R.id.btn_quiet_start)
        btnQuietEnd = findViewById(R.id.btn_quiet_end)
        customColorsPanel = findViewById(R.id.custom_colors_panel)
        colorPreviewBg = findViewById(R.id.color_preview_bg)
        colorPreviewText = findViewById(R.id.color_preview_text)
        colorPreviewTitle = findViewById(R.id.color_preview_title)
        scanningIndicator = findViewById(R.id.scanning_indicator)
        tvScanningStatus = findViewById(R.id.tv_scanning_status)

        // Setup sections
        setupFolderSection()
        setupIntervalSection()
        setupOpenWithSection()
        setupThemeSection()
        setupFontSection()
        setupTagFilterSection()
        setupBlacklistSection()
        setupQuietHoursSection()
        setupCustomColorsSection()
        setupActionButtons()
    }

    // ── Folder ────────────────────────────────────────────────────────────────

    private fun setupFolderSection() {
        btnSelectFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, REQUEST_FOLDER)
        }

        val folderUri = PreferencesManager.getFolderUri(this)
        if (folderUri != null) {
            val path = folderUri.lastPathSegment?.replace("primary:", "/")
                ?: folderUri.toString()
            tvFolderPath.text = path
            val count = PreferencesManager.getCachedFileUris(this).size
            updateFileCountBadge(count)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FOLDER && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            PreferencesManager.setFolderUri(this, uri)
            val path = uri.lastPathSegment?.replace("primary:", "/") ?: uri.toString()
            tvFolderPath.text = path
            // Auto-scan
            triggerRefreshFiles()
        }
    }

    // ── Interval ──────────────────────────────────────────────────────────────

    private fun setupIntervalSection() {
        val current = PreferencesManager.getIntervalMinutes(this)
        val idx = INTERVAL_VALUES.indexOf(current).coerceAtLeast(0)
        seekInterval.max = INTERVAL_VALUES.size - 1
        seekInterval.progress = idx
        tvIntervalValue.text = formatInterval(current)

        seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                val minutes = INTERVAL_VALUES[progress]
                tvIntervalValue.text = formatInterval(minutes)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {
                val minutes = INTERVAL_VALUES[s.progress]
                PreferencesManager.setIntervalMinutes(this@MainActivity, minutes)
                NoteWidgetProvider.scheduleWork(this@MainActivity)
            }
        })
    }

    private fun formatInterval(minutes: Int): String {
        return when {
            minutes < 0 -> getString(R.string.interval_off)
            minutes < 60 -> "${minutes}m"
            else -> {
                val hours = minutes / 60
                resources.getQuantityString(R.plurals.hours_format, hours, hours)
            }
        }
    }

    // ── Open With ─────────────────────────────────────────────────────────────

    private fun setupOpenWithSection() {
        when (PreferencesManager.getOpenWith(this)) {
            PreferencesManager.OPEN_WITH_OBSIDIAN -> findViewById<RadioButton>(R.id.radio_obsidian).isChecked = true
            PreferencesManager.OPEN_WITH_SYSTEM -> findViewById<RadioButton>(R.id.radio_system).isChecked = true
            else -> findViewById<RadioButton>(R.id.radio_viewer).isChecked = true
        }
        radioGroupOpenWith.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_obsidian -> PreferencesManager.OPEN_WITH_OBSIDIAN
                R.id.radio_system -> PreferencesManager.OPEN_WITH_SYSTEM
                else -> PreferencesManager.OPEN_WITH_VIEWER
            }
            PreferencesManager.setOpenWith(this, value)
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private fun setupThemeSection() {
        when (PreferencesManager.getWidgetTheme(this)) {
            PreferencesManager.THEME_DARK -> findViewById<RadioButton>(R.id.radio_theme_dark).isChecked = true
            PreferencesManager.THEME_TRANSPARENT -> findViewById<RadioButton>(R.id.radio_theme_transparent).isChecked = true
            PreferencesManager.THEME_CUSTOM -> {
                findViewById<RadioButton>(R.id.radio_theme_custom).isChecked = true
                customColorsPanel.visibility = View.VISIBLE
            }
            else -> findViewById<RadioButton>(R.id.radio_theme_default).isChecked = true
        }
        radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radio_theme_dark -> PreferencesManager.THEME_DARK
                R.id.radio_theme_transparent -> PreferencesManager.THEME_TRANSPARENT
                R.id.radio_theme_custom -> PreferencesManager.THEME_CUSTOM
                else -> PreferencesManager.THEME_DEFAULT
            }
            PreferencesManager.setWidgetTheme(this, theme)
            customColorsPanel.visibility = if (theme == PreferencesManager.THEME_CUSTOM) View.VISIBLE else View.GONE
        }
    }

    // ── Font Size ─────────────────────────────────────────────────────────────

    private fun setupFontSection() {
        when (PreferencesManager.getFontSize(this)) {
            PreferencesManager.FONT_SIZE_SMALL -> findViewById<RadioButton>(R.id.radio_font_small).isChecked = true
            PreferencesManager.FONT_SIZE_LARGE -> findViewById<RadioButton>(R.id.radio_font_large).isChecked = true
            else -> findViewById<RadioButton>(R.id.radio_font_medium).isChecked = true
        }
        radioGroupFont.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.radio_font_small -> PreferencesManager.FONT_SIZE_SMALL
                R.id.radio_font_large -> PreferencesManager.FONT_SIZE_LARGE
                else -> PreferencesManager.FONT_SIZE_MEDIUM
            }
            PreferencesManager.setFontSize(this, size)
        }
    }

    // ── Tag Filter (ChipGroup) ────────────────────────────────────────────────

    private fun setupTagFilterSection() {
        // Load existing tags as chips
        val tags = PreferencesManager.getTagList(this)
        chipGroupTags.removeAllViews()
        for (tag in tags) {
            addTagChip(tag)
        }

        // Add tag on button click or enter
        btnAddTag.setOnClickListener { addTagFromInput() }
        editTagInput.setOnEditorActionListener { _, _, _ ->
            addTagFromInput()
            true
        }

        // Load known tags for suggestions
        loadTagSuggestions()
    }

    private fun addTagFromInput() {
        val raw = editTagInput.text.toString().trim()
        if (raw.isEmpty()) return

        // Support comma-separated input
        val newTags = raw.split(",", ";")
            .map { it.trim().removePrefix("#").trim() }
            .filter { it.isNotEmpty() }

        for (tag in newTags) {
            addTagChip(tag)
        }
        editTagInput.text?.clear()
        saveTagsFromChips()
    }

    private fun addTagChip(tag: String) {
        // Don't add duplicate chips
        val normalizedTag = tag.removePrefix("#").trim().lowercase()
        for (i in 0 until chipGroupTags.childCount) {
            val existing = chipGroupTags.getChildAt(i) as? Chip ?: continue
            if (existing.text.toString().removePrefix("#").trim().lowercase() == normalizedTag) return
        }

        val chip = Chip(this).apply {
            text = "#$normalizedTag"
            isCloseIconVisible = true
            isCheckable = false
            setOnCloseIconClickListener {
                chipGroupTags.removeView(this)
                saveTagsFromChips()
            }
        }
        chipGroupTags.addView(chip)
    }

    private fun saveTagsFromChips() {
        val tags = mutableListOf<String>()
        for (i in 0 until chipGroupTags.childCount) {
            val chip = chipGroupTags.getChildAt(i) as? Chip ?: continue
            tags.add(chip.text.toString().removePrefix("#").trim())
        }
        PreferencesManager.setTagList(this, tags)
        // Update the filtered file count badge
        updateFilteredCount()
    }

    private fun loadTagSuggestions() {
        lifecycleScope.launch {
            val knownTags = withContext(Dispatchers.IO) {
                TagIndexManager.getAllKnownTags(this@MainActivity)
            }
            // Populate suggestion chips or autocomplete
            // For now, show a subtitle with available tags
            if (knownTags.isNotEmpty()) {
                val hint = knownTags.take(10).joinToString(", ") { "#$it" }
                editTagInput.hint = hint
            }
        }
    }

    // ── Blacklist ─────────────────────────────────────────────────────────────

    private fun setupBlacklistSection() {
        editFolderBlacklist.setText(
            PreferencesManager.getFolderBlacklist(this).joinToString("; ")
        )
    }

    // ── Quiet Hours ───────────────────────────────────────────────────────────

    private fun setupQuietHoursSection() {
        val enabled = PreferencesManager.isQuietHoursEnabled(this)
        switchQuietHours.isChecked = enabled
        quietHoursPanel.visibility = if (enabled) View.VISIBLE else View.GONE

        val (startH, startM) = PreferencesManager.getQuietHoursStart(this)
        val (endH, endM) = PreferencesManager.getQuietHoursEnd(this)
        btnQuietStart.text = String.format("%02d:%02d", startH, startM)
        btnQuietEnd.text = String.format("%02d:%02d", endH, endM)

        switchQuietHours.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setQuietHoursEnabled(this, isChecked)
            quietHoursPanel.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnQuietStart.setOnClickListener {
            showTimePicker(startH, startM) { h, m ->
                PreferencesManager.setQuietHoursStart(this, h, m)
                btnQuietStart.text = String.format("%02d:%02d", h, m)
            }
        }

        btnQuietEnd.setOnClickListener {
            showTimePicker(endH, endM) { h, m ->
                PreferencesManager.setQuietHoursEnd(this, h, m)
                btnQuietEnd.text = String.format("%02d:%02d", h, m)
            }
        }
    }

    private fun showTimePicker(currentH: Int, currentM: Int, onSet: (Int, Int) -> Unit) {
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setHour(currentH)
            .setMinute(currentM)
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .build()
        picker.addOnPositiveButtonClickListener {
            onSet(picker.hour, picker.minute)
        }
        picker.show(supportFragmentManager, "timePicker")
    }

    // ── Custom Colors ─────────────────────────────────────────────────────────

    private fun setupCustomColorsSection() {
        val bgColor = PreferencesManager.getCustomWidgetBgColor(this)
        val textColor = PreferencesManager.getCustomWidgetTextColor(this)
        val titleColor = PreferencesManager.getCustomWidgetTitleColor(this)

        colorPreviewBg.setBackgroundColor(bgColor)
        colorPreviewText.setBackgroundColor(textColor)
        colorPreviewTitle.setBackgroundColor(titleColor)

        findViewById<MaterialButton>(R.id.btn_pick_bg_color).setOnClickListener {
            showColorPicker(bgColor) { color ->
                PreferencesManager.setCustomWidgetBgColor(this, color)
                colorPreviewBg.setBackgroundColor(color)
            }
        }
        findViewById<MaterialButton>(R.id.btn_pick_text_color).setOnClickListener {
            showColorPicker(textColor) { color ->
                PreferencesManager.setCustomWidgetTextColor(this, color)
                colorPreviewText.setBackgroundColor(color)
            }
        }
        findViewById<MaterialButton>(R.id.btn_pick_title_color).setOnClickListener {
            showColorPicker(titleColor) { color ->
                PreferencesManager.setCustomWidgetTitleColor(this, color)
                colorPreviewTitle.setBackgroundColor(color)
            }
        }
    }

    private fun showColorPicker(currentColor: Int, onColorSelected: (Int) -> Unit) {
        val hexInput = EditText(this).apply {
            setText(String.format("#%06X", 0xFFFFFF and currentColor))
            setSingleLine(true)
            setPadding(48, 16, 48, 16)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pick_color)
            .setView(hexInput)
            .setPositiveButton("OK") { _, _ ->
                try {
                    val color = android.graphics.Color.parseColor(hexInput.text.toString().trim())
                    onColorSelected(color)
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid color", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Action Buttons ────────────────────────────────────────────────────────

    private fun setupActionButtons() {
        findViewById<MaterialButton>(R.id.btn_refresh_widgets).setOnClickListener {
            saveAllSettings()
            triggerRefreshWidgets()
        }
        findViewById<MaterialButton>(R.id.btn_refresh_files).setOnClickListener {
            saveAllSettings()
            triggerRefreshFiles()
        }
    }

    private fun triggerRefreshWidgets() {
        val manager = AppWidgetManager.getInstance(this)

        // Single-note widgets
        val singleIds = manager.getAppWidgetIds(
            ComponentName(this, NoteWidgetProvider::class.java)
        )
        for (id in singleIds) {
            NoteWidgetProvider.updateWidget(this, manager, id)
        }

        // List widgets
        NoteListWidgetProvider.updateAllListWidgets(this)

        NoteWidgetProvider.scheduleWork(this)
        Toast.makeText(this, R.string.widget_updated, Toast.LENGTH_SHORT).show()
    }

    private fun triggerRefreshFiles() {
        val folderUri = PreferencesManager.getFolderUri(this) ?: return

        scanningIndicator.visibility = View.VISIBLE
        tvScanningStatus.text = getString(R.string.scanning_files)

        lifecycleScope.launch {
            val (totalCount, filteredCount) = withContext(Dispatchers.IO) {
                MarkdownFileScanner.refreshCacheWithProgress(this@MainActivity, folderUri) { phase, current, total ->
                    if (phase == "index" && total > 0) {
                        launch(Dispatchers.Main) {
                            tvScanningStatus.text = getString(R.string.indexing_tags, current, total)
                        }
                    }
                }
            }

            scanningIndicator.visibility = View.GONE

            // Show filtered count
            val tags = PreferencesManager.getTagList(this@MainActivity)
            if (tags.isNotEmpty()) {
                tvFileCount.text = getString(R.string.files_filtered, filteredCount, totalCount,
                    tags.joinToString(", ") { "#$it" })
            } else {
                tvFileCount.text = getString(R.string.file_count, totalCount)
            }
            tvFileCount.visibility = View.VISIBLE

            Toast.makeText(
                this@MainActivity,
                getString(R.string.files_refreshed, totalCount),
                Toast.LENGTH_SHORT
            ).show()

            // Load tag suggestions for chips
            loadTagSuggestions()

            // Also refresh widgets
            triggerRefreshWidgets()
        }
    }

    private fun updateFilteredCount() {
        lifecycleScope.launch {
            val tags = PreferencesManager.getTagList(this@MainActivity)
            val logic = PreferencesManager.getTagLogic(this@MainActivity)
            val totalCount = PreferencesManager.getCachedFileUris(this@MainActivity).size

            if (tags.isNotEmpty() && totalCount > 0) {
                val filteredCount = withContext(Dispatchers.IO) {
                    TagIndexManager.getFilteredCount(this@MainActivity, tags, logic)
                }
                tvFileCount.text = getString(R.string.files_filtered, filteredCount, totalCount,
                    tags.joinToString(", ") { "#$it" })
            } else if (totalCount > 0) {
                tvFileCount.text = getString(R.string.file_count, totalCount)
            }
        }
    }

    private fun updateFileCountBadge(count: Int) {
        tvFileCount.text = getString(R.string.file_count, count)
        tvFileCount.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    // ── Save/restore ──────────────────────────────────────────────────────────

    private fun saveAllSettings() {
        saveTagsFromChips()
        // Save blacklist
        val blacklist = editFolderBlacklist.text.toString()
        PreferencesManager.setFolderBlacklist(this, blacklist.split(";", ",").map { it.trim() }.filter { it.isNotEmpty() })
    }

    override fun onPause() {
        super.onPause()
        saveAllSettings()
    }

    // ── Widget configuration result ───────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        // If opened from widget config intent, set result
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultIntent)
        }
    }
}
