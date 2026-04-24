package com.mdnotes.widget

import android.content.Context
import android.widget.RemoteViews

/**
 * Centralized helper for applying widget themes to RemoteViews.
 * Eliminates duplicated theme code across NoteWidgetProvider and NoteListWidgetProvider.
 */
object WidgetThemeHelper {

    /**
     * Apply the selected theme background to the root view.
     */
    fun applyTheme(context: Context, views: RemoteViews, rootViewId: Int) {
        when (val theme = PreferencesManager.getWidgetTheme(context)) {
            PreferencesManager.THEME_CUSTOM -> {
                val bgColor = PreferencesManager.getCustomWidgetBgColor(context)
                views.setInt(rootViewId, "setBackgroundColor", bgColor)
            }
            else -> {
                val bgRes = when (theme) {
                    PreferencesManager.THEME_DARK -> R.drawable.widget_bg_dark
                    PreferencesManager.THEME_TRANSPARENT -> R.drawable.widget_bg_transparent
                    else -> R.drawable.widget_bg_default
                }
                views.setInt(rootViewId, "setBackgroundResource", bgRes)
            }
        }
    }

    /**
     * Apply custom text colors if the custom theme is active.
     */
    fun applyCustomTextColors(
        context: Context,
        views: RemoteViews,
        titleViewId: Int,
        contentViewId: Int,
        metaViewId: Int? = null
    ) {
        val theme = PreferencesManager.getWidgetTheme(context)
        if (theme == PreferencesManager.THEME_CUSTOM) {
            val titleColor = PreferencesManager.getCustomWidgetTitleColor(context)
            val textColor = PreferencesManager.getCustomWidgetTextColor(context)
            views.setTextColor(titleViewId, titleColor)
            views.setTextColor(contentViewId, textColor)
            if (metaViewId != null) {
                views.setTextColor(metaViewId, (textColor and 0x00FFFFFF) or 0x99000000.toInt())
            }
        }
    }

    /**
     * Get the font size in SP based on the user's preference.
     */
    fun getFontSizeSp(context: Context): Float {
        return when (PreferencesManager.getFontSize(context)) {
            PreferencesManager.FONT_SIZE_SMALL -> 10f
            PreferencesManager.FONT_SIZE_LARGE -> 16f
            else -> 12f
        }
    }
}
