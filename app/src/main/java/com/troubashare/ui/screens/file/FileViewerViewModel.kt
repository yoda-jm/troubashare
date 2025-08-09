package com.troubashare.ui.screens.file

import androidx.compose.ui.graphics.Color
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
import java.util.*

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
        _drawingState.value = _drawingState.value.copy(
            isDrawing = !_drawingState.value.isDrawing
        )
    }
    
    fun updateDrawingState(newState: DrawingState) {
        _drawingState.value = newState
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