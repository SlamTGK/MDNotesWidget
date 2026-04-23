package com.mdnotes.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            PreferencesManager.setFolderUri(this, uri)
            updateFolderDisplay(uri)
            updateFileCountBadge(0)

            lifecycleScope.launch {
                val count = withContext(Dispatchers.IO) {
                    MarkdownFileScanner.refreshCache(this@MainActivity, uri)
                }
                updateFileCountBadge(count)
                triggerWidgetUpdate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pendingWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
        }

        setupFolderSection()
        setupIntervalSection()
        setupOpenWithSection()
        setupThemeSection()
        setupFontSection()
        setupTagFilterSection()
        setupQuietHoursSection()
        setupActionButtons()
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private fun setupFolderSection() {
        val folderUri = PreferencesManager.getFolderUri(this)
        if (folderUri != null) {
            updateFolderDisplay(folderUri)
            lifecycleScope.launch {
                val count = withContext(Dispatchers.IO) {
                    PreferencesManager.getCachedFileUris(this@MainActivity).size
                }
                updateFileCountBadge(count)
            }
        } else {
            updateFileCountBadge(0)
        }

        findViewById<Button>(R.id.btn_select_folder).setOnClickListener {
            folderPickerLauncher.launch(PreferencesManager.getFolderUri(this))
        }
    }

    private fun setupIntervalSection() {
        val seekBar = findViewById<SeekBar>(R.id.seekbar_interval)
        val tvValue = findViewById<TextView>(R.id.tv_interval_value)
        val currentMinutes = PreferencesManager.getIntervalMinutes(this)

        seekBar.max = 25
        seekBar.progress = minutesToProgress(currentMinutes)
        tvValue.text = formatInterval(currentMinutes)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                val minutes = progressToMinutes(progress)
                tvValue.text = formatInterval(minutes)
                if (fromUser) {
                    PreferencesManager.setIntervalMinutes(this@MainActivity, minutes)
                    NoteWidgetProvider.scheduleWork(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }

    private fun progressToMinutes(progress: Int): Int {
        return when (progress) {
            0 -> -1
            1 -> 30
            else -> (progress - 1) * 60
        }
    }

    private fun minutesToProgress(minutes: Int): Int {
        return when {
            minutes < 0 -> 0
            minutes == 30 -> 1
            else -> (minutes / 60) + 1
        }
    }

    private fun formatInterval(minutes: Int): String {
        if (minutes < 0) return getString(R.string.interval_off)
        if (minutes == 30) return getString(R.string.interval_30m)
        val hours = minutes / 60
        return resources.getQuantityString(R.plurals.hours_format, hours, hours)
    }

    private fun setupOpenWithSection() {
        val openWith = PreferencesManager.getOpenWith(this)
        val radioViewer = findViewById<RadioButton>(R.id.radio_viewer)
        val radioObsidian = findViewById<RadioButton>(R.id.radio_obsidian)
        val radioSystem = findViewById<RadioButton>(R.id.radio_system)

        when (openWith) {
            PreferencesManager.OPEN_WITH_OBSIDIAN -> radioObsidian.isChecked = true
            PreferencesManager.OPEN_WITH_SYSTEM -> radioSystem.isChecked = true
            else -> radioViewer.isChecked = true
        }

        val group = findViewById<RadioGroup>(R.id.radio_group_open_with)
        group.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_obsidian -> PreferencesManager.OPEN_WITH_OBSIDIAN
                R.id.radio_system -> PreferencesManager.OPEN_WITH_SYSTEM
                else -> PreferencesManager.OPEN_WITH_VIEWER
            }
            PreferencesManager.setOpenWith(this, value)
        }
    }

    private fun setupThemeSection() {
        val theme = PreferencesManager.getWidgetTheme(this)
        val customColorsPanel = findViewById<LinearLayout>(R.id.custom_colors_panel)

        when (theme) {
            PreferencesManager.THEME_DARK -> findViewById<RadioButton>(R.id.radio_theme_dark).isChecked = true
            PreferencesManager.THEME_TRANSPARENT -> findViewById<RadioButton>(R.id.radio_theme_transparent).isChecked = true
            PreferencesManager.THEME_CUSTOM -> {
                findViewById<RadioButton>(R.id.radio_theme_custom).isChecked = true
                customColorsPanel.visibility = View.VISIBLE
            }
            else -> findViewById<RadioButton>(R.id.radio_theme_default).isChecked = true
        }

        updateColorPreviews()

        val group = findViewById<RadioGroup>(R.id.radio_group_theme)
        group.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_theme_dark -> PreferencesManager.THEME_DARK
                R.id.radio_theme_transparent -> PreferencesManager.THEME_TRANSPARENT
                R.id.radio_theme_custom -> PreferencesManager.THEME_CUSTOM
                else -> PreferencesManager.THEME_DEFAULT
            }
            PreferencesManager.setWidgetTheme(this, value)
            customColorsPanel.visibility = if (value == PreferencesManager.THEME_CUSTOM) View.VISIBLE else View.GONE
            triggerWidgetUpdate()
        }

        // Color picker buttons
        setupColorPickerButton(R.id.btn_pick_bg_color, "bg")
        setupColorPickerButton(R.id.btn_pick_text_color, "text")
        setupColorPickerButton(R.id.btn_pick_title_color, "title")
    }

    private fun setupColorPickerButton(buttonId: Int, colorType: String) {
        findViewById<View>(buttonId).setOnClickListener {
            val currentColor = when (colorType) {
                "bg" -> PreferencesManager.getCustomWidgetBgColor(this)
                "text" -> PreferencesManager.getCustomWidgetTextColor(this)
                "title" -> PreferencesManager.getCustomWidgetTitleColor(this)
                else -> Color.WHITE
            }
            showColorPickerDialog(currentColor) { selectedColor ->
                when (colorType) {
                    "bg" -> PreferencesManager.setCustomWidgetBgColor(this, selectedColor)
                    "text" -> PreferencesManager.setCustomWidgetTextColor(this, selectedColor)
                    "title" -> PreferencesManager.setCustomWidgetTitleColor(this, selectedColor)
                }
                updateColorPreviews()
                triggerWidgetUpdate()
            }
        }
    }

    private fun showColorPickerDialog(currentColor: Int, onColorSelected: (Int) -> Unit) {
        // Simple color picker using a dialog with preset colors + hex input
        val colors = intArrayOf(
            0xFF1E1B4B.toInt(), 0xFF18181B.toInt(), 0xFF0F172A.toInt(),
            0xFF1a1a2e.toInt(), 0xFF16213e.toInt(), 0xFF0f3460.toInt(),
            0xFF2d132c.toInt(), 0xFF1b1b2f.toInt(), 0xFF162447.toInt(),
            0xFFFFFFFF.toInt(), 0xFFF8FAFC.toInt(), 0xFFC7D2E8.toInt(),
            0xFF94A3B8.toInt(), 0xFF7C3AED.toInt(), 0xFF06B6D4.toInt(),
            0xFFE11D48.toInt(), 0xFFF59E0B.toInt(), 0xFF10B981.toInt()
        )

        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.pick_color))

        val gridLayout = GridLayout(this).apply {
            columnCount = 6
            setPadding(24, 24, 24, 24)
        }

        for (color in colors) {
            val swatch = View(this).apply {
                val params = GridLayout.LayoutParams().apply {
                    width = 120
                    height = 120
                    setMargins(8, 8, 8, 8)
                }
                layoutParams = params
                background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 16f
                    if (color == currentColor) {
                        setStroke(4, 0xFFFFFFFF.toInt())
                    }
                }
                setOnClickListener {
                    onColorSelected(color)
                    (it.parent as? View)?.let { parent ->
                        // Find and dismiss the dialog
                        var v: View? = parent
                        while (v != null) {
                            if (v.tag is android.app.AlertDialog) {
                                (v.tag as android.app.AlertDialog).dismiss()
                                return@setOnClickListener
                            }
                            v = v.parent as? View
                        }
                    }
                }
            }
            gridLayout.addView(swatch)
        }

        val scrollView = ScrollView(this).apply {
            addView(gridLayout)
        }

        val dialog = builder.setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        // Tag dialog for dismissal
        scrollView.tag = dialog
        gridLayout.tag = dialog

        // Update swatch click listeners to dismiss dialog
        for (i in 0 until gridLayout.childCount) {
            val swatch = gridLayout.getChildAt(i)
            val color = colors[i]
            swatch.setOnClickListener {
                onColorSelected(color)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun updateColorPreviews() {
        val bgColor = PreferencesManager.getCustomWidgetBgColor(this)
        val textColor = PreferencesManager.getCustomWidgetTextColor(this)
        val titleColor = PreferencesManager.getCustomWidgetTitleColor(this)

        findViewById<View>(R.id.color_preview_bg)?.let {
            it.background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 8f
            }
        }
        findViewById<View>(R.id.color_preview_text)?.let {
            it.background = GradientDrawable().apply {
                setColor(textColor)
                cornerRadius = 8f
            }
        }
        findViewById<View>(R.id.color_preview_title)?.let {
            it.background = GradientDrawable().apply {
                setColor(titleColor)
                cornerRadius = 8f
            }
        }
    }

    private fun setupFontSection() {
        val fontSize = PreferencesManager.getFontSize(this)
        when (fontSize) {
            PreferencesManager.FONT_SIZE_SMALL -> findViewById<RadioButton>(R.id.radio_font_small).isChecked = true
            PreferencesManager.FONT_SIZE_LARGE -> findViewById<RadioButton>(R.id.radio_font_large).isChecked = true
            else -> findViewById<RadioButton>(R.id.radio_font_medium).isChecked = true
        }

        val group = findViewById<RadioGroup>(R.id.radio_group_font)
        group.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_font_small -> PreferencesManager.FONT_SIZE_SMALL
                R.id.radio_font_large -> PreferencesManager.FONT_SIZE_LARGE
                else -> PreferencesManager.FONT_SIZE_MEDIUM
            }
            PreferencesManager.setFontSize(this, value)
            triggerWidgetUpdate()
        }
    }

    private fun setupTagFilterSection() {
        val editTag = findViewById<EditText>(R.id.edit_tag_filter)
        val editBlacklist = findViewById<EditText>(R.id.edit_folder_blacklist)

        editTag.setText(PreferencesManager.getTagFilter(this))
        editBlacklist.setText(PreferencesManager.getFolderBlacklistRaw(this))

        // Tag logic
        val tagLogic = PreferencesManager.getTagLogic(this)
        val radioOr = findViewById<RadioButton>(R.id.radio_tag_or)
        val radioAnd = findViewById<RadioButton>(R.id.radio_tag_and)

        if (tagLogic == PreferencesManager.TAG_LOGIC_AND) {
            radioAnd.isChecked = true
        } else {
            radioOr.isChecked = true
        }

        val logicGroup = findViewById<RadioGroup>(R.id.radio_group_tag_logic)
        logicGroup.setOnCheckedChangeListener { _, checkedId ->
            val value = if (checkedId == R.id.radio_tag_and)
                PreferencesManager.TAG_LOGIC_AND
            else
                PreferencesManager.TAG_LOGIC_OR
            PreferencesManager.setTagLogic(this, value)
        }
    }

    private fun setupQuietHoursSection() {
        val switch = findViewById<MaterialSwitch>(R.id.switch_quiet_hours)
        val timePanel = findViewById<LinearLayout>(R.id.quiet_hours_time_panel)
        val btnStart = findViewById<Button>(R.id.btn_quiet_start)
        val btnEnd = findViewById<Button>(R.id.btn_quiet_end)

        val enabled = PreferencesManager.isQuietHoursEnabled(this)
        switch.isChecked = enabled
        timePanel.visibility = if (enabled) View.VISIBLE else View.GONE

        // Display current times
        updateQuietHoursDisplay(btnStart, PreferencesManager.getQuietHoursStart(this))
        updateQuietHoursDisplay(btnEnd, PreferencesManager.getQuietHoursEnd(this))

        switch.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setQuietHoursEnabled(this, isChecked)
            timePanel.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnStart.setOnClickListener {
            val current = PreferencesManager.getQuietHoursStart(this)
            showTimePicker(current / 60, current % 60) { hour, minute ->
                val minutes = hour * 60 + minute
                PreferencesManager.setQuietHoursStart(this, minutes)
                updateQuietHoursDisplay(btnStart, minutes)
            }
        }

        btnEnd.setOnClickListener {
            val current = PreferencesManager.getQuietHoursEnd(this)
            showTimePicker(current / 60, current % 60) { hour, minute ->
                val minutes = hour * 60 + minute
                PreferencesManager.setQuietHoursEnd(this, minutes)
                updateQuietHoursDisplay(btnEnd, minutes)
            }
        }
    }

    private fun showTimePicker(hour: Int, minute: Int, onTimeSelected: (Int, Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .build()

        picker.addOnPositiveButtonClickListener {
            onTimeSelected(picker.hour, picker.minute)
        }

        picker.show(supportFragmentManager, "time_picker")
    }

    private fun updateQuietHoursDisplay(button: Button, minutesFromMidnight: Int) {
        val h = minutesFromMidnight / 60
        val m = minutesFromMidnight % 60
        button.text = String.format("%02d:%02d", h, m)
    }

    private fun setupActionButtons() {
        findViewById<Button>(R.id.btn_refresh_widgets).setOnClickListener {
            saveAdvancedSettings()
            triggerWidgetUpdate()
            Toast.makeText(this, getString(R.string.widget_updated), Toast.LENGTH_SHORT).show()

            if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                finishWithOk()
            }
        }

        findViewById<Button>(R.id.btn_refresh_files).setOnClickListener {
            saveAdvancedSettings()
            val folderUri = PreferencesManager.getFolderUri(this)
            if (folderUri == null) {
                Toast.makeText(this, getString(R.string.no_folder_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val count = withContext(Dispatchers.IO) {
                    MarkdownFileScanner.refreshCache(this@MainActivity, folderUri)
                }
                updateFileCountBadge(count)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.files_refreshed, count),
                    Toast.LENGTH_SHORT
                ).show()
                triggerWidgetUpdate()
            }
        }

        findViewById<TextView>(R.id.tv_author_credit)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/slamtgk"))
            startActivity(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        saveAdvancedSettings()
    }

    private fun saveAdvancedSettings() {
        val editTag = findViewById<EditText>(R.id.edit_tag_filter)
        val editBlacklist = findViewById<EditText>(R.id.edit_folder_blacklist)
        PreferencesManager.setTagFilter(this, editTag.text.toString())
        PreferencesManager.setFolderBlacklistRaw(this, editBlacklist.text.toString())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateFolderDisplay(uri: Uri) {
        val tv = findViewById<TextView>(R.id.tv_folder_path)
        val segment = uri.lastPathSegment ?: uri.toString()
        val readable = segment.replace("primary:", "/sdcard/")
        tv.text = readable
    }

    private fun updateFileCountBadge(count: Int) {
        val tv = findViewById<TextView>(R.id.tv_file_count)
        tv.text = if (count == 0) "" else getString(R.string.file_count, count)
    }

    private fun triggerWidgetUpdate() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, NoteWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            val intent = Intent(this, NoteWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            sendBroadcast(intent)
        }
    }

    private fun finishWithOk() {
        val result = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
