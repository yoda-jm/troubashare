package com.troubashare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.troubashare.domain.model.*
import java.util.*

@Composable
fun AnnotationCanvas(
    backgroundBitmap: ImageBitmap?,
    annotations: List<com.troubashare.domain.model.Annotation>,
    drawingState: DrawingState,
    onStrokeAdded: (AnnotationStroke) -> Unit,
    onDrawingStateChanged: (DrawingState) -> Unit,
    onZoomGesture: ((zoom: Float, pan: Offset) -> Unit)? = null,
    onDoubleTap: ((tapOffset: Offset, size: androidx.compose.ui.geometry.Size) -> Unit)? = null,
    onStrokeUpdated: ((old: AnnotationStroke, new: AnnotationStroke) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var currentPoints by remember { mutableStateOf<List<AnnotationPoint>>(emptyList()) }
    
    // Keep local strokes until they appear in annotations from parent
    var localStrokes by remember { mutableStateOf<List<AnnotationStroke>>(emptyList()) }
    
    // Force recomposition when drawing state changes
    LaunchedEffect(drawingState.isDrawing) {
        if (!drawingState.isDrawing) {
            currentPath = null
            currentPoints = emptyList()
        }
    }
    
    // Remove local strokes that are now in the persistent annotations
    LaunchedEffect(annotations) {
        val persistedStrokeIds = annotations.flatMap { it.strokes }.map { it.id }.toSet()
        localStrokes = localStrokes.filter { it.id !in persistedStrokeIds }
    }
    
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(drawingState.tool, drawingState.isDrawing) {
                when (drawingState.tool) {
                    DrawingTool.TEXT -> {
                        // Handle text annotation placement
                        detectTapGestures { offset ->
                            onDrawingStateChanged(
                                drawingState.copy(
                                    showTextDialog = true,
                                    textDialogPosition = offset
                                )
                            )
                        }
                    }
                    
                    DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.ERASER -> {
                        // Handle drawing gestures
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = Path().apply { 
                                    moveTo(offset.x, offset.y) 
                                }
                                currentPoints = listOf(
                                    AnnotationPoint(
                                        x = offset.x,
                                        y = offset.y,
                                        pressure = 1f,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume() // Consume touch events to prevent conflicts
                                currentPath?.let { path ->
                                    path.lineTo(change.position.x, change.position.y)
                                    currentPoints = currentPoints + AnnotationPoint(
                                        x = change.position.x,
                                        y = change.position.y,
                                        pressure = 1f,
                                        timestamp = System.currentTimeMillis()
                                    )
                                }
                            },
                            onDragEnd = {
                                if (currentPoints.isNotEmpty()) {
                                    val stroke = AnnotationStroke(
                                        id = UUID.randomUUID().toString(),
                                        points = currentPoints,
                                        color = drawingState.color.value.toLong(),
                                        strokeWidth = drawingState.strokeWidth,
                                        tool = drawingState.tool,
                                        createdAt = System.currentTimeMillis()
                                    )
                                    // Add to local strokes immediately for instant feedback
                                    localStrokes = localStrokes + stroke
                                    // Also send to parent for persistence
                                    onStrokeAdded(stroke)
                                }
                                currentPath = null
                                currentPoints = emptyList()
                            }
                        )
                    }
                    
                    DrawingTool.SELECT -> {
                        // Handle selection and movement gestures
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                // Hit test to find stroke at touch point for movement
                                val allStrokes = annotations.flatMap { it.strokes } + localStrokes
                                val selectedStroke = findStrokeAtPoint(allStrokes, startOffset)
                                if (selectedStroke != null && onStrokeUpdated != null) {
                                    // Store original stroke for movement
                                    currentPoints = listOf(
                                        AnnotationPoint(
                                            x = startOffset.x,
                                            y = startOffset.y,
                                            pressure = 1f,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                if (currentPoints.isNotEmpty() && onStrokeUpdated != null) {
                                    change.consume()
                                    val allStrokes = annotations.flatMap { it.strokes } + localStrokes
                                    val startOffset = Offset(currentPoints.first().x, currentPoints.first().y)
                                    val selectedStroke = findStrokeAtPoint(allStrokes, startOffset)
                                    
                                    selectedStroke?.let { stroke ->
                                        // Calculate translation
                                        val translation = change.position - startOffset
                                        val updatedStroke = stroke.copy(
                                            points = stroke.points.map { point ->
                                                point.copy(
                                                    x = point.x + translation.x,
                                                    y = point.y + translation.y
                                                )
                                            }
                                        )
                                        onStrokeUpdated.invoke(stroke, updatedStroke)
                                    }
                                }
                            },
                            onDragEnd = {
                                currentPoints = emptyList()
                            }
                        )
                    }
                    
                    DrawingTool.PAN_ZOOM -> {
                        // Handle pan/zoom gestures if callbacks are provided
                        if (onZoomGesture != null && onDoubleTap != null) {
                            // Combined gesture handling for pan/zoom
                            detectTransformGestures { _, pan, zoom, _ ->
                                onZoomGesture.invoke(zoom, pan)
                            }
                        }
                        if (onDoubleTap != null) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    onDoubleTap.invoke(offset, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
                                }
                            )
                        }
                    }
                }
            }
    ) {
        // Draw existing annotation strokes (background handled by parent)
        val allStrokes = annotations.flatMap { it.strokes } + localStrokes
        allStrokes.forEach { stroke ->
            val isSelected = drawingState.selectedStroke?.id == stroke.id
            if (stroke.points.isNotEmpty()) {
                val path = createPathFromPoints(stroke.points)
                val strokeColor = try {
                    Color(stroke.color.toULong())
                } catch (e: Exception) {
                    Color.Black // Fallback to black instead of red
                }
                
                when (stroke.tool) {
                    DrawingTool.PEN -> {
                        // Draw selection highlight first (behind the stroke)
                        if (isSelected) {
                            drawPath(
                                path = path,
                                color = Color.Blue.copy(alpha = 0.3f),
                                style = Stroke(
                                    width = stroke.strokeWidth + 8f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(
                                width = stroke.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    DrawingTool.HIGHLIGHTER -> {
                        // Draw selection highlight first (behind the stroke)
                        if (isSelected) {
                            drawPath(
                                path = path,
                                color = Color.Blue.copy(alpha = 0.3f),
                                style = Stroke(
                                    width = (stroke.strokeWidth * 2f) + 8f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        
                        drawPath(
                            path = path,
                            color = strokeColor.copy(alpha = 0.5f),
                            style = Stroke(
                                width = stroke.strokeWidth * 2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    DrawingTool.ERASER -> {
                        // Draw selection highlight first (behind the stroke)
                        if (isSelected) {
                            drawPath(
                                path = path,
                                color = Color.Blue.copy(alpha = 0.3f),
                                style = Stroke(
                                    width = (stroke.strokeWidth * 3f) + 8f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        
                        // For eraser, use the background color or white with alpha blending
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 1f),
                            style = Stroke(
                                width = stroke.strokeWidth * 3f, // Make eraser thicker
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    DrawingTool.SELECT -> {
                        // SELECT tool is for annotation selection/deletion - don't draw these as strokes
                        // Selection will be handled separately
                    }
                    DrawingTool.TEXT -> {
                        // Draw text annotation
                        stroke.text?.let { text ->
                            if (stroke.points.isNotEmpty()) {
                                val position = stroke.points.first()
                                // Ensure text is visible with better size and color handling
                                val textColor = if (strokeColor == Color.White || strokeColor.alpha < 0.5f) {
                                    Color.Black // Use black for better visibility if original color is white or transparent
                                } else {
                                    strokeColor
                                }
                                
                                // Draw selection highlight background for text
                                if (isSelected) {
                                    val fontSize = if (stroke.strokeWidth * 3 >= 14f) (stroke.strokeWidth * 3).sp else 14.sp
                                    val textWidth = text.length * fontSize.value * 0.6f // Approximate text width
                                    val textHeight = fontSize.value * 1.2f
                                    
                                    drawRect(
                                        color = Color.Blue.copy(alpha = 0.2f),
                                        topLeft = Offset(position.x - 4f, position.y - 4f),
                                        size = androidx.compose.ui.geometry.Size(textWidth + 8f, textHeight + 8f)
                                    )
                                }
                                
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = text,
                                    topLeft = Offset(position.x, position.y),
                                    style = TextStyle(
                                        color = textColor,
                                        fontSize = if (stroke.strokeWidth * 3 >= 14f) (stroke.strokeWidth * 3).sp else 14.sp // Minimum readable size
                                    )
                                )
                            }
                        }
                    }
                    DrawingTool.PAN_ZOOM -> {
                        // PAN_ZOOM tool doesn't create strokes
                    }
                }
            }
        }
        
        // Draw current stroke being drawn (real-time preview)
        currentPath?.let { path ->
            val strokeColor = drawingState.color
            
            when (drawingState.tool) {
                DrawingTool.PEN -> {
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(
                            width = drawingState.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                DrawingTool.HIGHLIGHTER -> {
                    drawPath(
                        path = path,
                        color = strokeColor.copy(alpha = 0.5f),
                        style = Stroke(
                            width = drawingState.strokeWidth * 2f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                DrawingTool.ERASER -> {
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 1f),
                        style = Stroke(
                            width = drawingState.strokeWidth * 3f, // Make eraser thicker
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                DrawingTool.SELECT -> {
                    // SELECT tool doesn't draw preview strokes
                }
                DrawingTool.TEXT -> {
                    // TEXT tool will have separate text input handling
                }
                DrawingTool.PAN_ZOOM -> {
                    // PAN_ZOOM tool doesn't draw strokes
                }
            }
        }
    }
}

private fun createPathFromPoints(points: List<com.troubashare.domain.model.AnnotationPoint>): Path {
    val path = Path()
    try {
        if (points.isNotEmpty()) {
            val firstPoint = points.first()
            // Check for valid coordinates
            if (firstPoint.x.isFinite() && firstPoint.y.isFinite()) {
                path.moveTo(firstPoint.x, firstPoint.y)
                points.drop(1).forEach { point ->
                    if (point.x.isFinite() && point.y.isFinite()) {
                        path.lineTo(point.x, point.y)
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Return empty path if there's an issue
        return Path()
    }
    return path
}

private fun findStrokeAtPoint(strokes: List<AnnotationStroke>, point: Offset): AnnotationStroke? {
    val hitRadius = 50f // Adjust hit detection radius as needed
    
    return strokes.reversed().firstOrNull { stroke ->
        when (stroke.tool) {
            DrawingTool.TEXT -> {
                // For text, check if point is within text bounds
                if (stroke.points.isNotEmpty()) {
                    val textPosition = stroke.points.first()
                    val textBounds = androidx.compose.ui.geometry.Rect(
                        offset = Offset(textPosition.x, textPosition.y),
                        size = androidx.compose.ui.geometry.Size(
                            width = (stroke.text?.length ?: 0) * stroke.strokeWidth * 2,
                            height = stroke.strokeWidth * 4
                        )
                    )
                    textBounds.contains(point)
                } else false
            }
            DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.ERASER -> {
                // For drawing strokes, check distance to any point on the path
                stroke.points.any { strokePoint ->
                    val distance = kotlin.math.sqrt(
                        (point.x - strokePoint.x) * (point.x - strokePoint.x) + 
                        (point.y - strokePoint.y) * (point.y - strokePoint.y)
                    )
                    distance <= hitRadius
                }
            }
            else -> false
        }
    }
}