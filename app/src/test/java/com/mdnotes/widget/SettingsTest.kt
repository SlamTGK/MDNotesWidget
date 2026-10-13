package com.mdnotes.widget

import android.content.Context
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun `excluding five notes updates folder total even when tags already exclude them`() {
        val draft = SettingsDraft(context)
        PreferencesManager.setTagFilter(draft, "selected")
        val notes = (0 until 877).map { i ->
            SettingsCatalog.Entry("note:$i", "${if (i < 5) "Archive" else "Notes"}/$i.md",
                if (i < 831) setOf("selected") else emptySet())
        }
        val catalog = SettingsCatalog(Uri.parse("content://notes/root"), listOf("Archive", "Notes"), notes)
        assertEquals(831, catalog.selection(draft).matches.size)
        PreferencesManager.setOption(draft, "excluded_folder_paths", "[\"Archive\"]")
        val selection = catalog.selection(draft)
        assertEquals(826, selection.matches.size)
        assertEquals(872, selection.available)
        assertEquals(5, selection.excludedByFolders)
        val alreadyFiltered = catalog.copy(notes = notes.mapIndexed { i, note ->
            note.copy(tags = if (i in 5 until 836) setOf("selected") else emptySet())
        }).selection(draft)
        assertEquals(831, alreadyFiltered.matches.size)
        assertEquals(872, alreadyFiltered.available)
        assertEquals(5, alreadyFiltered.excludedByFolders)
        draft.applySettings()
        assertEquals(selection, catalog.copy().selection(context))
    }

    @Test fun `hidden directories are always excluded but dotted ordinary names remain`() {
        val catalog = SettingsCatalog(Uri.parse("content://notes/root"), emptyList(), listOf(
            SettingsCatalog.Entry("hidden-root", ".backup/one.md", emptySet()),
            SettingsCatalog.Entry("hidden-child", "Notes/.anything/two.md", emptySet()),
            SettingsCatalog.Entry("visible", "Notes.v2/three.md", emptySet())))
        assertEquals(listOf("visible"), catalog.selection(SettingsDraft(context)).matches.map { it.uri })
        assertEquals(1, catalog.selection(context).total)
        assertTrue(FolderRules.excludes("Notes/.anything/sub", emptyList()))
        assertFalse(FolderRules.excludes("Notes.v2", emptyList()))
    }

    @Test fun `draft is isolated survives recreation and applies without overwriting widget state`() {
        PreferencesManager.setTagFilter(context, "work")
        val draft = SettingsDraft(context)
        PreferencesManager.setTagFilter(draft, "personal")
        PreferencesManager.setOption(draft, "note_template", "# {{title}}\ncreated: {{date}}")
        assertEquals("work", PreferencesManager.getTagFilter(context))
        val restored = SettingsDraft(context, draft.serialize())
        assertTrue(restored.dirty)
        PreferencesManager.setCurrentNoteUri(context, 42, "content://notes/new")
        PreferencesManager.setNotePinned(context, 42, true)
        restored.applySettings()
        assertEquals("personal", PreferencesManager.getTagFilter(context))
        assertEquals("content://notes/new", PreferencesManager.getCurrentNoteUri(context, 42))
        assertTrue(PreferencesManager.isNotePinned(context, 42))
        assertFalse(restored.dirty)
    }

    @Test fun `existing widget configuration migrates once and preserves current note`() {
        PreferencesManager.setTagFilter(context, "old-default")
        context.getSharedPreferences("widget_settings_42", Context.MODE_PRIVATE).edit()
            .putBoolean("initialized", true).putString("tag_filter", "quotes")
            .putString("note_42", "content://notes/quote").putBoolean("pinned_42", true).apply()
        PreferencesManager.migrateSharedSettings(context, listOf(42))
        assertEquals("quotes", PreferencesManager.getTagFilter(context))
        assertEquals("content://notes/quote", PreferencesManager.getCurrentNoteUri(context, 42))
        assertTrue(PreferencesManager.isNotePinned(context, 42))
        PreferencesManager.setTagFilter(context, "changed")
        PreferencesManager.migrateSharedSettings(context, listOf(42))
        assertEquals("changed", PreferencesManager.getTagFilter(context))
    }

    @Test fun `catalog preview applies all filters and isolates same-name folders`() {
        val catalog = SettingsCatalog(Uri.parse("content://notes/root"), listOf("Work/Archive", "Personal/Archive"), listOf(
            SettingsCatalog.Entry("one", "Work/Archive/one.md", setOf("work")),
            SettingsCatalog.Entry("two", "Personal/Archive/two.md", setOf("work", "private")),
            SettingsCatalog.Entry("three", "Personal/three.md", setOf("work"))))
        val draft = SettingsDraft(context)
        PreferencesManager.setOption(draft, "excluded_folder_paths", "[\"Work/Archive\"]")
        assertEquals(listOf("two", "three"), catalog.matching(draft).map { it.uri })
        PreferencesManager.setTagFilter(draft, "work")
        PreferencesManager.setOption(draft, "excluded_tags", "private")
        assertEquals(listOf("three"), catalog.matching(draft).map { it.uri })
        PreferencesManager.setOption(draft, "favorites_only", "true")
        assertTrue(catalog.matching(draft).isEmpty())
        PreferencesManager.addToFavorites(context, "three")
        assertEquals(listOf("three"), catalog.matching(draft).map { it.uri })
        assertEquals(3, catalog.notes.size)
    }
}
