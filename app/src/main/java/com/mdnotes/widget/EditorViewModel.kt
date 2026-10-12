package com.mdnotes.widget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = NoteRepository(app)
    private val mutableSession = MutableStateFlow<EditorSession?>(null)
    val session = mutableSession.asStateFlow()
    val error = MutableStateFlow<Throwable?>(null)
    private val operations = Mutex()
    private var autosave: Job? = null

    suspend fun open(uri: String, raw: String) = operations.withLock {
        if (mutableSession.value?.uri != uri) {
            mutableSession.value = repository.draft(uri) ?: EditorSession(uri, raw, raw)
        }
    }

    fun edit(text: String) {
        val current = mutableSession.value ?: return
        mutableSession.value = current.copy(text = text)
        // Queue draft writes in order; they survive Activity recreation.
        viewModelScope.launch { operations.withLock {
            try { mutableSession.value?.let { repository.keepDraft(it) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error.value = e }
        } }
        autosave?.cancel()
        autosave = viewModelScope.launch {
            delay(700)
            // Cancelling a debounce must not cancel an in-flight SAF write.
            viewModelScope.launch { save() }
        }
    }

    suspend fun save(): Boolean = operations.withLock {
        val snapshot = mutableSession.value ?: return@withLock true
        try {
            if (snapshot.dirty) repository.save(snapshot)
            else repository.discardDraft(snapshot.uri)
            val latest = mutableSession.value
            if (latest?.uri == snapshot.uri) mutableSession.value = latest.copy(original = snapshot.text)
            error.value = null
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error.value = e; false }
    }

    fun flush() { viewModelScope.launch { save() } }

    suspend fun close() = operations.withLock {
        autosave?.cancel()
        mutableSession.value = null
    }

    suspend fun discard() = operations.withLock {
        autosave?.cancel()
        mutableSession.value?.let { repository.discardDraft(it.uri) }
        mutableSession.value = null
        error.value = null
    }
}
