package com.mdnotes.widget

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StorageTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.filesDir.deleteRecursively()
        context.filesDir.mkdirs()
    }

    @Test fun `atomic transaction serializes parallel list changes`() {
        val file = File(context.filesDir, "concurrent.json")
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        val values = mutableSetOf<String>()
        val jobs = (0 until 40).map { value -> pool.submit {
            gate.await()
            AtomicJsonStore.transaction {
                values.add("note:$value")
            }
        } }
        gate.countDown()
        jobs.forEach { it.get(15, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(40, values.distinct().size)
        AtomicJsonStore.write(file, JSONArray(values).toString())
        assertEquals(40, JSONArray(AtomicJsonStore.read(file)!!).length())
    }

    @Test fun `corrupt favorites are not replaced with an empty list on mutation`() {
        val file = File(context.filesDir, "favorites.json")
        file.writeText("broken json")
        try { PreferencesManager.addToFavorites(context, "new-note"); fail("Expected failure") }
        catch (_: java.io.IOException) {}
        assertEquals("broken json", file.readText())
    }

    @Test fun `widget settings are independent and inherit defaults once`() {
        PreferencesManager.setTagFilter(context, "default")
        val one = PreferencesManager.widgetContext(context, 101)
        val two = PreferencesManager.widgetContext(context, 102)
        assertEquals("default", PreferencesManager.getTagFilter(one))
        assertEquals("default", PreferencesManager.getTagFilter(two))
        PreferencesManager.setTagFilter(one, "work")
        PreferencesManager.setTagFilter(context, "new default")
        assertEquals("work", PreferencesManager.getTagFilter(one))
        assertEquals("default", PreferencesManager.getTagFilter(two))
    }

    @Test fun `changing folders clears a pin but preserves the template`() {
        val widget = PreferencesManager.widgetContext(context, 103)
        PreferencesManager.setOption(widget, "note_template", "# {{title}}")
        PreferencesManager.setCurrentNoteUri(widget, 103, "content://notes/a")
        PreferencesManager.setNotePinned(widget, 103, true)
        PreferencesManager.setFolderUri(widget, Uri.parse("content://notes/tree/root"))
        assertNull(PreferencesManager.getCurrentNoteUri(widget, 103))
        assertFalse(PreferencesManager.isNotePinned(widget, 103))
        assertEquals("# {{title}}", PreferencesManager.getOption(widget, "note_template"))
    }

    @Test fun `blacklists isolate caches for the same folder`() {
        val one = PreferencesManager.widgetContext(context, 104)
        val two = PreferencesManager.widgetContext(context, 105)
        val folder = Uri.parse("content://notes/tree/root")
        PreferencesManager.setFolderUri(one, folder)
        PreferencesManager.setFolderUri(two, folder)
        PreferencesManager.setFolderBlacklist(one, listOf("Private"))
        PreferencesManager.setFolderBlacklist(two, emptyList())
        PreferencesManager.setCachedFileUris(one, listOf("public"))
        PreferencesManager.setCachedFileUris(two, listOf("public", "private"))
        assertEquals(listOf("public"), PreferencesManager.getCachedFileUris(one))
        assertEquals(listOf("public", "private"), PreferencesManager.getCachedFileUris(two))
    }
}
