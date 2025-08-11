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
    var currentPoints by remember { mutableStateOf<List<AnnotationPoint>>(emptyList()) }
    
    // Keep local strokes until they appear in annotations from parent
    var localStrokes by remember { mutableStateOf<List<AnnotationStroke>>(emptyList()) }
    
    // Force recomposition when drawing state changes
    LaunchedEffect(drawingState.isDrawing) {
        if (!drawingState.isDrawing) {
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
    
    // Calculate effective PDF display area once (will be used in both pointerInput and Canvas)
    var effectiveWidth by remember { mutableStateOf(0f) }
    var effectiveHeight by remember { mutableStateOf(0f) }
    var pdfOffsetX by remember { mutableStateOf(0f) }
    var pdfOffsetY by remember { mutableStateOf(0f) }
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(drawingState.tool, drawingState.isDrawing, drawingState.color, drawingState.strokeWidth) {
                when (drawingState.tool) {
                    DrawingTool.TEXT -> {
                        // Handle text annotation placement
                        detectTapGestures { offset ->
                            // CONVERT to PDF-relative position for text dialog
                            val pdfRelativeOffset = androidx.compose.ui.geometry.Offset(
                                x = (offset.x - pdfOffsetX) / effectiveWidth,   // 0.0 to 1.0 relative to effective PDF area
                                y = (offset.y - pdfOffsetY) / effectiveHeight  // 0.0 to 1.0 relative to effective PDF area
                            )
                            
                            onDrawingStateChanged(
                                drawingState.copy(
                                    showTextDialog = true,
                                    textDialogPosition = pdfRelativeOffset  // Use PDF-relative position
                                )
                            )
                        }
                    }
                    
                    DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.ERASER -> {
                        // Handle drawing gestures
                        detectDragGestures(
                            onDragStart = { offset ->
                                // CONVERT to PDF-relative coordinates (0.0 to 1.0 range)
                                val pdfRelativeX = (offset.x - pdfOffsetX) / effectiveWidth   // 0.0 to 1.0 relative to effective PDF area
                                val pdfRelativeY = (offset.y - pdfOffsetY) / effectiveHeight  // 0.0 to 1.0 relative to effective PDF area
                                
                                // Initialize with first point
                                currentPoints = listOf(
                                    AnnotationPoint(
                                        x = pdfRelativeX,  // Save PDF-relative coordinates (0.0-1.0)
                                        y = pdfRelativeY,
                                        pressure = 1f,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume() // Consume touch events to prevent conflicts
                                
                                // CONVERT drag coordinates to PDF-relative
                                val pdfRelativeX = (change.position.x - pdfOffsetX) / effectiveWidth
                                val pdfRelativeY = (change.position.y - pdfOffsetY) / effectiveHeight
                                
                                // Add point to PDF-relative coordinates list for real-time drawing
                                currentPoints = currentPoints + AnnotationPoint(
                                    x = pdfRelativeX,  // Save PDF-relative coordinates (0.0-1.0)
                                    y = pdfRelativeY,
                                    pressure = 1f,
                                    timestamp = System.currentTimeMillis()
                                )
                            },
                            onDragEnd = {
                                if (currentPoints.isNotEmpty()) {
                                    val stroke = AnnotationStroke(
                                        id = UUID.randomUUID().toString(),
                                        points = currentPoints,
                                        color = drawingState.color.toArgb().toUInt().toLong(),
                                        strokeWidth = drawingState.strokeWidth,
                                        tool = drawingState.tool,
                                        createdAt = System.currentTimeMillis()
                                    )
                                    // Add to local strokes immediately for instant feedback
                                    localStrokes = localStrokes + stroke
                                    // Also send to parent for persistence
                                    onStrokeAdded(stroke)
                                }
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
                                val selectedStroke = findStrokeAtPoint(allStrokes, startOffset, effectiveWidth, effectiveHeight)
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
                                    val selectedStroke = findStrokeAtPoint(allStrokes, startOffset, effectiveWidth, effectiveHeight)
                                    
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
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // Update effective PDF display area (same logic as AnnotationOverlay)
        val newEffectiveWidth: Float
        val newEffectiveHeight: Float
        val newPdfOffsetX: Float
        val newPdfOffsetY: Float
        
        if (backgroundBitmap != null) {
            val bitmapAspectRatio = backgroundBitmap.width.toFloat() / backgroundBitmap.height.toFloat()
            val canvasAspectRatio = canvasWidth / canvasHeight
            
            if (bitmapAspectRatio > canvasAspectRatio) {
                // PDF is wider - fit to width, letterbox top/bottom
                newEffectiveWidth = canvasWidth
                newEffectiveHeight = canvasWidth / bitmapAspectRatio
                newPdfOffsetX = 0f
                newPdfOffsetY = (canvasHeight - newEffectiveHeight) / 2f
            } else {
                // PDF is taller - fit to height, letterbox left/right  
                newEffectiveHeight = canvasHeight
                newEffectiveWidth = canvasHeight * bitmapAspectRatio
                newPdfOffsetX = (canvasWidth - newEffectiveWidth) / 2f
                newPdfOffsetY = 0f
            }
        } else {
            // Fallback to full canvas if no bitmap available
            newEffectiveWidth = canvasWidth
            newEffectiveHeight = canvasHeight
            newPdfOffsetX = 0f
            newPdfOffsetY = 0f
        }
        
        // Update state variables
        effectiveWidth = newEffectiveWidth
        effectiveHeight = newEffectiveHeight
        pdfOffsetX = newPdfOffsetX
        pdfOffsetY = newPdfOffsetY
        
        // Draw existing annotation strokes (background handled by parent)
        val allStrokes = annotations.flatMap { it.strokes } + localStrokes
        
        allStrokes.forEach { stroke ->
            val isSelected = drawingState.selectedStroke?.id == stroke.id
            if (stroke.points.isNotEmpty()) {
                // CONVERT from PDF-relative coordinates (0.0-1.0) to effective PDF display area pixels
                val canvasPoints = stroke.points.map { point ->
                    point.copy(
                        x = point.x * effectiveWidth + pdfOffsetX,   // PDF-relative to effective area pixels
                        y = point.y * effectiveHeight + pdfOffsetY   // PDF-relative to effective area pixels
                    )
                }
                val path = createPathFromPoints(canvasPoints)
                val strokeColor = try {
                    Color(stroke.color.toUInt().toInt())
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
                                    width = (stroke.strokeWidth * 1.3f) + 8f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        
                        drawPath(
                            path = path,
                            color = strokeColor.copy(alpha = 0.5f),
                            style = Stroke(
                                width = stroke.strokeWidth * 1.3f,
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
                            if (canvasPoints.isNotEmpty()) {
                                val position = canvasPoints.first()  // Use canvas-converted coordinates
                                // Use black color for maximum visibility in drawing mode too
                                val textColor = Color.Black
                                
                                // Ensure minimum readable size
                                val fontSize = maxOf(stroke.strokeWidth * 3, 18f).sp
                                
                                // Draw selection highlight background for text
                                if (isSelected) {
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
                                        fontSize = fontSize
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
        if (currentPoints.isNotEmpty()) {
            // Convert current PDF-relative points to canvas coordinates for drawing
            val canvasPoints = currentPoints.map { point ->
                point.copy(
                    x = point.x * effectiveWidth + pdfOffsetX,
                    y = point.y * effectiveHeight + pdfOffsetY
                )
            }
            val path = createPathFromPoints(canvasPoints)
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
                            width = drawingState.strokeWidth * 1.3f,
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

private fun findStrokeAtPoint(strokes: List<AnnotationStroke>, point: Offset, canvasWidth: Float, canvasHeight: Float): AnnotationStroke? {
    val hitRadius = 50f // Adjust hit detection radius as needed
    
    return strokes.reversed().firstOrNull { stroke ->
        when (stroke.tool) {
            DrawingTool.TEXT -> {
                // For text, check if point is within text bounds
                if (stroke.points.isNotEmpty()) {
                    val pdfPosition = stroke.points.first()
                    // Convert PDF-relative coordinates to canvas pixels
                    val canvasPosition = Offset(
                        x = pdfPosition.x * canvasWidth,
                        y = pdfPosition.y * canvasHeight
                    )
                    val textBounds = androidx.compose.ui.geometry.Rect(
                        offset = canvasPosition,
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
                    // Convert PDF-relative coordinates to canvas pixels
                    val canvasX = strokePoint.x * canvasWidth
                    val canvasY = strokePoint.y * canvasHeight
                    val distance = kotlin.math.sqrt(
                        (point.x - canvasX) * (point.x - canvasX) + 
                        (point.y - canvasY) * (point.y - canvasY)
                    )
                    distance <= hitRadius
                }
            }
            else -> false
        }
    }
}