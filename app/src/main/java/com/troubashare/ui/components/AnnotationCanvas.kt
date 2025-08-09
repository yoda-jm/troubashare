package com.troubashare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
    
    val density = LocalDensity.current
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(drawingState.isDrawing) {
                if (drawingState.isDrawing) {
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
                        onDrag = { _, dragAmount ->
                            currentPath?.let { path ->
                                val newOffset = Offset(
                                    currentPoints.last().x + dragAmount.x,
                                    currentPoints.last().y + dragAmount.y
                                )
                                
                                path.lineTo(newOffset.x, newOffset.y)
                                currentPoints = currentPoints + AnnotationPoint(
                                    x = newOffset.x,
                                    y = newOffset.y,
                                    pressure = 1f,
                                    timestamp = System.currentTimeMillis()
                                )
                            }
                        },
                        onDragEnd = {
                            currentPath?.let { path ->
                                if (currentPoints.isNotEmpty()) {
                                    val stroke = AnnotationStroke(
                                        id = UUID.randomUUID().toString(),
                                        points = currentPoints,
                                        color = drawingState.color.toArgb().toLong(),
                                        strokeWidth = drawingState.strokeWidth,
                                        tool = drawingState.tool,
                                        createdAt = System.currentTimeMillis()
                                    )
                                    onStrokeAdded(stroke)
                                }
                                currentPath = null
                                currentPoints = emptyList()
                            }
                        }
                    )
                }
            }
    ) {
        // Draw background bitmap if available
        backgroundBitmap?.let { bitmap ->
            drawImage(
                image = bitmap,
                topLeft = Offset.Zero
            )
        }
        
        // Draw existing annotation strokes
        annotations.forEach { annotationItem ->
            annotationItem.strokes.forEach { stroke ->
                if (stroke.points.isNotEmpty()) {
                    val path = createPathFromPoints(stroke.points)
                    val strokeColor = Color(stroke.color.toInt())
                    
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
                            // Eraser would need special handling - for now, draw as white
                            drawPath(
                                path = path,
                                color = Color.White,
                                style = Stroke(
                                    width = stroke.strokeWidth * 2f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // Draw current stroke being drawn
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
                        color = Color.White,
                        style = Stroke(
                            width = drawingState.strokeWidth * 2f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

private fun createPathFromPoints(points: List<AnnotationPoint>): Path {
    val path = Path()
    if (points.isNotEmpty()) {
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point ->
            path.lineTo(point.x, point.y)
        }
    }
    return path
}