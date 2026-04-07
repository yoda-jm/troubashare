package com.troubashare.ui.screens.file

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.AnnotationRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.preferences.AnnotationPreferencesManager
import com.troubashare.data.preferences.SessionManager
import com.troubashare.domain.model.Annotation as DomainAnnotation
import com.troubashare.domain.model.AppMode
import com.troubashare.domain.model.AnnotationLayer
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
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context
) : ViewModel() {

    private val filePath: String = URLDecoder.decode(savedStateHandle["filePath"] ?: "", "UTF-8")
    private val fileId: String = savedStateHandle["fileId"] ?: ""
    private val memberId: String = savedStateHandle["memberId"] ?: ""
    private val songId: String = savedStateHandle["songId"] ?: ""

    private val fileResolver = FileResolver(songRepository, fileId, memberId, songId, filePath)
    private val preferencesManager = AnnotationPreferencesManager(context)

    /**
     * True when opened from the file pool (no member context) — admin/shared-layer view.
     * Takes priority over the session mode.
     */
    val isFileLevelView: Boolean = memberId.isBlank()

    /**
     * Effective viewing mode as a StateFlow (reacts to session changes).
     * Admin is forced when there is no member context.
     */
    val viewerMode: StateFlow<AppMode> = if (isFileLevelView) {
        MutableStateFlow(AppMode.ADMIN)
    } else {
        sessionManager.mode.map { if (isFileLevelView) AppMode.ADMIN else it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.mode.value)
    }

    /** Snapshot of the current effective mode (used internally). */
    private val currentViewerMode: AppMode get() = viewerMode.value

    /**
     * The member identity to use for layer ownership and preferences.
     *
     * In PERFORMER/CONDUCTOR mode this is the session's chosen member, NOT the nav-arg
     * memberId — because the nav arg can be any member's ID (e.g. the admin tapped on
     * Bob's file row, but the current user is Alice in Performer mode).
     *
     * In ADMIN mode the nav arg identity is used (no personal context needed anyway).
     */
    private val effectiveViewerMemberId: String
        get() = when (currentViewerMode) {
            AppMode.PERFORMER, AppMode.CONDUCTOR ->
                // Use the session identity — deliberately do NOT fall back to the nav-arg
                // memberId, which is whoever's file row was tapped (could be anyone).
                // If no session member is set, return "" so the layer filter shows
                // only shared/promoted layers rather than accidentally leaking another
                // member's personal layers.
                sessionManager.activeMemberId.value?.takeIf { it.isNotBlank() } ?: ""
            AppMode.ADMIN -> fileResolver.getEffectiveMemberId()
        }

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

    // ── Layer state ──────────────────────────────────────────────────────────

    /** All layers visible to this viewer (shared + personal where applicable). */
    private val _layers = MutableStateFlow<List<AnnotationLayer>>(emptyList())
    val layers: StateFlow<List<AnnotationLayer>> = _layers.asStateFlow()

    /** ID of the layer currently being drawn on. */
    private val _activeLayerId = MutableStateFlow<String?>(null)
    val activeLayerId: StateFlow<String?> = _activeLayerId.asStateFlow()

    /** Layer IDs the viewer has hidden. */
    private val _hiddenLayerIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenLayerIds: StateFlow<Set<String>> = _hiddenLayerIds.asStateFlow()

    val activeLayer: StateFlow<AnnotationLayer?> = combine(_layers, _activeLayerId) { layers, activeId ->
        layers.find { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Derived: visible annotations on the current page ────────────────────

    val currentPageAnnotations = combine(
        _annotations, _currentPage, _hiddenLayerIds
    ) { annotationList, page, hidden ->
        annotationList.filter { ann ->
            ann.pageNumber == page && ann.layerId !in hidden
        }
    }.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private val saveManager = AnnotationSaveManager(
        annotationRepository = annotationRepository,
        scope = viewModelScope,
        annotations = _annotations,
        onError = { msg -> _uiState.value = _uiState.value.copy(errorMessage = msg) }
    )

    init {
        if (fileId.isBlank() && filePath.isNotBlank()) {
            viewModelScope.launch {
                fileResolver.resolveFromPath()
                loadLayersAndAnnotations()
            }
        } else {
            loadLayersAndAnnotations()
        }
    }

    // ── Layer loading ────────────────────────────────────────────────────────

    private fun loadLayersAndAnnotations() {
        val fid = fileResolver.getEffectiveFileId()
        val mid = effectiveViewerMemberId

        // Restore hidden-layer prefs
        _hiddenLayerIds.value = preferencesManager.getHiddenLayerIds(fid, mid)

        viewModelScope.launch {
            // Observe layers. When the layer list changes (add/rename/delete),
            // flatMapLatest cancels the previous annotation Flow and starts a new one.
            annotationRepository.getLayersForFile(fid)
                .flatMapLatest { allLayers ->
                    // Re-read mid inside the lambda so mode changes are picked up
                    val currentMid = effectiveViewerMemberId
                    // Filter to layers this viewer can see
                    val viewerLayers = allLayers.filter { layer ->
                        when (currentViewerMode) {
                            AppMode.ADMIN      -> layer.isShared || layer.isPromoted
                            AppMode.CONDUCTOR  -> true
                            AppMode.PERFORMER  -> layer.ownerId == currentMid || layer.isShared || layer.isPromoted
                        }
                    }
                    _layers.value = viewerLayers
                    ensureValidActiveLayer(viewerLayers)

                    val layerIds = viewerLayers.map { it.id }
                    annotationRepository.getAnnotationsByLayers(layerIds)
                }
                .collect { annotations ->
                    _annotations.value = annotations
                }
        }
    }

    /**
     * Ensure [_activeLayerId] points to a valid writable layer.
     * Priority: persisted pref > current in-memory value > first writable layer.
     */
    private fun ensureValidActiveLayer(viewerLayers: List<AnnotationLayer>) {
        val fid = fileResolver.getEffectiveFileId()
        val mid = effectiveViewerMemberId
        val storedId = preferencesManager.getActiveLayerId(fid, mid)
        val currentId = _activeLayerId.value

        // A writable layer for this viewer
        fun isWritable(layer: AnnotationLayer) = when (currentViewerMode) {
            AppMode.ADMIN     -> layer.isShared
            AppMode.CONDUCTOR,
            AppMode.PERFORMER -> layer.ownerId == mid
        }

        val best = viewerLayers.find { it.id == currentId && isWritable(it) }
            ?: viewerLayers.find { it.id == storedId && isWritable(it) }
            ?: viewerLayers.firstOrNull { isWritable(it) }

        if (best != null) {
            if (_activeLayerId.value != best.id) {
                _activeLayerId.value = best.id
            }
        } else {
            // No writable layer — clear active layer so we don't accidentally
            // write to a layer the current viewer cannot edit (e.g. shared layer
            // was active from a previous ADMIN session).
            _activeLayerId.value = null
        }
    }

    // ── Layer management ─────────────────────────────────────────────────────

    fun setActiveLayer(layerId: String) {
        val layer = _layers.value.find { it.id == layerId } ?: return
        if (!canEditLayer(layerId)) return
        _activeLayerId.value = layerId
        preferencesManager.setActiveLayerId(
            fileResolver.getEffectiveFileId(), effectiveViewerMemberId, layerId
        )
    }

    fun toggleLayerVisible(layerId: String) {
        val hidden = _hiddenLayerIds.value
        val nowHidden = layerId !in hidden
        _hiddenLayerIds.value = if (nowHidden) hidden + layerId else hidden - layerId
        preferencesManager.setLayerHidden(
            fileResolver.getEffectiveFileId(),
            effectiveViewerMemberId,
            layerId,
            nowHidden
        )
    }

    fun createLayer(name: String) {
        val fid = fileResolver.getEffectiveFileId()
        val ownerId = when (currentViewerMode) {
            AppMode.ADMIN     -> SHARED_ANNOTATION_LAYER
            AppMode.PERFORMER,
            AppMode.CONDUCTOR -> effectiveViewerMemberId
        }
        val existingCount = _layers.value.count { it.ownerId == ownerId }
        viewModelScope.launch {
            try {
                val newLayer = annotationRepository.createLayer(
                    fileId = fid,
                    name = name,
                    ownerId = ownerId,
                    colorIndex = existingCount % LAYER_COLOR_COUNT,
                    displayOrder = existingCount
                )
                _activeLayerId.value = newLayer.id
                preferencesManager.setActiveLayerId(fid, fileResolver.getEffectiveMemberId(), newLayer.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to create layer: ${e.message}")
            }
        }
    }

    fun promoteLayer(layerId: String, promoted: Boolean) {
        if (!canEditLayer(layerId)) return
        viewModelScope.launch {
            try {
                annotationRepository.setLayerPromoted(layerId, promoted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to update layer: ${e.message}")
            }
        }
    }

    fun renameLayer(layerId: String, newName: String) {
        if (!canEditLayer(layerId)) return
        viewModelScope.launch {
            try {
                annotationRepository.renameLayer(layerId, newName)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to rename layer: ${e.message}")
            }
        }
    }

    fun deleteLayer(layerId: String) {
        if (!canEditLayer(layerId)) return
        // Don't delete the last writable layer
        val writableLayers = _layers.value.filter { canEditLayer(it.id) }
        if (writableLayers.size <= 1) {
            _uiState.value = _uiState.value.copy(errorMessage = "Cannot delete the last layer.")
            return
        }
        viewModelScope.launch {
            try {
                annotationRepository.deleteLayer(layerId)
                if (_activeLayerId.value == layerId) {
                    val next = _layers.value.firstOrNull { it.id != layerId && canEditLayer(it.id) }
                    _activeLayerId.value = next?.id
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to delete layer: ${e.message}")
            }
        }
    }

    /** True if this viewer can rename/delete the given layer. */
    fun canEditLayer(layerId: String): Boolean {
        val layer = _layers.value.find { it.id == layerId } ?: return false
        val mid = effectiveViewerMemberId
        return when (currentViewerMode) {
            AppMode.ADMIN     -> layer.isShared
            AppMode.PERFORMER,
            AppMode.CONDUCTOR -> layer.ownerId == mid
        }
    }

    // ── Page navigation ──────────────────────────────────────────────────────

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
        if (_drawingState.value.selectedStroke != null) {
            _drawingState.value = _drawingState.value.copy(selectedStroke = null)
        }
    }

    // ── Drawing mode ─────────────────────────────────────────────────────────

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

    // ── Stroke operations ────────────────────────────────────────────────────

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

                val activeLayerVal = _layers.value.find { it.id == _activeLayerId.value }
                    ?: return@launch  // no active layer yet (layers still loading)
                if (!canEditLayer(activeLayerVal.id)) return@launch  // ownership guard

                val currentPageValue = _currentPage.value
                var annotation = _annotations.value.find {
                    it.pageNumber == currentPageValue && it.layerId == activeLayerVal.id
                }

                if (annotation == null) {
                    annotation = annotationRepository.createAnnotation(
                        fileId = fileResolver.getEffectiveFileId(),
                        memberId = activeLayerVal.ownerId,
                        layerId = activeLayerVal.id,
                        pageNumber = currentPageValue
                    )
                    _annotations.value = _annotations.value + annotation
                }

                val updatedAnnotation = annotation.copy(strokes = annotation.strokes + stroke)
                _annotations.value = _annotations.value.map { ann ->
                    if (ann.id == annotation.id) updatedAnnotation else ann
                }

                // Save immediately — ensures DB is current for other viewers and
                // triggers Room Flow observers with the correct stroke count.
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
                val activeLayerVal = _layers.value.find { it.id == _activeLayerId.value }
                    ?: return@launch
                if (!canEditLayer(activeLayerVal.id)) return@launch  // ownership guard
                val currentPageValue = _currentPage.value
                _annotations.value = _annotations.value.filter {
                    !(it.pageNumber == currentPageValue && it.layerId == activeLayerVal.id)
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
        val annotation = _annotations.value.find { ann -> ann.strokes.any { it.id == stroke.id } }
            ?: return
        if (!canEditLayer(annotation.layerId)) return  // ownership guard
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                _annotations.value = _annotations.value.map { ann ->
                    if (ann.id == annotation.id) {
                        ann.copy(strokes = ann.strokes.filter { it.id != stroke.id })
                    } else ann
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
        if (!canEditLayer(annotation.layerId)) return  // ownership guard
        _annotations.value = _annotations.value.map { ann ->
            if (ann.id == annotation.id) {
                ann.copy(strokes = ann.strokes.map { if (it.id == oldStroke.id) newStroke else it })
            } else ann
        }
        saveManager.markDirty()
    }

    fun showAnnotationsList() {
        _drawingState.value = _drawingState.value.copy(tool = DrawingTool.SELECT)
    }

    // ── Save / export ────────────────────────────────────────────────────────

    fun saveAnnotationsNow() {
        saveManager.saveNow()
        viewModelScope.launch(NonCancellable) {
            saveAnnotationLayerSilent()
        }
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
                        errorMessage = "✅ Annotations saved ($totalStrokes strokes) but cannot export (missing song link)."
                    )
                    return@launch
                }

                val gson = Gson()
                val jsonContent = gson.toJson(mapOf(
                    "fileId" to fileResolver.getEffectiveFileId(),
                    "annotations" to annotations,
                    "createdAt" to System.currentTimeMillis(),
                    "version" to "2.0"
                ))

                val song = songRepository.getSongById(effectiveSongId)
                val effectiveFileId = fileResolver.getEffectiveFileId()
                val existingAnnotationFile = song?.files?.find { f ->
                    f.fileType == FileType.ANNOTATION &&
                    f.fileName.startsWith("annotations_${effectiveFileId}_")
                }
                val isUpdate = existingAnnotationFile != null

                val fileName = "annotations_${effectiveFileId}_${System.currentTimeMillis()}.json"
                val result = songRepository.addFileToSong(
                    songId = effectiveSongId,
                    uploadedBy = fileResolver.getEffectiveMemberId(),
                    fileName = fileName,
                    inputStream = ByteArrayInputStream(jsonContent.toByteArray()),
                    autoSelectForMember = null
                )

                if (result.isSuccess) {
                    if (existingAnnotationFile != null) {
                        songRepository.removeFileFromSong(existingAnnotationFile, cleanupAnnotations = false)
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "✅ Annotation layer ${if (isUpdate) "updated" else "saved"} ($totalStrokes strokes)"
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

    private suspend fun saveAnnotationLayerSilent() {
        try {
            val effectiveSongId = fileResolver.getEffectiveSongId()
            if (effectiveSongId.isBlank()) return
            val song = songRepository.getSongById(effectiveSongId) ?: return
            val gson = Gson()
            val effectiveFileId = fileResolver.getEffectiveFileId()

            val annotationsByLayer = _annotations.value.groupBy { it.layerId }
            for ((layerId, layerAnnotations) in annotationsByLayer) {
                if (layerAnnotations.sumOf { it.strokes.size } == 0) continue
                val ownerId = _layers.value.find { it.id == layerId }?.ownerId ?: continue

                val jsonContent = gson.toJson(mapOf(
                    "fileId" to effectiveFileId,
                    "layerId" to layerId,
                    "ownerId" to ownerId,
                    "annotations" to layerAnnotations,
                    "createdAt" to System.currentTimeMillis(),
                    "version" to "2.0"
                ))

                val existing = song.files.find { f ->
                    f.fileType == FileType.ANNOTATION &&
                    f.fileName.startsWith("annotations_${effectiveFileId}_${layerId}_")
                }
                val fileName = "annotations_${effectiveFileId}_${layerId}_${System.currentTimeMillis()}.json"
                val result = songRepository.addFileToSong(
                    songId = effectiveSongId,
                    uploadedBy = ownerId,
                    fileName = fileName,
                    inputStream = ByteArrayInputStream(jsonContent.toByteArray()),
                    autoSelectForMember = null
                )
                if (result.isSuccess && existing != null) {
                    songRepository.removeFileFromSong(existing, cleanupAnnotations = false)
                }
            }
        } catch (_: Exception) { /* silent on exit */ }
    }

    // ── PDF export ───────────────────────────────────────────────────────────

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
                FileOutputStream(File(outputFilePath)).use { fos -> pdfDocument.writeTo(fos) }
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

    companion object {
        /** Number of distinct layer colours in the UI palette. */
        const val LAYER_COLOR_COUNT = 6
        val SHARED_LAYER_ID = SHARED_ANNOTATION_LAYER
    }
}

data class FileViewerUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
