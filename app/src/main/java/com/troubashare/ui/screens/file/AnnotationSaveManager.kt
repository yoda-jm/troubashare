package com.troubashare.ui.screens.file

import com.troubashare.data.repository.AnnotationRepository
import com.troubashare.domain.model.Annotation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AUTO_SAVE_DEBOUNCE_MS = 5_000L

/**
 * Manages auto-save debouncing and database persistence for annotations.
 * Keeps the ViewModel free of save scheduling and raw DB write logic.
 */
class AnnotationSaveManager(
    private val annotationRepository: AnnotationRepository,
    private val scope: CoroutineScope,
    private val annotations: StateFlow<List<Annotation>>,
    private val onError: (String) -> Unit
) {
    private var autoSaveJob: Job? = null

    /** Schedule a debounced save. Cancels any pending save and restarts the timer. */
    fun markDirty() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            persistAll()
        }
    }

    /** Cancel any pending debounced save and persist immediately.
     *  Uses NonCancellable so the save completes even if viewModelScope is
     *  cancelled while the screen is being disposed. */
    fun saveNow() {
        autoSaveJob?.cancel()
        scope.launch(NonCancellable) { persistAll() }
    }

    private suspend fun persistAll() {
        // NonCancellable prevents an in-progress save from being interrupted
        // mid-way (after deletes but before inserts), which would corrupt data.
        withContext(NonCancellable) {
            try {
                annotations.value.forEach { annotation ->
                    annotationRepository.saveAnnotationWithStrokes(annotation)
                }
            } catch (e: Exception) {
                onError("Failed to save annotations: ${e.message}")
            }
        }
    }
}
