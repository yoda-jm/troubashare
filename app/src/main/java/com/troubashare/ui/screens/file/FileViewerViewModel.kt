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
import java.io.ByteArrayInputStream
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import com.google.gson.Gson
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.preferences.AnnotationPreferencesManager
import android.content.Context

class FileViewerViewModel(
    private val annotationRepository: AnnotationRepository,
    private val songRepository: SongRepository,
    private val fileId: String,
    private val memberId: String,
    private val songId: String,
    private val filePath: String = "",
    private val context: Context
) : ViewModel() {
    
    // Store the resolved file ID, member ID, and song ID once we look them up
    private var resolvedFileId: String? = null
    private var resolvedMemberId: String? = null
    private var resolvedSongId: String? = null
    
    // Get the effective file ID, preferring resolved ID over original
    private fun getEffectiveFileId(): String {
        return when {
            resolvedFileId != null -> {
                println("DEBUG FileViewerViewModel: Using resolved fileId: '$resolvedFileId'")
                resolvedFileId!!
            }
            fileId.isNotBlank() -> {
                println("DEBUG FileViewerViewModel: Using original fileId: '$fileId'")
                fileId
            }
            else -> {
                // Use a hash of the file path as a fallback
                val pathHash = filePath.hashCode().toString().replace("-", "n")
                val fallbackId = "path-$pathHash"
                println("DEBUG FileViewerViewModel: Using fallback path-based ID: '$fallbackId'")
                fallbackId
            }
        }
    }
    
    // Get the effective member ID, preferring resolved ID over original
    private fun getEffectiveMemberId(): String {
        return when {
            resolvedMemberId != null -> {
                println("DEBUG FileViewerViewModel: Using resolved memberId: '$resolvedMemberId'")
                resolvedMemberId!!
            }
            memberId.isNotBlank() && memberId != "current-member-id" && memberId != "unknown-member" -> {
                println("DEBUG FileViewerViewModel: Using original memberId: '$memberId'")
                memberId
            }
            else -> {
                println("DEBUG FileViewerViewModel: WARNING - No valid memberId available, using fallback")
                "fallback-member"
            }
        }
    }
    
    // Get the effective song ID, preferring resolved ID over original
    private fun getEffectiveSongId(): String {
        return when {
            resolvedSongId != null -> {
                println("DEBUG FileViewerViewModel: Using resolved songId: '$resolvedSongId'")
                resolvedSongId!!
            }
            songId.isNotBlank() -> {
                println("DEBUG FileViewerViewModel: Using original songId: '$songId'")
                songId
            }
            else -> {
                println("DEBUG FileViewerViewModel: WARNING - No valid songId available, using empty")
                ""
            }
        }
    }
    
    private val _uiState = MutableStateFlow(FileViewerUiState())
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()
    
    private val _drawingState = MutableStateFlow(DrawingState())
    val drawingState: StateFlow<DrawingState> = _drawingState.asStateFlow()
    
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
    
    private val _annotations = MutableStateFlow<List<DomainAnnotation>>(emptyList())
    val annotations: StateFlow<List<DomainAnnotation>> = _annotations.asStateFlow()
    
    private val preferencesManager = AnnotationPreferencesManager(context)
    
    // Current page annotations - filtered by preferences
    val currentPageAnnotations = combine(
        _annotations,
        _currentPage
    ) { annotationList, page ->
        val effectiveMemberId = getEffectiveMemberId()
        val effectiveFileId = getEffectiveFileId()
        
        annotationList.filter { annotation ->
            // Filter by page and member
            annotation.pageNumber == page && annotation.memberId == effectiveMemberId &&
            // Filter by visibility preferences
            preferencesManager.getAnnotationLayerVisibility(effectiveFileId, annotation.memberId)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    init {
        // First try to resolve the actual file ID from database if needed
        if (fileId.isBlank() && filePath.isNotBlank()) {
            resolveFileIdFromPath()
        } else {
            loadAnnotations()
        }
    }
    
    private fun resolveFileIdFromPath() {
        viewModelScope.launch {
            try {
                println("DEBUG: Attempting to resolve file ID from path: '$filePath'")
                // Extract songId from the file path if possible
                // Path format: .../songs/{songId}/members/{memberId}/filename
                val pathSegments = filePath.split("/")
                val songsIndex = pathSegments.indexOf("songs")
                if (songsIndex != -1 && songsIndex + 1 < pathSegments.size) {
                    val extractedSongId = pathSegments[songsIndex + 1]
                    println("DEBUG: Extracted songId from path: '$extractedSongId'")
                    
                    // Get the song and find the file with matching path
                    val song = songRepository.getSongById(extractedSongId)
                    val matchingFile = song?.files?.find { it.filePath == filePath }
                    
                    if (matchingFile != null) {
                        println("DEBUG: Found matching file in database - id='${matchingFile.id}', songId='${matchingFile.songId}', memberId='${matchingFile.memberId}'")
                        resolvedFileId = matchingFile.id
                        resolvedSongId = matchingFile.songId
                        // Also store the correct member ID if the passed one is invalid
                        if (memberId.isBlank() || memberId == "current-member-id" || memberId == "unknown-member") {
                            resolvedMemberId = matchingFile.memberId
                            println("DEBUG: Resolved memberId from database: '${matchingFile.memberId}' (original was '$memberId')")
                        }
                        println("DEBUG: Resolved songId from database: '${matchingFile.songId}' (original was '$songId')")
                    } else {
                        println("DEBUG: No matching file found in database for path: '$filePath'")
                    }
                }
                
                // Now load annotations with the resolved ID
                loadAnnotations()
                
            } catch (e: Exception) {
                println("DEBUG: Error resolving file ID from path: ${e.message}")
                // Fall back to loading with path-based ID
                loadAnnotations()
            }
        }
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
                println("DEBUG: Adding stroke with ${stroke.points.size} points, tool: ${stroke.tool}")
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Find or create annotation for current page
                val currentPageValue = _currentPage.value
                val effectiveMemberId = getEffectiveMemberId()
                var annotation = _annotations.value.find { 
                    it.pageNumber == currentPageValue && it.memberId == effectiveMemberId 
                }
                
                if (annotation == null) {
                    println("DEBUG: Creating new annotation for page $currentPageValue")
                    annotation = annotationRepository.createAnnotation(
                        fileId = getEffectiveFileId(),
                        memberId = getEffectiveMemberId(),
                        pageNumber = currentPageValue
                    )
                    println("DEBUG: Created annotation with ID: ${annotation.id}")
                }
                
                // Add stroke to annotation
                println("DEBUG: Adding stroke ${stroke.id} to annotation ${annotation.id}")
                annotationRepository.addStrokeToAnnotation(annotation.id, stroke)
                
                // Reload annotations to get updated state
                loadAnnotations()
                println("DEBUG: Stroke added successfully")
                
            } catch (e: Exception) {
                println("DEBUG: Error adding stroke: ${e.message}")
                e.printStackTrace()
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
                val effectiveMemberId = getEffectiveMemberId()
                val pageAnnotations = _annotations.value.filter { 
                    it.pageNumber == currentPageValue && it.memberId == effectiveMemberId 
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
                                strokeWidth = stroke.strokeWidth * 1.3f
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
                                if (stroke.points.isNotEmpty()) {
                                    val position = stroke.points.first()
                                    val paint = Paint().apply {
                                        color = android.graphics.Color.BLACK // Use black for maximum visibility in PDF
                                        textSize = maxOf(stroke.strokeWidth * 3, 18f) // Minimum readable size
                                        isAntiAlias = true
                                    }
                                    canvas.drawText(text, position.x, position.y, paint)
                                }
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
    
    fun saveAnnotationLayer() {
        viewModelScope.launch {
            try {
                println("DEBUG: Starting to save annotation layer...")
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Use current annotations state - don't reload as it might overwrite unsaved changes
                val annotations = _annotations.value
                println("DEBUG: Found ${annotations.size} annotations to save")
                
                // Debug each annotation
                annotations.forEachIndexed { index, annotation ->
                    println("DEBUG: Annotation $index - ID: ${annotation.id}, Page: ${annotation.pageNumber}, Strokes: ${annotation.strokes.size}")
                    annotation.strokes.forEachIndexed { strokeIndex, stroke ->
                        println("DEBUG:   Stroke $strokeIndex - Tool: ${stroke.tool}, Points: ${stroke.points.size}")
                    }
                }
                
                // Also count total strokes across all annotations for better feedback
                val totalStrokes = annotations.sumOf { it.strokes.size }
                println("DEBUG: Total strokes: $totalStrokes")
                
                if (annotations.isEmpty() || totalStrokes == 0) {
                    println("DEBUG: No annotations to save - checking if we have unsaved drawing state")
                    println("DEBUG: Current drawing state - isDrawing: ${_drawingState.value.isDrawing}, tool: ${_drawingState.value.tool}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No annotations found to save. Make sure you've drawn something and it has been saved to the database. Try drawing something, then waiting a moment before saving."
                    )
                    return@launch
                }
                
                // Create annotation layer JSON
                val gson = Gson()
                val annotationData = mapOf(
                    "fileId" to getEffectiveFileId(),
                    "originalFileId" to fileId, // Keep track of original (possibly empty) fileId
                    "memberId" to getEffectiveMemberId(),
                    "annotations" to annotations,
                    "createdAt" to System.currentTimeMillis(),
                    "version" to "1.0"
                )
                val jsonContent = gson.toJson(annotationData)
                
                // Check for existing annotation layer file to prevent duplicates
                val effectiveSongId = getEffectiveSongId()
                if (effectiveSongId.isBlank()) {
                    println("DEBUG: songId is empty - will save annotations to database but cannot create annotation file")
                    // Still save the annotation data to database using the path-based fileId
                    // But cannot create the JSON file without songId
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "✅ SUCCESS: Annotations saved to database ($totalStrokes strokes) but cannot export file (missing song link). Try re-uploading the PDF."
                    )
                    return@launch
                }
                
                // Look for existing annotation layer files for this file/member combination
                var isUpdate = false
                var existingAnnotationFile: com.troubashare.domain.model.SongFile? = null
                val song = songRepository.getSongById(effectiveSongId)
                existingAnnotationFile = song?.files?.find { file ->
                    file.fileType == com.troubashare.domain.model.FileType.ANNOTATION &&
                    file.memberId == getEffectiveMemberId() &&
                    file.fileName.startsWith("annotations_${getEffectiveFileId()}_")
                }
                
                if (existingAnnotationFile != null) {
                    println("DEBUG: Found existing annotation file '${existingAnnotationFile.fileName}', will replace it")
                    isUpdate = true
                } else {
                    println("DEBUG: No existing annotation file found, creating new one")
                }
                
                // Create filename for annotation layer (use timestamp for uniqueness but avoid conflicts)
                val fileName = "annotations_${getEffectiveFileId()}_${getEffectiveMemberId()}_${System.currentTimeMillis()}.json"
                println("DEBUG: Saving annotation file: $fileName")
                println("DEBUG: JSON content length: ${jsonContent.length}")
                
                // Save the new annotation file first
                val result = songRepository.addFileToSong(
                    songId = effectiveSongId,
                    memberId = getEffectiveMemberId(),
                    fileName = fileName,
                    inputStream = ByteArrayInputStream(jsonContent.toByteArray())
                )
                
                println("DEBUG: Save result: ${if (result.isSuccess) "SUCCESS" else "FAILED: ${result.exceptionOrNull()?.message}"}")
                
                if (result.isSuccess) {
                    // Only delete existing file AFTER successfully saving the new one
                    if (existingAnnotationFile != null) {
                        println("DEBUG: New file saved successfully, now deleting old annotation file '${existingAnnotationFile.fileName}'")
                        // Pass cleanupAnnotations = false to avoid clearing the database annotations we just saved
                        val deleteResult = songRepository.removeFileFromSong(existingAnnotationFile, cleanupAnnotations = false)
                        if (deleteResult.isFailure) {
                            println("DEBUG: Warning: Failed to delete old annotation file (new file is saved): ${deleteResult.exceptionOrNull()?.message}")
                            // Don't fail the operation - the new file is saved
                        } else {
                            println("DEBUG: Successfully deleted old annotation file")
                        }
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
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to save annotation layer: ${e.message}"
                )
            }
        }
    }
    
    private fun loadAnnotations() {
        viewModelScope.launch {
            try {
                val currentEffectiveFileId = getEffectiveFileId()
                val currentEffectiveMemberId = getEffectiveMemberId()
                println("DEBUG: Loading annotations for original fileId: '$fileId', effective fileId: '$currentEffectiveFileId', original memberId: '$memberId', effective memberId: '$currentEffectiveMemberId'")
                annotationRepository.getAnnotationsByFileAndMember(currentEffectiveFileId, currentEffectiveMemberId)
                    .collect { annotationList ->
                        println("DEBUG: Loaded ${annotationList.size} annotations from database")
                        annotationList.forEachIndexed { index, annotation ->
                            println("DEBUG: Annotation $index - ID: ${annotation.id}, Strokes: ${annotation.strokes.size}")
                        }
                        _annotations.value = annotationList
                        
                        // Log visibility preferences for debugging
                        val visibility = preferencesManager.getAnnotationLayerVisibility(currentEffectiveFileId, currentEffectiveMemberId)
                        println("DEBUG: Annotation layer visibility for fileId='$currentEffectiveFileId', memberId='$currentEffectiveMemberId': $visibility")
                    }
            } catch (e: Exception) {
                println("DEBUG: Error loading annotations: ${e.message}")
                e.printStackTrace()
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