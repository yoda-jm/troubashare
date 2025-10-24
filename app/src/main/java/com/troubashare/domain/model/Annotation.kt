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
    val color: Long = Color.Black.toArgb().toUInt().toLong(), // Changed default to black
    val strokeWidth: Float = 5f, // Increased default stroke width
    val opacity: Float = 1f, // 0.0 to 1.0, where 0.4-0.5 is highlighter mode
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
    TEXT("Text"),
    SELECT("Select"),
    PAN_ZOOM("Pan/Zoom"),
    // Legacy tools kept for backward compatibility with existing annotations
    @Deprecated("Use PEN with opacity instead")
    HIGHLIGHTER("Highlighter"),
    @Deprecated("Use SELECT tool and delete button instead")
    ERASER("Eraser")
}

data class DrawingState(
    val tool: DrawingTool = DrawingTool.PAN_ZOOM, // Default to view mode
    val color: Color = Color.Black, // Changed default to black for better visibility
    val strokeWidth: Float = 5f, // Increased default stroke width
    val opacity: Float = 1f, // 0.0 (transparent) to 1.0 (opaque), 0.4-0.5 is "highlighter mode"
    val isDrawing: Boolean = false, // Start in view mode
    val currentStroke: AnnotationStroke? = null,
    val selectedStroke: AnnotationStroke? = null, // Currently selected stroke for editing
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val showTextDialog: Boolean = false,
    val textDialogPosition: Offset? = null
)