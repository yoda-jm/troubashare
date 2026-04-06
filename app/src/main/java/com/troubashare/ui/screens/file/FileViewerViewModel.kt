package com.troubashare.ui.screens.file

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.AnnotationRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.preferences.AnnotationPreferencesManager
import com.troubashare.domain.model.Annotation as DomainAnnotation
import com.troubashare.domain.model.AnnotationStroke
import com.troubashare.domain.model.AnnotationPoint
import com.troubashare.domain.model.DrawingState
import com.troubashare.domain.model.DrawingTool
import com.troubashare.domain.model.FileType
import com.troubashare.domain.model.SHARED_ANNOTATION_LAYER
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.content.Context
import com.google.gson.Gson
import javax.inject.Inject

@HiltViewModel
class FileViewerViewModel @Inject constructor(
    private val annotationRepository: AnnotationRepository,
    private val songRepository: SongRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context
) : ViewModel() {

    private val filePath: String = URLDecoder.decode(savedStateHandle["filePath"] ?: "", "UTF-8")
    private val fileId: String = savedStateHandle["fileId"] ?: ""
    private val memberId: String = savedStateHandle["memberId"] ?: ""
    private val songId: String = savedStateHandle["songId"] ?: ""

    private val fileResolver = FileResolver(songRepository, fileId, memberId, songId, filePath)
    private val preferencesManager = AnnotationPreferencesManager(context)

    private val _uiState = MutableStateFlow(FileViewerUiState())
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()

    private val _drawingState = MutableStateFlow(
        DrawingState(
            color = Color(preferencesManager.getDrawingColor()),
            strokeWidth = preferencesManager.getDrawingStrokeWidth(),
            opacity = preferencesManager.getDrawingOpacity()
        )
    )
    val drawingState: StateFlow<DrawingState> = _drawingState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _annotations = MutableStateFlow<List<DomainAnnotation>>(emptyList())
    val annotations: StateFlow<List<DomainAnnotation>> = _annotations.asStateFlow()

    // Layer state — initialised from persisted preferences after fileResolver is ready
    private val _activeLayerIsShared = MutableStateFlow(false)
    val activeLayerIsShared: StateFlow<Boolean> = _activeLayerIsShared.asStateFlow()

    private val _showPersonalLayer = MutableStateFlow(true)
    val showPersonalLayer: StateFlow<Boolean> = _showPersonalLayer.asStateFlow()

    private val _showSharedLayer = MutableStateFlow(true)
    val showSharedLayer: StateFlow<Boolean> = _showSharedLayer.asStateFlow()

    private val saveManager = AnnotationSaveManager(
        annotationRepository = annotationRepository,
        scope = viewModelScope,
        annotations = _annotations,
        onError = { msg -> _uiState.value = _uiState.value.copy(errorMessage = msg) }
    )

    val currentPageAnnotations = combine(
        _annotations, _currentPage, _showPersonalLayer, _showSharedLayer
    ) { annotationList, page, showPersonal, showShared ->
        val effectiveMemberId = fileResolver.getEffectiveMemberId()
        annotationList.filter { annotation ->
            annotation.pageNumber == page && when (annotation.memberId) {
                effectiveMemberId -> showPersonal
                SHARED_LAYER_ID   -> showShared
                else              -> false
            }
        }
    }.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    init {
        // If fileId is blank (old-style navigation without IDs), resolve from path
        if (fileId.isBlank() && filePath.isNotBlank()) {
            viewModelScope.launch {
                fileResolver.resolveFromPath()
                restoreLayerPreferences()
                loadAnnotations()
            }
        } else {
            // IDs are already available from navigation args
            restoreLayerPreferences()
            loadAnnotations()
        }
    }

    /** True when opened from the file pool (no specific member) — shared layer only. */
    val isFileLevelView: Boolean = memberId.isBlank()

    private fun restoreLayerPreferences() {
        val fid = fileResolver.getEffectiveFileId()
        val mid = fileResolver.getEffectiveMemberId()
        if (isFileLevelView) {
            // Pool view: always draw on shared layer; personal layer irrelevant
            _activeLayerIsShared.value = true
            _showPersonalLayer.value   = false
            _showSharedLayer.value     = true
        } else {
            // Member view: always draw on personal; shared is opt-in read-only
            _activeLayerIsShared.value = false
            _showPersonalLayer.value   = preferencesManager.getAnnotationLayerVisibility(fid, mid)
            _showSharedLayer.value     = preferencesManager.getSharedLayerVisible(fid, mid)
        }
    }

    fun setActiveLayerShared(isShared: Boolean) {
        // Only allowed at file-pool level; member view always draws personal
        if (!isFileLevelView) return
        _activeLayerIsShared.value = isShared
        preferencesManager.setActiveLayerIsShared(
            fileResolver.getEffectiveFileId(), fileResolver.getEffectiveMemberId(), isShared
        )
    }

    fun setPersonalLayerVisible(visible: Boolean) {
        _showPersonalLayer.value = visible
        preferencesManager.setAnnotationLayerVisibility(
            fileResolver.getEffectiveFileId(), fileResolver.getEffectiveMemberId(), visible
        )
    }

    fun setSharedLayerVisible(visible: Boolean) {
        _showSharedLayer.value = visible
        preferencesManager.setSharedLayerVisible(
            fileResolver.getEffectiveFileId(), fileResolver.getEffectiveMemberId(), visible
        )
    }

    companion object {
        val SHARED_LAYER_ID = SHARED_ANNOTATION_LAYER
    }

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
        if (_drawingState.value.selectedStroke != null) {
            _drawingState.value = _drawingState.value.copy(selectedStroke = null)
        }
    }

    fun toggleDrawingMode() {
        val newIsDrawing = !_drawingState.value.isDrawing
        _drawingState.value = _drawingState.value.copy(
            isDrawing = newIsDrawing,
            tool = if (newIsDrawing) DrawingTool.PEN else _drawingState.value.tool
        )
    }

    fun enterDrawingMode() {
        _drawingState.value = _drawingState.value.copy(isDrawing = true, tool = DrawingTool.PEN)
    }

    fun exitDrawingMode() {
        _drawingState.value = _drawingState.value.copy(isDrawing = false)
    }

    fun updateDrawingState(newState: DrawingState) {
        val old = _drawingState.value
        _drawingState.value = newState
        if (old.color != newState.color || old.strokeWidth != newState.strokeWidth || old.opacity != newState.opacity) {
            preferencesManager.saveDrawingStyle(
                colorArgb = newState.color.toArgb(),
                strokeWidth = newState.strokeWidth,
                opacity = newState.opacity
            )
        }
    }

    fun addTextAnnotation(text: String, position: androidx.compose.ui.geometry.Offset) {
        if (text.isNotBlank()) {
            val stroke = AnnotationStroke(
                id = UUID.randomUUID().toString(),
                points = listOf(
                    AnnotationPoint(x = position.x, y = position.y, pressure = 1f, timestamp = System.currentTimeMillis())
                ),
                color = _drawingState.value.color.toArgb().toUInt().toLong(),
                strokeWidth = _drawingState.value.strokeWidth,
                opacity = _drawingState.value.opacity,
                tool = DrawingTool.TEXT,
                text = text,
                createdAt = System.currentTimeMillis()
            )
            addStroke(stroke)
        }
        _drawingState.value = _drawingState.value.copy(showTextDialog = false, textDialogPosition = null)
    }

    fun addStroke(stroke: AnnotationStroke) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val currentPageValue = _currentPage.value
                // isFileLevelView is the authoritative gate: pool view ALWAYS writes shared.
                // _activeLayerIsShared may still be false during the first frame if
                // restoreLayerPreferences() hasn't finished, so we double-guard here.
                val layerMemberId = if (isFileLevelView || _activeLayerIsShared.value) SHARED_LAYER_ID
                                    else fileResolver.getEffectiveMemberId()
                var annotation = _annotations.value.find {
                    it.pageNumber == currentPageValue && it.memberId == layerMemberId
                }

                if (annotation == null) {
                    annotation = annotationRepository.createAnnotation(
                        fileId = fileResolver.getEffectiveFileId(),
                        memberId = layerMemberId,
                        pageNumber = currentPageValue
                    )
                    _annotations.value = _annotations.value + annotation
                }

                val updatedAnnotation = annotation.copy(strokes = annotation.strokes + stroke)
                _annotations.value = _annotations.value.map { ann ->
                    if (ann.id == annotation.id) updatedAnnotation else ann
                }

                // Save immediately (no debounce) so the DB is always current.
                // This ensures the stroke is visible when opening from another context
                // (e.g. member view) and ensures Room Flow observers see correct counts.
                annotationRepository.saveAnnotationWithStrokes(updatedAnnotation)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to add annotation: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun clearAllAnnotations() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val currentPageValue = _currentPage.value
                val layerMemberId = if (isFileLevelView || _activeLayerIsShared.value) SHARED_LAYER_ID
                                    else fileResolver.getEffectiveMemberId()
                _annotations.value = _annotations.value.filter {
                    !(it.pageNumber == currentPageValue && it.memberId == layerMemberId)
                }
                saveManager.markDirty()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to clear annotations: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun deleteStroke(stroke: AnnotationStroke) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                _annotations.value = _annotations.value.map { annotation ->
                    if (annotation.strokes.any { it.id == stroke.id }) {
                        annotation.copy(strokes = annotation.strokes.filter { it.id != stroke.id })
                    } else {
                        annotation
                    }
                }
                saveManager.markDirty()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to delete stroke: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateStroke(oldStroke: AnnotationStroke, newStroke: AnnotationStroke) {
        val annotation = _annotations.value.find { ann -> ann.strokes.any { it.id == oldStroke.id } }
            ?: return

        _annotations.value = _annotations.value.map { ann ->
            if (ann.id == annotation.id) {
                ann.copy(strokes = ann.strokes.map { if (it.id == oldStroke.id) newStroke else it })
            } else {
                ann
            }
        }

        saveManager.markDirty()
    }

    fun showAnnotationsList() {
        _drawingState.value = _drawingState.value.copy(tool = DrawingTool.SELECT)
    }

    suspend fun saveAnnotationsAsPdf(originalFilePath: String, outputFilePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val annotations = _annotations.value
                val pdfDocument = PdfDocument()

                for (page in 0 until 1) {
                    val pageInfo = PdfDocument.PageInfo.Builder(612, 792, page + 1).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    drawAnnotationsOnCanvas(pdfPage.canvas, annotations.filter { it.pageNumber == page })
                    pdfDocument.finishPage(pdfPage)
                }

                FileOutputStream(File(outputFilePath)).use { fos ->
                    pdfDocument.writeTo(fos)
                }
                pdfDocument.close()
                true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save annotations as PDF: ${e.message}")
                false
            }
        }
    }

    private fun drawAnnotationsOnCanvas(canvas: Canvas, annotations: List<DomainAnnotation>) {
        annotations.forEach { annotation ->
            annotation.strokes.forEach { stroke ->
                if (stroke.points.isEmpty()) return@forEach
                when (stroke.tool) {
                    DrawingTool.PEN -> {
                        val paint = Paint().apply {
                            color = stroke.color.toInt()
                            strokeWidth = stroke.strokeWidth
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        canvas.drawPath(createAndroidPath(stroke.points), paint)
                    }
                    DrawingTool.HIGHLIGHTER -> {
                        val paint = Paint().apply {
                            color = stroke.color.toInt()
                            alpha = 128
                            strokeWidth = stroke.strokeWidth * 1.3f
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        canvas.drawPath(createAndroidPath(stroke.points), paint)
                    }
                    DrawingTool.TEXT -> {
                        stroke.text?.let { text ->
                            val position = stroke.points.first()
                            val paint = Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = maxOf(stroke.strokeWidth * 3, 18f)
                                isAntiAlias = true
                            }
                            canvas.drawText(text, position.x, position.y, paint)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun createAndroidPath(points: List<AnnotationPoint>): Path {
        val path = Path()
        if (points.isNotEmpty()) {
            path.moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { path.lineTo(it.x, it.y) }
        }
        return path
    }

    fun saveAnnotationLayer() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val annotations = _annotations.value
                val totalStrokes = annotations.sumOf { it.strokes.size }

                if (annotations.isEmpty() || totalStrokes == 0) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No annotations found to save. Draw something first."
                    )
                    return@launch
                }

                val effectiveSongId = fileResolver.getEffectiveSongId()
                if (effectiveSongId.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "✅ SUCCESS: Annotations saved to database ($totalStrokes strokes) but cannot export file (missing song link). Try re-uploading the PDF."
                    )
                    return@launch
                }

                val gson = Gson()
                val jsonContent = gson.toJson(mapOf(
                    "fileId" to fileResolver.getEffectiveFileId(),
                    "originalFileId" to fileId,
                    "memberId" to fileResolver.getEffectiveMemberId(),
                    "annotations" to annotations,
                    "createdAt" to System.currentTimeMillis(),
                    "version" to "1.0"
                ))

                val song = songRepository.getSongById(effectiveSongId)
                val existingAnnotationFile = song?.files?.find { file ->
                    file.fileType == FileType.ANNOTATION &&
                    file.uploadedBy == fileResolver.getEffectiveMemberId() &&
                    file.fileName.startsWith("annotations_${fileResolver.getEffectiveFileId()}_")
                }
                val isUpdate = existingAnnotationFile != null

                val fileName = "annotations_${fileResolver.getEffectiveFileId()}_${fileResolver.getEffectiveMemberId()}_${System.currentTimeMillis()}.json"
                val result = songRepository.addFileToSong(
                    songId = effectiveSongId,
                    uploadedBy = fileResolver.getEffectiveMemberId(),
                    fileName = fileName,
                    inputStream = ByteArrayInputStream(jsonContent.toByteArray()),
                    autoSelectForMember = null  // annotation files don't need a selection
                )

                if (result.isSuccess) {
                    if (existingAnnotationFile != null) {
                        songRepository.removeFileFromSong(existingAnnotationFile, cleanupAnnotations = false)
                    }
                    val actionText = if (isUpdate) "updated" else "saved"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "✅ SUCCESS: Annotation layer $actionText as $fileName with $totalStrokes strokes"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to save annotation layer: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to save annotation layer: ${e.message}")
            }
        }
    }

    private fun loadAnnotations() {
        val effectiveFileId = fileResolver.getEffectiveFileId()
        val effectiveMemberId = fileResolver.getEffectiveMemberId()

        // Personal layer: load once (only this session writes personal annotations).
        viewModelScope.launch {
            try {
                val personal = annotationRepository.getAnnotationsByFileAndMemberOnce(
                    effectiveFileId, effectiveMemberId
                )
                _annotations.value = personal + _annotations.value.filter { it.memberId == SHARED_LAYER_ID }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load annotations: ${e.message}")
            }
        }

        // Shared layer: observe live so any DB write (including from another session
        // or from addStroke's immediate save) is instantly reflected here.
        viewModelScope.launch {
            try {
                annotationRepository.getAnnotationsByFileAndMember(effectiveFileId, SHARED_LAYER_ID)
                    .collect { sharedAnnotations ->
                        // Keep all non-shared (personal/in-memory) annotations and replace shared.
                        _annotations.value =
                            _annotations.value.filter { it.memberId != SHARED_LAYER_ID } + sharedAnnotations
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to observe shared annotations: ${e.message}")
            }
        }
    }

    fun saveAnnotationsNow() {
        saveManager.saveNow()
        // Also refresh the annotation layer file so the song detail indicator stays current.
        // Uses NonCancellable so it completes even if viewModelScope is being torn down.
        viewModelScope.launch(NonCancellable) {
            saveAnnotationLayerSilent()
        }
    }

    /** Like saveAnnotationLayer() but with no UI state updates — safe to call on exit. */
    private suspend fun saveAnnotationLayerSilent() {
        try {
            val effectiveSongId = fileResolver.getEffectiveSongId()
            if (effectiveSongId.isBlank()) return

            val song = songRepository.getSongById(effectiveSongId) ?: return
            val gson = Gson()
            val effectiveFileId = fileResolver.getEffectiveFileId()

            // Save each distinct layer (personal + shared) that has strokes
            val annotationsByLayer = _annotations.value.groupBy { it.memberId }
            for ((layerMemberId, layerAnnotations) in annotationsByLayer) {
                if (layerAnnotations.sumOf { it.strokes.size } == 0) continue

                val jsonContent = gson.toJson(mapOf(
                    "fileId" to effectiveFileId,
                    "memberId" to layerMemberId,
                    "annotations" to layerAnnotations,
                    "createdAt" to System.currentTimeMillis(),
                    "version" to "1.0"
                ))

                val existing = song.files.find { f ->
                    f.fileType == FileType.ANNOTATION &&
                    f.uploadedBy == layerMemberId &&
                    f.fileName.startsWith("annotations_${effectiveFileId}_")
                }
                val fileName = "annotations_${effectiveFileId}_${layerMemberId}_${System.currentTimeMillis()}.json"
                val result = songRepository.addFileToSong(
                    songId = effectiveSongId,
                    uploadedBy = layerMemberId,
                    fileName = fileName,
                    inputStream = ByteArrayInputStream(jsonContent.toByteArray()),
                    autoSelectForMember = null
                )
                if (result.isSuccess && existing != null) {
                    songRepository.removeFileFromSong(existing, cleanupAnnotations = false)
                }
            }
        } catch (e: Exception) {
            // Silent on exit — no UI to show errors to
        }
    }
}

data class FileViewerUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
