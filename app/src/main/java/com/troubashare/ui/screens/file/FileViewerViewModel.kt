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

    private val saveManager = AnnotationSaveManager(
        annotationRepository = annotationRepository,
        scope = viewModelScope,
        annotations = _annotations,
        onError = { msg -> _uiState.value = _uiState.value.copy(errorMessage = msg) }
    )

    val currentPageAnnotations = combine(_annotations, _currentPage) { annotationList, page ->
        val effectiveMemberId = fileResolver.getEffectiveMemberId()
        val effectiveFileId = fileResolver.getEffectiveFileId()
        annotationList.filter { annotation ->
            annotation.pageNumber == page &&
            annotation.memberId == effectiveMemberId &&
            preferencesManager.getAnnotationLayerVisibility(effectiveFileId, annotation.memberId)
        }
    }.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    init {
        if (fileId.isBlank() && filePath.isNotBlank()) {
            viewModelScope.launch {
                fileResolver.resolveFromPath()
                loadAnnotations()
            }
        } else {
            loadAnnotations()
        }
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
                val effectiveMemberId = fileResolver.getEffectiveMemberId()
                var annotation = _annotations.value.find {
                    it.pageNumber == currentPageValue && it.memberId == effectiveMemberId
                }

                if (annotation == null) {
                    annotation = annotationRepository.createAnnotation(
                        fileId = fileResolver.getEffectiveFileId(),
                        memberId = effectiveMemberId,
                        pageNumber = currentPageValue
                    )
                    _annotations.value = _annotations.value + annotation
                }

                _annotations.value = _annotations.value.map { ann ->
                    if (ann.id == annotation.id) ann.copy(strokes = ann.strokes + stroke) else ann
                }

                saveManager.markDirty()
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
                val effectiveMemberId = fileResolver.getEffectiveMemberId()
                _annotations.value = _annotations.value.filter {
                    !(it.pageNumber == currentPageValue && it.memberId == effectiveMemberId)
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
        viewModelScope.launch {
            try {
                _annotations.value = annotationRepository.getAnnotationsByFileAndMemberOnce(
                    fileResolver.getEffectiveFileId(),
                    fileResolver.getEffectiveMemberId()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load annotations: ${e.message}")
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
            val currentAnnotations = _annotations.value
            if (currentAnnotations.isEmpty() || currentAnnotations.sumOf { it.strokes.size } == 0) return

            val effectiveSongId = fileResolver.getEffectiveSongId()
            if (effectiveSongId.isBlank()) return

            val gson = Gson()
            val jsonContent = gson.toJson(mapOf(
                "fileId" to fileResolver.getEffectiveFileId(),
                "memberId" to fileResolver.getEffectiveMemberId(),
                "annotations" to currentAnnotations,
                "createdAt" to System.currentTimeMillis(),
                "version" to "1.0"
            ))

            val song = songRepository.getSongById(effectiveSongId)
            val existingAnnotationFile = song?.files?.find { file ->
                file.fileType == FileType.ANNOTATION &&
                file.uploadedBy == fileResolver.getEffectiveMemberId() &&
                file.fileName.startsWith("annotations_${fileResolver.getEffectiveFileId()}_")
            }

            val fileName = "annotations_${fileResolver.getEffectiveFileId()}_${fileResolver.getEffectiveMemberId()}_${System.currentTimeMillis()}.json"
            val result = songRepository.addFileToSong(
                songId = effectiveSongId,
                uploadedBy = fileResolver.getEffectiveMemberId(),
                fileName = fileName,
                inputStream = ByteArrayInputStream(jsonContent.toByteArray()),
                autoSelectForMember = null  // annotation files don't need a selection
            )
            if (result.isSuccess && existingAnnotationFile != null) {
                songRepository.removeFileFromSong(existingAnnotationFile, cleanupAnnotations = false)
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
