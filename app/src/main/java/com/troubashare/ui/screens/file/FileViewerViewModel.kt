package com.troubashare.ui.screens.file

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.AnnotationRepository
import com.troubashare.domain.model.Annotation as DomainAnnotation
import com.troubashare.domain.model.AnnotationStroke
import com.troubashare.domain.model.AnnotationPoint
import com.troubashare.domain.model.DrawingState
import com.troubashare.domain.model.DrawingTool
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument

class FileViewerViewModel(
    private val annotationRepository: AnnotationRepository,
    private val fileId: String,
    private val memberId: String
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FileViewerUiState())
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()
    
    private val _drawingState = MutableStateFlow(DrawingState())
    val drawingState: StateFlow<DrawingState> = _drawingState.asStateFlow()
    
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    
    private val _annotations = MutableStateFlow<List<DomainAnnotation>>(emptyList())
    val annotations: StateFlow<List<DomainAnnotation>> = _annotations.asStateFlow()
    
    // Current page annotations
    val currentPageAnnotations = combine(
        _annotations,
        _currentPage
    ) { annotationList, page ->
        annotationList.filter { it.pageNumber == page }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    init {
        loadAnnotations()
    }
    
    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }
    
    fun toggleDrawingMode() {
        val newIsDrawing = !_drawingState.value.isDrawing
        _drawingState.value = _drawingState.value.copy(
            isDrawing = newIsDrawing,
            // When entering drawing mode, default to PEN tool
            tool = if (newIsDrawing) {
                DrawingTool.PEN
            } else {
                _drawingState.value.tool
            }
        )
    }
    
    fun enterDrawingMode() {
        _drawingState.value = _drawingState.value.copy(
            isDrawing = true,
            tool = DrawingTool.PEN
        )
    }
    
    fun exitDrawingMode() {
        _drawingState.value = _drawingState.value.copy(
            isDrawing = false
        )
    }
    
    fun updateDrawingState(newState: DrawingState) {
        _drawingState.value = newState
    }
    
    fun addTextAnnotation(text: String, position: androidx.compose.ui.geometry.Offset) {
        if (text.isNotBlank()) {
            val stroke = AnnotationStroke(
                id = UUID.randomUUID().toString(),
                points = listOf(
                    AnnotationPoint(
                        x = position.x,
                        y = position.y,
                        pressure = 1f,
                        timestamp = System.currentTimeMillis()
                    )
                ),
                color = _drawingState.value.color.toArgb().toUInt().toLong(),
                strokeWidth = _drawingState.value.strokeWidth,
                tool = DrawingTool.TEXT,
                text = text,
                createdAt = System.currentTimeMillis()
            )
            addStroke(stroke)
        }
        
        // Close the text dialog
        _drawingState.value = _drawingState.value.copy(
            showTextDialog = false,
            textDialogPosition = null
        )
    }
    
    fun addStroke(stroke: AnnotationStroke) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Find or create annotation for current page
                val currentPageValue = _currentPage.value
                var annotation = _annotations.value.find { 
                    it.pageNumber == currentPageValue && it.memberId == memberId 
                }
                
                if (annotation == null) {
                    annotation = annotationRepository.createAnnotation(
                        fileId = fileId,
                        memberId = memberId,
                        pageNumber = currentPageValue
                    )
                }
                
                // Add stroke to annotation
                annotationRepository.addStrokeToAnnotation(annotation.id, stroke)
                
                // Reload annotations to get updated state
                loadAnnotations()
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add annotation: ${e.message}"
                )
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
                val pageAnnotations = _annotations.value.filter { 
                    it.pageNumber == currentPageValue && it.memberId == memberId 
                }
                
                pageAnnotations.forEach { annotationItem ->
                    annotationRepository.deleteAnnotation(annotationItem)
                }
                
                loadAnnotations()
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to clear annotations: ${e.message}"
                )
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
                
                // Find the annotation containing this stroke
                val annotation = _annotations.value.find { annotation ->
                    annotation.strokes.any { it.id == stroke.id }
                }
                
                annotation?.let {
                    annotationRepository.removeStrokeFromAnnotation(it.id, stroke)
                    loadAnnotations()
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete stroke: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun updateStroke(oldStroke: AnnotationStroke, newStroke: AnnotationStroke) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Find the annotation containing the old stroke
                val annotation = _annotations.value.find { annotation ->
                    annotation.strokes.any { it.id == oldStroke.id }
                }
                
                annotation?.let {
                    // Remove old stroke and add new one
                    annotationRepository.removeStrokeFromAnnotation(it.id, oldStroke)
                    annotationRepository.addStrokeToAnnotation(it.id, newStroke)
                    loadAnnotations()
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update stroke: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun showAnnotationsList() {
        _drawingState.value = _drawingState.value.copy(
            tool = DrawingTool.SELECT // Use SELECT as select mode
        )
    }
    
    suspend fun saveAnnotationsAsPdf(originalFilePath: String, outputFilePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val annotations = _annotations.value
                val pageCount = 1 // For now, assume single page. Could be extended for multi-page PDFs
                
                val pdfDocument = PdfDocument()
                
                for (page in 0 until pageCount) {
                    val pageInfo = PdfDocument.PageInfo.Builder(612, 792, page + 1).create() // Standard letter size
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    val canvas = pdfPage.canvas
                    
                    // Draw annotations for this page
                    val pageAnnotations = annotations.filter { it.pageNumber == page }
                    drawAnnotationsOnCanvas(canvas, pageAnnotations)
                    
                    pdfDocument.finishPage(pdfPage)
                }
                
                // Save the PDF
                val outputFile = File(outputFilePath)
                val fos = FileOutputStream(outputFile)
                pdfDocument.writeTo(fos)
                pdfDocument.close()
                fos.close()
                
                true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save annotations as PDF: ${e.message}"
                )
                false
            }
        }
    }
    
    private fun drawAnnotationsOnCanvas(canvas: Canvas, annotations: List<DomainAnnotation>) {
        annotations.forEach { annotation ->
            annotation.strokes.forEach { stroke ->
                if (stroke.points.isNotEmpty()) {
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
                            val path = createAndroidPath(stroke.points)
                            canvas.drawPath(path, paint)
                        }
                        DrawingTool.HIGHLIGHTER -> {
                            val paint = Paint().apply {
                                color = stroke.color.toInt()
                                alpha = 128 // 50% transparency
                                strokeWidth = stroke.strokeWidth * 2f
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                                isAntiAlias = true
                            }
                            val path = createAndroidPath(stroke.points)
                            canvas.drawPath(path, paint)
                        }
                        DrawingTool.TEXT -> {
                            stroke.text?.let { text ->
                                val position = stroke.points.first()
                                val paint = Paint().apply {
                                    color = stroke.color.toInt()
                                    textSize = maxOf(stroke.strokeWidth * 3, 14f)
                                    isAntiAlias = true
                                }
                                canvas.drawText(text, position.x, position.y, paint)
                            }
                        }
                        else -> { /* Other tools not saved to PDF */ }
                    }
                }
            }
        }
    }
    
    private fun createAndroidPath(points: List<AnnotationPoint>): Path {
        val path = Path()
        if (points.isNotEmpty()) {
            val firstPoint = points.first()
            path.moveTo(firstPoint.x, firstPoint.y)
            points.drop(1).forEach { point ->
                path.lineTo(point.x, point.y)
            }
        }
        return path
    }
    
    private fun loadAnnotations() {
        viewModelScope.launch {
            try {
                annotationRepository.getAnnotationsByFileAndMember(fileId, memberId)
                    .collect { annotationList ->
                        _annotations.value = annotationList
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to load annotations: ${e.message}"
                )
            }
        }
    }
}

data class FileViewerUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)