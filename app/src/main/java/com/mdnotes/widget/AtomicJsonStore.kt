package com.mdnotes.widget

import android.util.AtomicFile
import java.io.File

/** All read-modify-write operations must also hold this monitor. */
object AtomicJsonStore {
    @Synchronized
    fun read(file: File): String? = if (file.exists() || File(file.path + ".bak").exists()) {
        AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    } else null

    @Synchronized
    fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    /** One monitor for a complete local list read-modify-write transaction. */
    @Synchronized
    fun <T> transaction(block: () -> T): T = block()
}
