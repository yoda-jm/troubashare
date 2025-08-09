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
                // Handle drawing gestures for pen/highlighter tools when in drawing mode
                // Handle text tool tap gestures
                if (drawingState.isDrawing && drawingState.tool == DrawingTool.TEXT) {
                    detectTapGestures { offset ->
                        onDrawingStateChanged(
                            drawingState.copy(
                                showTextDialog = true,
                                textDialogPosition = offset
                            )
                        )
                    }
                } else if (drawingState.isDrawing && 
                    (drawingState.tool == DrawingTool.PEN || 
                     drawingState.tool == DrawingTool.HIGHLIGHTER ||
                     drawingState.tool == DrawingTool.ERASER)) {
                    
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
                                    color = drawingState.color.toArgb().toLong(),
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
                // For PAN_ZOOM tool, don't intercept gestures - let the parent handle them
            }
    ) {
        // Draw existing annotation strokes (background handled by parent)
        val allStrokes = annotations.flatMap { it.strokes } + localStrokes
        allStrokes.forEach { stroke ->
            if (stroke.points.isNotEmpty()) {
                val path = createPathFromPoints(stroke.points)
                val strokeColor = try {
                    Color(stroke.color.toULong())
                } catch (e: Exception) {
                    Color.Red // Fallback color
                }
                
                when (stroke.tool) {
                    DrawingTool.PEN -> {
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
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = text,
                                    topLeft = Offset(position.x, position.y),
                                    style = TextStyle(
                                        color = strokeColor,
                                        fontSize = (stroke.strokeWidth * 4).sp
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