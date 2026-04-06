package com.troubashare.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Synthetic memberId used for the shared/group annotation layer. */
const val SHARED_ANNOTATION_LAYER = "_shared_"

enum class AnnotationScope {
    PERSONAL,  // Only visible to the author (default)
    PART,      // Visible to all members of the author's Part (Ensemble mode only)
    ALL        // Visible to everyone in the group
}

data class Annotation(
    val id: String,
    val fileId: String, // References SongFile.id
    val memberId: String,
    val pageNumber: Int = 0,
    val scope: AnnotationScope = AnnotationScope.PERSONAL,
    val partId: String? = null, // only when scope = PART
    val strokes: List<AnnotationStroke> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AnnotationStroke(
    val id: String,
    val points: List<AnnotationPoint>,
    val color: Long = Color.Black.toArgb().toUInt().toLong(),
    val strokeWidth: Float = 5f,
    val opacity: Float = 1f,
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
    @Deprecated("Use PEN with opacity instead")
    HIGHLIGHTER("Highlighter"),
    @Deprecated("Use SELECT tool and delete button instead")
    ERASER("Eraser")
}

data class DrawingState(
    val tool: DrawingTool = DrawingTool.PAN_ZOOM,
    val color: Color = Color.Black,
    val strokeWidth: Float = 5f,
    val opacity: Float = 1f,
    val isDrawing: Boolean = false,
    val currentStroke: AnnotationStroke? = null,
    val selectedStroke: AnnotationStroke? = null,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val showTextDialog: Boolean = false,
    val textDialogPosition: Offset? = null
)
