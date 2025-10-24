package com.troubashare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
    var effectiveWidth by remember { mutableFloatStateOf(0f) }
    var effectiveHeight by remember { mutableFloatStateOf(0f) }
    var pdfOffsetX by remember { mutableFloatStateOf(0f) }
    var pdfOffsetY by remember { mutableFloatStateOf(0f) }
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(drawingState.tool, drawingState.isDrawing, drawingState.color, drawingState.strokeWidth) {
                when (drawingState.tool) {
                    DrawingTool.TEXT -> {
                        // Handle text annotation placement
                        detectTapGestures { offset ->
                            // CONVERT to PDF-relative position for text dialog
                            val pdfRelativeOffset = Offset(
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
                        // Handle tap-to-select and drag-to-move
                        detectTapGestures(
                            onTap = { tapOffset ->
                                // Tap to select a stroke
                                val allStrokes = annotations.flatMap { it.strokes } + localStrokes
                                val tappedStroke = findStrokeAtPoint(allStrokes, tapOffset, effectiveWidth, effectiveHeight, pdfOffsetX, pdfOffsetY)

                                onDrawingStateChanged(
                                    drawingState.copy(
                                        selectedStroke = if (drawingState.selectedStroke?.id == tappedStroke?.id) {
                                            null // Deselect if tapping same stroke
                                        } else {
                                            tappedStroke // Select the tapped stroke
                                        }
                                    )
                                )
                            }
                        )

                        // Drag gesture for moving selected stroke
                        if (drawingState.selectedStroke != null && onStrokeUpdated != null) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    // Verify the selected stroke is at this position
                                    val allStrokes = annotations.flatMap { it.strokes } + localStrokes
                                    val strokeAtPoint = findStrokeAtPoint(allStrokes, startOffset, effectiveWidth, effectiveHeight, pdfOffsetX, pdfOffsetY)

                                    if (strokeAtPoint?.id == drawingState.selectedStroke.id) {
                                        // Store starting position for drag calculation
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
                                    if (currentPoints.isNotEmpty()) {
                                        change.consume()
                                        val startOffset = Offset(currentPoints.first().x, currentPoints.first().y)
                                        val translation = change.position - startOffset

                                        // Calculate PDF-relative translation
                                        val pdfTranslationX = translation.x / effectiveWidth
                                        val pdfTranslationY = translation.y / effectiveHeight

                                        // Update the selected stroke position
                                        val updatedStroke = drawingState.selectedStroke.copy(
                                            points = drawingState.selectedStroke.points.map { point ->
                                                point.copy(
                                                    x = point.x + pdfTranslationX,
                                                    y = point.y + pdfTranslationY
                                                )
                                            }
                                        )

                                        onStrokeUpdated.invoke(drawingState.selectedStroke, updatedStroke)

                                        // Update drawing state with moved stroke
                                        onDrawingStateChanged(drawingState.copy(selectedStroke = updatedStroke))

                                        // Update start position for next drag calculation
                                        currentPoints = listOf(
                                            AnnotationPoint(
                                                x = change.position.x,
                                                y = change.position.y,
                                                pressure = 1f,
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                },
                                onDragEnd = {
                                    currentPoints = emptyList()
                                }
                            )
                        }
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
                        // Draw bounding box for selected stroke
                        if (isSelected) {
                            val bounds = calculateStrokeBounds(canvasPoints)
                            drawRect(
                                color = Color.Blue,
                                topLeft = bounds.topLeft,
                                size = bounds.size,
                                style = Stroke(
                                    width = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                            // Draw corner handles
                            drawCornerHandles(bounds.topLeft, bounds.size)
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
                        // Draw bounding box for selected stroke
                        if (isSelected) {
                            val bounds = calculateStrokeBounds(canvasPoints)
                            drawRect(
                                color = Color.Blue,
                                topLeft = bounds.topLeft,
                                size = bounds.size,
                                style = Stroke(
                                    width = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                            // Draw corner handles
                            drawCornerHandles(bounds.topLeft, bounds.size)
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
                        // Draw bounding box for selected stroke
                        if (isSelected) {
                            val bounds = calculateStrokeBounds(canvasPoints)
                            drawRect(
                                color = Color.Red,  // Red for eraser strokes
                                topLeft = bounds.topLeft,
                                size = bounds.size,
                                style = Stroke(
                                    width = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                            // Draw corner handles
                            drawCornerHandles(bounds.topLeft, bounds.size)
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

                                // Draw bounding box for selected text
                                if (isSelected) {
                                    val textWidth = text.length * fontSize.value * 0.6f // Approximate text width
                                    val textHeight = fontSize.value * 1.2f

                                    val bounds = androidx.compose.ui.geometry.Rect(
                                        offset = Offset(position.x - 8f, position.y - 8f),
                                        size = androidx.compose.ui.geometry.Size(textWidth + 16f, textHeight + 16f)
                                    )

                                    // Draw bounding box with dashed border
                                    drawRect(
                                        color = Color.Blue,
                                        topLeft = bounds.topLeft,
                                        size = bounds.size,
                                        style = Stroke(
                                            width = 2f,
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                        )
                                    )

                                    // Draw corner handles
                                    drawCornerHandles(bounds.topLeft, bounds.size)

                                    // Draw semi-transparent background
                                    drawRect(
                                        color = Color.Blue.copy(alpha = 0.1f),
                                        topLeft = bounds.topLeft,
                                        size = bounds.size
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

private fun createPathFromPoints(points: List<AnnotationPoint>): Path {
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

/**
 * Find the best matching stroke at a tap point using intelligent scoring.
 * Considers:
 * - Distance to tap point (closer is better)
 * - Stroke size (smaller/more precise strokes preferred)
 * - Stroke density (points closer together = more likely to be tapped intentionally)
 * - Z-order (newer strokes on top preferred slightly)
 */
private fun findStrokeAtPoint(
    strokes: List<AnnotationStroke>,
    point: Offset,
    effectiveWidth: Float,
    effectiveHeight: Float,
    pdfOffsetX: Float,
    pdfOffsetY: Float
): AnnotationStroke? {
    val hitRadius = 50f // Maximum distance to consider

    // Score each stroke and pick the best match
    data class StrokeCandidate(
        val stroke: AnnotationStroke,
        val score: Float,
        val minDistance: Float
    )

    val candidates = strokes.mapNotNull { stroke ->
        when (stroke.tool) {
            DrawingTool.TEXT -> {
                // For text, check if point is within text bounds
                if (stroke.points.isNotEmpty()) {
                    val pdfPosition = stroke.points.first()
                    val canvasPosition = Offset(
                        x = pdfPosition.x * effectiveWidth + pdfOffsetX,
                        y = pdfPosition.y * effectiveHeight + pdfOffsetY
                    )
                    val textBounds = androidx.compose.ui.geometry.Rect(
                        offset = canvasPosition,
                        size = androidx.compose.ui.geometry.Size(
                            width = (stroke.text?.length ?: 0) * stroke.strokeWidth * 2,
                            height = stroke.strokeWidth * 4
                        )
                    )

                    if (textBounds.contains(point)) {
                        // Calculate distance to center of text
                        val centerX = textBounds.left + textBounds.width / 2f
                        val centerY = textBounds.top + textBounds.height / 2f
                        val distanceToCenter = kotlin.math.sqrt(
                            (point.x - centerX) * (point.x - centerX) +
                            (point.y - centerY) * (point.y - centerY)
                        )

                        // Text has high priority when directly tapped
                        val score = 1000f - distanceToCenter
                        StrokeCandidate(stroke, score, distanceToCenter)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

            DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.ERASER -> {
                // Find minimum distance to any point on the stroke
                var minDistance = Float.MAX_VALUE
                var pointsWithinRadius = 0

                stroke.points.forEach { strokePoint ->
                    val canvasX = strokePoint.x * effectiveWidth + pdfOffsetX
                    val canvasY = strokePoint.y * effectiveHeight + pdfOffsetY
                    val distance = kotlin.math.sqrt(
                        (point.x - canvasX) * (point.x - canvasX) +
                        (point.y - canvasY) * (point.y - canvasY)
                    )

                    if (distance < minDistance) {
                        minDistance = distance
                    }
                    if (distance <= hitRadius) {
                        pointsWithinRadius++
                    }
                }

                if (minDistance <= hitRadius) {
                    // Calculate stroke bounds to determine size
                    val canvasPoints = stroke.points.map { p ->
                        AnnotationPoint(
                            x = p.x * effectiveWidth + pdfOffsetX,
                            y = p.y * effectiveHeight + pdfOffsetY,
                            pressure = p.pressure,
                            timestamp = p.timestamp
                        )
                    }
                    val bounds = calculateStrokeBounds(canvasPoints)
                    val strokeArea = bounds.width * bounds.height

                    // Scoring factors:
                    // 1. Closer distance = higher score (inverse distance)
                    val distanceScore = (hitRadius - minDistance) / hitRadius * 100f

                    // 2. Smaller strokes = higher score (prefer precise strokes)
                    val sizeScore = (1f / (1f + strokeArea / 10000f)) * 50f

                    // 3. More points within radius = higher score (indicates user tapped on dense part)
                    val densityScore = pointsWithinRadius * 2f

                    // 4. Stroke width penalty (prefer thinner strokes for precision)
                    val widthScore = (20f - stroke.strokeWidth) / 20f * 20f

                    val totalScore = distanceScore + sizeScore + densityScore + widthScore

                    StrokeCandidate(stroke, totalScore, minDistance)
                } else {
                    null
                }
            }

            else -> null
        }
    }

    // Return the stroke with the highest score
    return candidates.maxByOrNull { it.score }?.stroke
}

/**
 * Calculate the bounding box for a stroke's points
 */
private fun calculateStrokeBounds(points: List<AnnotationPoint>): androidx.compose.ui.geometry.Rect {
    if (points.isEmpty()) {
        return androidx.compose.ui.geometry.Rect.Zero
    }

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE

    points.forEach { point ->
        if (point.x < minX) minX = point.x
        if (point.y < minY) minY = point.y
        if (point.x > maxX) maxX = point.x
        if (point.y > maxY) maxY = point.y
    }

    // Add padding around the stroke
    val padding = 15f
    minX -= padding
    minY -= padding
    maxX += padding
    maxY += padding

    return androidx.compose.ui.geometry.Rect(
        left = minX,
        top = minY,
        right = maxX,
        bottom = maxY
    )
}

/**
 * Draw corner handles for the bounding box
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerHandles(
    topLeft: Offset,
    size: androidx.compose.ui.geometry.Size
) {
    val handleSize = 12f
    val handleColor = Color.Blue

    // Top-left corner
    drawCircle(
        color = handleColor,
        radius = handleSize / 2,
        center = topLeft
    )

    // Top-right corner
    drawCircle(
        color = handleColor,
        radius = handleSize / 2,
        center = Offset(topLeft.x + size.width, topLeft.y)
    )

    // Bottom-left corner
    drawCircle(
        color = handleColor,
        radius = handleSize / 2,
        center = Offset(topLeft.x, topLeft.y + size.height)
    )

    // Bottom-right corner
    drawCircle(
        color = handleColor,
        radius = handleSize / 2,
        center = Offset(topLeft.x + size.width, topLeft.y + size.height)
    )
}