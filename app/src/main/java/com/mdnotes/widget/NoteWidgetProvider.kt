package com.mdnotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews

open class NoteWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.mdnotes.widget.ACTION_REFRESH"
        const val ACTION_PIN_NOTE = "com.mdnotes.widget.ACTION_PIN_NOTE"
        const val EXTRA_WIDGET_ID = "extra_widget_id"

        fun scheduleWork(context: Context) = WidgetUpdateCoordinator.schedule(context)
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) = WidgetUpdateCoordinator.refresh(context, widgetId, true)

        private fun activityIntent(context: Context, id: Int, action: String, target: Class<*>): Intent =
            Intent(context, target).setData(Uri.parse("mdnotes://widget/$id/$action"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(NoteViewerActivity.EXTRA_WIDGET_ID, id)

        private fun settings(context: Context, id: Int) = PendingIntent.getActivity(context, id,
            activityIntent(context, id, "settings", MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun broadcast(context: Context, id: Int, action: String): PendingIntent = PendingIntent.getBroadcast(context, id,
            Intent(context, NoteWidgetProvider::class.java).setAction(action).putExtra(EXTRA_WIDGET_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun showError(context: Context, id: Int, message: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_empty)
            WidgetThemeHelper.applyTheme(context, views, R.id.widget_empty_root)
            views.setOnClickPendingIntent(R.id.widget_empty_root, settings(context, id))
            views.setOnClickPendingIntent(R.id.widget_empty_refresh, broadcast(context, id, ACTION_REFRESH))
            // The empty layout has a shared title; make access failures distinguishable from no matches.
            views.setTextViewText(R.id.widget_empty_text, context.getString(message))
            AppWidgetManager.getInstance(context).updateAppWidget(id, views)
        }

        internal fun configureContent(views: RemoteViews, text: Int, fontSize: Float) {
            views.setTextViewTextSize(text, android.util.TypedValue.COMPLEX_UNIT_SP, fontSize)
            // Reset restrictions on re-applied RemoteViews as well as newly inflated views.
            views.setBoolean(text, "setSingleLine", false)
            views.setInt(text, "setMaxLines", Int.MAX_VALUE)
        }

        fun render(context: Context, id: Int, retain: Boolean, canPublish: () -> Boolean = { true }) {
            val manager = AppWidgetManager.getInstance(context)
            if (id !in WidgetUpdateCoordinator.ids(context)) return
            val folder = PreferencesManager.getFolderUri(context)
            if (folder == null) {
                val views = RemoteViews(context.packageName, R.layout.widget_unconfigured)
                views.setOnClickPendingIntent(R.id.widget_unconfigured_root, settings(context, id))
                manager.updateAppWidget(id, views)
                return
            }
            val pinned = PreferencesManager.isNotePinned(context, id)
            val previous = PreferencesManager.getCurrentNoteUri(context, id)
            var note = if ((pinned || retain) && previous != null) MarkdownFileScanner.readNoteContent(context, Uri.parse(previous)) else null
            if (note == null) {
                if (pinned) PreferencesManager.setNotePinned(context, id, false)
                repeat(3) {
                    if (note == null) MarkdownFileScanner.getRandomFile(context, folder)?.let { uri ->
                        note = MarkdownFileScanner.readNoteContent(context, uri)
                        if (note == null) PreferencesManager.setCachedFileUris(context, PreferencesManager.getCachedFileUris(context).filterNot { it == uri.toString() })
                    }
                }
            }
            val content = note ?: run { showError(context, id, R.string.no_notes_found); return }
            if (!canPublish()) return
            PreferencesManager.setCurrentNoteUri(context, id, content.uri.toString())
            val expanded = manager.getAppWidgetInfo(id)?.provider?.className == NoteListWidgetProvider::class.java.name
            val views = RemoteViews(context.packageName, if (expanded) R.layout.widget_list else R.layout.widget_note)
            val root = if (expanded) R.id.widget_scroll_root else R.id.widget_root
            val title = if (expanded) R.id.widget_scroll_title else R.id.widget_title
            val text = if (expanded) R.id.widget_scroll_content else R.id.widget_content
            val meta = if (expanded) R.id.widget_scroll_meta else R.id.widget_meta
            val refresh = if (expanded) R.id.widget_scroll_random_btn else R.id.widget_refresh_btn
            val pin = if (expanded) R.id.widget_scroll_pin_btn else R.id.widget_pin_btn
            val create = if (expanded) R.id.widget_scroll_create_btn else R.id.widget_create_btn
            WidgetThemeHelper.applyTheme(context, views, root)
            WidgetThemeHelper.applyCustomTextColors(context, views, title, text, meta)
            views.setTextViewText(title, content.title)
            views.setTextViewText(text, content.content.take(if (expanded) 4000 else 2000))
            views.setTextViewText(meta, listOf(content.folderName, PreferencesManager.getTagList(context).joinToString { "#$it" })
                .filter { it.isNotBlank() }.joinToString(", "))
            views.setViewVisibility(meta, android.view.View.VISIBLE)
            val fontSize = WidgetThemeHelper.getFontSizeSp(context)
            configureContent(views, text, fontSize)
            views.setOnClickPendingIntent(refresh, broadcast(context, id, ACTION_REFRESH))
            views.setImageViewResource(pin, if (PreferencesManager.isNotePinned(context, id)) R.drawable.ic_pin_filled else R.drawable.ic_pin_outline)
            views.setOnClickPendingIntent(pin, broadcast(context, id, ACTION_PIN_NOTE))
            val open = PendingIntent.getActivity(context, id,
                activityIntent(context, id, "open", OpenNoteActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(text, open)
            views.setOnClickPendingIntent(title, settings(context, id))
            if (expanded) views.setOnClickPendingIntent(R.id.widget_scroll_open_btn, open)
            views.setOnClickPendingIntent(create, PendingIntent.getActivity(context, id,
                activityIntent(context, id, "create", NoteViewerActivity::class.java).putExtra("action_create", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            if (canPublish()) manager.updateAppWidget(id, views)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdateCoordinator.schedule(context)
        appWidgetIds.forEach { WidgetUpdateCoordinator.refresh(context, it, true) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetUpdateCoordinator.refresh(context, id, true)
    }

    override fun onEnabled(context: Context) { WidgetUpdateCoordinator.schedule(context) }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { appWidgetIds.forEach { WidgetUpdateCoordinator.remove(context, it) } }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WidgetUpdateCoordinator.schedule(context)
        }
        if (intent.action != ACTION_REFRESH && intent.action != ACTION_PIN_NOTE) return
        val id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id !in WidgetUpdateCoordinator.ids(context)) return
        val pin = intent.action == ACTION_PIN_NOTE
        val pending = goAsync()
        WidgetUpdateCoordinator.refreshFromClick(context, id, pin) { pending.finish() }
    }
}
