package com.mdnotes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    val settingsWidgetId: Int get() = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    private var scanJob: kotlinx.coroutines.Job? = null
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Toast.makeText(this, R.string.read_only_folder, Toast.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                Toast.makeText(this, R.string.saf_permission_lost, Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
        }
        scanJob?.cancel()
        PreferencesManager.setFolderUri(this, uri)
        tvFolderPath.text = uri.lastPathSegment?.replace("primary:", "/") ?: uri.toString()
        triggerRefreshFiles()
    }

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
        setupAdvancedSettings()
        findViewById<TextView>(R.id.tv_author_credit).text = getString(R.string.author_credit_version, BuildConfig.VERSION_NAME)
        findViewById<TextView>(R.id.tv_settings_scope).text = if (settingsWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
            getString(R.string.settings_defaults) else getString(R.string.settings_widget, settingsWidgetId)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    // ── Folder ────────────────────────────────────────────────────────────────

    private fun setupFolderSection() {
        btnSelectFolder.setOnClickListener {
            folderPicker.launch(null)
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
            minutes < 60 -> getString(R.string.minutes_format, minutes)
            minutes % 60 != 0 -> getString(R.string.hours_minutes_format, minutes / 60, minutes % 60)
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
            PreferencesManager.getFolderBlacklistRaw(this)
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
            val (h, m) = PreferencesManager.getQuietHoursStart(this)
            showTimePicker(h, m) { h, m ->
                PreferencesManager.setQuietHoursStart(this, h, m)
                btnQuietStart.text = String.format("%02d:%02d", h, m)
            }
        }

        btnQuietEnd.setOnClickListener {
            val (h, m) = PreferencesManager.getQuietHoursEnd(this)
            showTimePicker(h, m) { h, m ->
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
            showColorPicker(PreferencesManager.getCustomWidgetBgColor(this)) { color ->
                PreferencesManager.setCustomWidgetBgColor(this, color)
                colorPreviewBg.setBackgroundColor(color)
            }
        }
        findViewById<MaterialButton>(R.id.btn_pick_text_color).setOnClickListener {
            showColorPicker(PreferencesManager.getCustomWidgetTextColor(this)) { color ->
                PreferencesManager.setCustomWidgetTextColor(this, color)
                colorPreviewText.setBackgroundColor(color)
            }
        }
        findViewById<MaterialButton>(R.id.btn_pick_title_color).setOnClickListener {
            showColorPicker(PreferencesManager.getCustomWidgetTitleColor(this)) { color ->
                PreferencesManager.setCustomWidgetTitleColor(this, color)
                colorPreviewTitle.setBackgroundColor(color)
            }
        }
    }

    private fun showColorPicker(currentColor: Int, onColorSelected: (Int) -> Unit) {
        // Preset palette: dark backgrounds, light texts, accents
        val presets = listOf(
            // Dark backgrounds
            0xFF0D1117.toInt(), 0xFF161B22.toInt(), 0xFF1C1C1E.toInt(), 0xFF1E1E2E.toInt(),
            0xFF1E1B4B.toInt(), 0xFF0F172A.toInt(), 0xFF1A1A2E.toInt(), 0xFF0A0A0F.toInt(),
            // Coloured backgrounds
            0xFF1E3A5F.toInt(), 0xFF1B4332.toInt(), 0xFF3B1F2B.toInt(), 0xFF2D1B69.toInt(),
            0xFF1A2744.toInt(), 0xFF2C1810.toInt(), 0xFF0C3547.toInt(), 0xFF1F2937.toInt(),
            // Light / neutral
            0xFFFFFFFF.toInt(), 0xFFF8FAFC.toInt(), 0xFFF1F5F9.toInt(), 0xFFE2E8F0.toInt(),
            0xFFF9FAFB.toInt(), 0xFFEFF6FF.toInt(), 0xFFF0FDF4.toInt(), 0xFFFDF2F8.toInt(),
            // Accent colours
            0xFF6366F1.toInt(), 0xFF8B5CF6.toInt(), 0xFF3B82F6.toInt(), 0xFF10B981.toInt(),
            0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0xFFEC4899.toInt(), 0xFF06B6D4.toInt()
        )

        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val currentPreview = dialogView.findViewById<View>(R.id.color_current_preview)
        val currentHexLabel = dialogView.findViewById<TextView>(R.id.color_current_hex)
        val gridLayout = dialogView.findViewById<GridLayout>(R.id.color_preset_grid)
        val hexInput = dialogView.findViewById<TextInputEditText>(R.id.color_hex_input)

        var selectedColor = currentColor

        fun updatePreview(color: Int) {
            selectedColor = color
            val d = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(color)
                setStroke(2, Color.parseColor("#33FFFFFF"))
            }
            currentPreview.background = d
            currentHexLabel.text = String.format("#%08X", color)
        }

        updatePreview(currentColor)
        hexInput.setText(String.format("#%08X", currentColor))

        // Build preset color swatches
        val density = resources.displayMetrics.density
        val swatchSize = (36 * density).toInt()
        val swatchMargin = (3 * density).toInt()

        presets.forEachIndexed { index, preset ->
            val swatch = View(this).apply {
                val lp = GridLayout.LayoutParams().apply {
                    width = swatchSize
                    height = swatchSize
                    setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin)
                }
                layoutParams = lp
                val d = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f
                    setColor(preset)
                    setStroke(
                        if (preset == currentColor) 4 else 1,
                        if (preset == currentColor) Color.parseColor("#6366F1")
                        else Color.parseColor("#44FFFFFF")
                    )
                }
                background = d
                setOnClickListener {
                    updatePreview(preset)
                    hexInput.setText(String.format("#%08X", preset))
                    // Refresh all swatch borders
                    for (i in 0 until gridLayout.childCount) {
                        val child = gridLayout.getChildAt(i)
                        val isSelected = i == index
                        val cd = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 10f
                            setColor(presets[i])
                            setStroke(
                                if (isSelected) 4 else 1,
                                if (isSelected) Color.parseColor("#6366F1")
                                else Color.parseColor("#44FFFFFF")
                            )
                        }
                        child.background = cd
                    }
                }
            }
            gridLayout.addView(swatch)
        }

        // Live hex input -> update preview
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val hex = s?.toString()?.trim() ?: return
                if (hex.length == 7 || hex.length == 9) {
                    try {
                        updatePreview(Color.parseColor(hex))
                    } catch (_: Exception) {}
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pick_color)
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                // Prefer hex field if user typed something valid
                val hexStr = hexInput.text?.toString()?.trim() ?: ""
                val finalColor = try {
                    if (hexStr.startsWith("#")) Color.parseColor(hexStr) else selectedColor
                } catch (_: Exception) { selectedColor }
                onColorSelected(finalColor)
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
        if (settingsWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) WidgetUpdateCoordinator.refreshAll(this)
        else WidgetUpdateCoordinator.refresh(this, settingsWidgetId)
        NoteWidgetProvider.scheduleWork(this)
        Toast.makeText(this, R.string.widget_updated, Toast.LENGTH_SHORT).show()
    }

    private fun triggerRefreshFiles() {
        if (scanJob?.isActive == true) return
        val folderUri = PreferencesManager.getFolderUri(this) ?: return
        val scanContext = if (settingsWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) applicationContext
            else PreferencesManager.widgetContext(this, settingsWidgetId)

        scanningIndicator.visibility = View.VISIBLE
        tvScanningStatus.text = getString(R.string.scanning_files)

        scanJob = lifecycleScope.launch {
            try {
            val (totalCount, filteredCount) = withContext(Dispatchers.IO) {
                MarkdownFileScanner.refreshCacheWithProgress(scanContext, folderUri) { phase, current, total ->
                    if (phase == "index" && total > 0 && (current % 25 == 0 || current == total)) {
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(this@MainActivity, R.string.saf_permission_lost, Toast.LENGTH_LONG).show() }
            finally { scanningIndicator.visibility = View.GONE }
        }
    }

    private fun updateFilteredCount() {
        lifecycleScope.launch {
            val tags = PreferencesManager.getTagList(this@MainActivity)
            try {
                val (totalCount, filteredCount) = withContext(Dispatchers.IO) {
                    PreferencesManager.getCachedFileUris(this@MainActivity).size to TagIndexManager.getFilteredCount(this@MainActivity, tags)
                }
                tvFileCount.text = getString(R.string.files_filtered, filteredCount, totalCount,
                    tags.joinToString(", ") { "#$it" })
                tvFileCount.visibility = View.VISIBLE
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(this@MainActivity, R.string.storage_error, Toast.LENGTH_LONG).show() }
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

    private fun setupAdvancedSettings() {
        findViewById<MaterialButton>(R.id.btn_open_notes).setOnClickListener {
            startActivity(Intent(this, NoteViewerActivity::class.java).putExtra(NoteViewerActivity.EXTRA_WIDGET_ID, settingsWidgetId))
        }
        fun text(id: Int, key: String, default: String = "") {
            val edit = findViewById<EditText>(id)
            edit.setText(PreferencesManager.getOption(this, key, default))
            edit.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(value: Editable?) { PreferencesManager.setOption(this@MainActivity, key, value.toString()) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        text(R.id.edit_excluded_tags, "excluded_tags")
        text(R.id.edit_vault_name, "obsidian_vault")
        text(R.id.edit_note_template, "note_template")
        text(R.id.edit_notes_subfolder, "notes_subfolder")
        findViewById<MaterialSwitch>(R.id.switch_tags_and).apply {
            isChecked = PreferencesManager.getTagLogic(this@MainActivity) == PreferencesManager.TAG_LOGIC_AND
            setOnCheckedChangeListener { _, checked ->
                PreferencesManager.setTagLogic(this@MainActivity, if (checked) PreferencesManager.TAG_LOGIC_AND else PreferencesManager.TAG_LOGIC_OR)
                updateFilteredCount()
            }
        }
        findViewById<MaterialSwitch>(R.id.switch_favorites_only).apply {
            isChecked = PreferencesManager.getOption(this@MainActivity, "favorites_only") == "true"
            setOnCheckedChangeListener { _, checked ->
                PreferencesManager.setOption(this@MainActivity, "favorites_only", checked.toString())
                updateFilteredCount()
            }
        }
        val dates = arrayOf("auto", "yaml", "filename", "modified")
        findViewById<MaterialButton>(R.id.btn_date_source).setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.date_source)
                .setSingleChoiceItems(R.array.date_sources, dates.indexOf(PreferencesManager.getOption(this, "date_source", "auto"))) { dialog, which ->
                    PreferencesManager.setOption(this, "date_source", dates[which]); dialog.dismiss()
                }.show()
        }
        findViewById<MaterialButton>(R.id.btn_widget_preview).setOnClickListener {
            val preview = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                setBackgroundColor(PreferencesManager.getCustomWidgetBgColor(this@MainActivity))
            }
            preview.addView(TextView(this).apply { setText(R.string.preview_title); textSize = 18f; setTextColor(PreferencesManager.getCustomWidgetTitleColor(this@MainActivity)) })
            preview.addView(TextView(this).apply { setText(R.string.preview_body); textSize = WidgetThemeHelper.getFontSizeSp(this@MainActivity); setTextColor(PreferencesManager.getCustomWidgetTextColor(this@MainActivity)) })
            val bg = PreferencesManager.getCustomWidgetBgColor(this)
            val fg = PreferencesManager.getCustomWidgetTextColor(this)
            val opaqueBg = androidx.core.graphics.ColorUtils.compositeColors(bg, Color.BLACK)
            val contrast = androidx.core.graphics.ColorUtils.calculateContrast(fg, opaqueBg)
            MaterialAlertDialogBuilder(this).setTitle(getString(R.string.preview_contrast, contrast)).setView(preview)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }
}
