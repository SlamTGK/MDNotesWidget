package com.mdnotes.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings screen for the MD Notes Widget app.
 * Also serves as the widget configuration activity (configure= in widget info XML).
 *
 * Provides:
 *  - SAF folder picker
 *  - Update interval slider (1–24 hours)
 *  - Open-with selection (Obsidian / System chooser)
 *  - Manual refresh buttons
 */
class MainActivity : AppCompatActivity() {

    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // SAF folder picker launcher
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Take persistent read permission so we can access the URI across sessions
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            PreferencesManager.setFolderUri(this, uri)
            updateFolderDisplay(uri)
            updateFileCountBadge(0)

            // Rescan and update widgets on background thread
            Thread {
                val count = MarkdownFileScanner.refreshCache(this, uri)
                runOnUiThread { updateFileCountBadge(count) }
                triggerWidgetUpdate()
            }.start()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // If launched as widget configure activity, capture the widget ID and set result early
        pendingWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Required: set RESULT_CANCELED first so widget isn't added if user backs out
            setResult(Activity.RESULT_CANCELED)
        }

        setupFolderSection()
        setupIntervalSection()
        setupOpenWithSection()
        setupThemeSection()
        setupActionButtons()
    }

    // ── Section setup ─────────────────────────────────────────────────────────

    private fun setupFolderSection() {
        val folderUri = PreferencesManager.getFolderUri(this)
        if (folderUri != null) {
            updateFolderDisplay(folderUri)
            Thread {
                val count = PreferencesManager.getCachedFileUris(this).size
                runOnUiThread { updateFileCountBadge(count) }
            }.start()
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
        val current = PreferencesManager.getIntervalHours(this)

        seekBar.max = 23 // 0..23 maps to 1..24 hours
        seekBar.progress = current - 1
        tvValue.text = formatHours(current)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                val hours = progress + 1
                tvValue.text = formatHours(hours)
                if (fromUser) {
                    PreferencesManager.setIntervalHours(this@MainActivity, hours)
                    NoteWidgetProvider.scheduleWork(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }

    private fun setupOpenWithSection() {
        val openWith = PreferencesManager.getOpenWith(this)
        val radioObsidian = findViewById<RadioButton>(R.id.radio_obsidian)
        val radioSystem   = findViewById<RadioButton>(R.id.radio_system)

        if (openWith == PreferencesManager.OPEN_WITH_OBSIDIAN) {
            radioObsidian.isChecked = true
        } else {
            radioSystem.isChecked = true
        }

        val group = findViewById<RadioGroup>(R.id.radio_group_open_with)
        group.setOnCheckedChangeListener { _, checkedId ->
            val value = if (checkedId == R.id.radio_obsidian)
                PreferencesManager.OPEN_WITH_OBSIDIAN
            else
                PreferencesManager.OPEN_WITH_SYSTEM
            PreferencesManager.setOpenWith(this, value)
        }
    }

    private fun setupThemeSection() {
        val theme = PreferencesManager.getWidgetTheme(this)
        when (theme) {
            PreferencesManager.THEME_DARK -> findViewById<RadioButton>(R.id.radio_theme_dark).isChecked = true
            PreferencesManager.THEME_TRANSPARENT -> findViewById<RadioButton>(R.id.radio_theme_transparent).isChecked = true
            else -> findViewById<RadioButton>(R.id.radio_theme_default).isChecked = true
        }

        val group = findViewById<RadioGroup>(R.id.radio_group_theme)
        group.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_theme_dark -> PreferencesManager.THEME_DARK
                R.id.radio_theme_transparent -> PreferencesManager.THEME_TRANSPARENT
                else -> PreferencesManager.THEME_DEFAULT
            }
            PreferencesManager.setWidgetTheme(this, value)
            
            // Instantly update the widgets on the home screen to show the new theme
            triggerWidgetUpdate()
        }
    }

    private fun setupActionButtons() {
        // Refresh all widget displays
        findViewById<Button>(R.id.btn_refresh_widgets).setOnClickListener {
            triggerWidgetUpdate()
            Toast.makeText(this, getString(R.string.widget_updated), Toast.LENGTH_SHORT).show()

            // If we're a config activity, finish with OK so widget gets added
            if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                finishWithOk()
            }
        }

        // Rescan folder for new/deleted .md files
        findViewById<Button>(R.id.btn_refresh_files).setOnClickListener {
            val folderUri = PreferencesManager.getFolderUri(this)
            if (folderUri == null) {
                Toast.makeText(this, getString(R.string.no_folder_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                val count = MarkdownFileScanner.refreshCache(this, folderUri)
                runOnUiThread {
                    updateFileCountBadge(count)
                    Toast.makeText(
                        this,
                        getString(R.string.files_refreshed, count),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                triggerWidgetUpdate()
            }.start()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateFolderDisplay(uri: Uri) {
        val tv = findViewById<TextView>(R.id.tv_folder_path)
        // Convert SAF segment to human-readable path (e.g. "primary:Obsidian" → "/sdcard/Obsidian")
        val segment = uri.lastPathSegment ?: uri.toString()
        val readable = segment.replace("primary:", "/sdcard/")
        tv.text = readable
    }

    private fun updateFileCountBadge(count: Int) {
        val tv = findViewById<TextView>(R.id.tv_file_count)
        tv.text = if (count == 0) "" else getString(R.string.file_count, count)
    }

    private fun formatHours(hours: Int): String {
        return resources.getQuantityString(R.plurals.hours_format, hours, hours)
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
