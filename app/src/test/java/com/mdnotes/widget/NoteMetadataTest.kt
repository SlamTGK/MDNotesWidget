package com.mdnotes.widget

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NoteMetadataTest {
    @Test fun `folder selections distinguish paths while preserving legacy name rules`() {
        assertTrue(FolderRules.excludes("Work/Archive/2020", listOf("/Work/Archive")))
        assertFalse(FolderRules.excludes("Personal/Archive", listOf("/Work/Archive")))
        assertFalse(FolderRules.excludes("Work/Archive2", listOf("/Work/Archive")))
        assertTrue(FolderRules.excludes("Personal/Archive", listOf("archive")))
        assertTrue(FolderRules.excludes("Work/.obsidian", PreferencesManager.SYSTEM_BLACKLIST))
    }

    @Test fun `tags allow YAML comments and blank lines in lists`() {
        assertEquals(setOf("work", "personal", "body"), NoteMetadata.tags("---\ntags: [work, 'personal'] # comment\n---\n#body"))
        assertEquals(setOf("work", "personal"), NoteMetadata.tags("---\ntags:\n  - work # comment\n\n  # comment\n  - personal\n---\n"))
    }

    @Test fun `anniversary prefers creation date and never falls back to modification time in auto mode`() {
        assertEquals(LocalDate.of(2020, 1, 2), NoteMetadata.date("---\ndate: 2024-03-04\ncreated: 2020-01-02 # creation\n---\n", "2025-06-07.md", "auto", 12345))
        assertNull(NoteMetadata.date("no properties", "note.md", "auto", 1700000000000))
    }
    @Test fun `frontmatter only exists at document start`() {
        val raw = "Intro\n---\nKeep this text\n---\nEnd"
        assertEquals(raw, raw.replace(NoteMetadata.frontmatter, ""))
        assertEquals("Body", "---\n---\nBody".replace(NoteMetadata.frontmatter, ""))
        assertEquals("Body", "\uFEFF---\r\ntags: [work]\r\n---\r\nBody".replace(NoteMetadata.frontmatter, ""))
    }

    @Test fun `yaml and hashtags share exact normalized rules`() {
        assertEquals(setOf("work", "личное", "work/projects"), NoteMetadata.tags("""
            ---
            tags: ["work", '#Личное']
            ---
            #work/projects and `#not-a-tag`
            ```
            #not-a-tag-either
            ```
        """.trimIndent()))
        assertEquals(setOf("one", "two"), NoteMetadata.tags("---\ntags:\n  - one\n  - two\n---\n"))
        assertEquals(setOf("body"), NoteMetadata.tags("---\nNot frontmatter\n#body"))
    }

    @Test fun `tag matching handles OR AND exclusions and nested tags`() {
        val actual = setOf("work/project", "daily")
        assertTrue(NoteMetadata.matches(actual, listOf("WORK"), emptyList(), false))
        assertFalse(NoteMetadata.matches(setOf("workshop"), listOf("work"), emptyList(), false))
        assertTrue(NoteMetadata.matches(actual, listOf("work", "daily"), emptyList(), true))
        assertFalse(NoteMetadata.matches(actual, listOf("work", "missing"), emptyList(), true))
        assertFalse(NoteMetadata.matches(actual, emptyList(), listOf("work"), false))
    }

    @Test fun `anniversary date prefers explicit dates and rejects invalid values`() {
        assertEquals(LocalDate.of(2020, 2, 29), NoteMetadata.date("---\ndate: 2020-02-29\n---\n", "2022-01-01.md", "auto", 0))
        assertEquals(LocalDate.of(2022, 1, 1), NoteMetadata.date("---\ndate: invalid\n---\n", "2022-01-01.md", "auto", 0))
        assertNull(NoteMetadata.date("", "2023-02-29.md", "filename", 0))
        assertNull(NoteMetadata.date("", "note.md", "yaml", 0))
    }

    @Test fun `empty editor content is a dirty edit bound to its URI`() {
        val draft = EditorSession("note:A", "Original", "")
        assertTrue(draft.dirty)
        assertEquals("note:A", draft.copy(text = "Changed").uri)
    }
}
