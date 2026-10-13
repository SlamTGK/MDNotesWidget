package com.mdnotes.widget

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ListView
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import org.robolectric.shadows.ShadowDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsActivityTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun `folder picker selection changes counts and omits hidden folders`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val catalog = SettingsCatalog(Uri.parse("content://notes/root"), listOf("Archive", "Notes", ".backup", "Notes/.hidden"),
            (0 until 10).map { i -> SettingsCatalog.Entry("note:$i", "${if (i < 5) "Archive" else "Notes"}/$i.md", emptySet()) })
        MainActivity::class.java.getDeclaredField("catalog").apply { isAccessible = true }.set(activity, catalog)
        val views = descendants(activity.findViewById(R.id.settings_sections))
        views.filterIsInstance<TextView>().first { it.text == activity.getString(R.string.settings_choose_folders) }.performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val list = descendants(dialog.window!!.decorView).filterIsInstance<ListView>().single()
        assertEquals(2, list.adapter.count)
        list.performItemClick(list.adapter.getView(0, null, list), 0, list.adapter.getItemId(0))
        assertTrue(list.isItemChecked(0))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("[\"Archive\"]", PreferencesManager.getOption(activity.draft, "excluded_folder_paths"))
        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString().startsWith(activity.getString(R.string.settings_match_count, 5, 5)) })
        controller.pause().stop().destroy()
    }

    @Test fun `editing template does not save on pause and survives recreation`() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.setOption(context, "note_template", "original")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val input = descendants(activity.findViewById(R.id.settings_sections))
            .filterIsInstance<TextInputLayout>().first { it.hint == activity.getString(R.string.settings_template) }
            .editText as TextInputEditText
        input.setText("draft {{title}}")
        assertEquals("original", PreferencesManager.getOption(context, "note_template"))
        controller.recreate()
        assertEquals("draft {{title}}", PreferencesManager.getOption(controller.get().draft, "note_template"))
        assertEquals("original", PreferencesManager.getOption(context, "note_template"))
        assertEquals(controller.get().getString(R.string.settings_unsaved), controller.get().findViewById<TextView>(R.id.settings_status).text)
        controller.pause().stop().destroy()
        assertEquals("original", PreferencesManager.getOption(context, "note_template"))
    }
}
