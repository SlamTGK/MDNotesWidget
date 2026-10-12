package com.mdnotes.widget

/** The edited URI never follows the pager position. */
data class EditorSession(val uri: String, val original: String, val text: String) {
    val dirty: Boolean get() = original != text
}
