package com.troubashare.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class Annotation(
    val id: String,
    val fileId: String, // References SongFile.id
    val memberId: String,
    val pageNumber: Int = 0, // For multi-page PDFs
    val strokes: List<AnnotationStroke> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AnnotationStroke(
    val id: String,
    val points: List<AnnotationPoint>,
    val color: Long = Color.Red.toArgb().toLong(),
    val strokeWidth: Float = 3f,
    val tool: DrawingTool = DrawingTool.PEN,
    val text: String? = null, // For TEXT tool annotations
    val createdAt: Long = System.currentTimeMillis()
)

data class AnnotationPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timestamp: Long = System.currentTimeMillis()
)

enum class DrawingTool(val displayName: String) {
    PEN("Pen"),
    HIGHLIGHTER("Highlighter"),
    ERASER("Eraser"),
    TEXT("Text"),
    SELECT("Select"),
    PAN_ZOOM("Pan/Zoom")
}

data class DrawingState(
    val tool: DrawingTool = DrawingTool.PEN,
    val color: Color = Color.Red,
    val strokeWidth: Float = 3f,
    val isDrawing: Boolean = false,
    val currentStroke: AnnotationStroke? = null,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val showTextDialog: Boolean = false,
    val textDialogPosition: Offset? = null
)