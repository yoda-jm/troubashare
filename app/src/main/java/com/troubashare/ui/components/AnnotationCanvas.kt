package com.troubashare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
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
    onStrokeDeleted: ((AnnotationStroke) -> Unit)? = null,
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
            // Single pointer input: Handle all tool-specific gestures
            .pointerInput(drawingState.tool, drawingState.isDrawing, drawingState.color, drawingState.strokeWidth, drawingState.opacity, drawingState.selectedStroke?.id) {
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
                    
                    DrawingTool.PEN -> {
                        // Handle drawing gestures - use awaitPointerEventScope to capture initial touch
                        awaitPointerEventScope {
                            while (true) {
                                // Wait for first down event - this captures the EXACT touch point
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPosition = down.position

                                // CONVERT to PDF-relative coordinates (0.0 to 1.0 range)
                                val pdfRelativeX = (downPosition.x - pdfOffsetX) / effectiveWidth
                                val pdfRelativeY = (downPosition.y - pdfOffsetY) / effectiveHeight

                                // Initialize with first point at EXACT touch location
                                currentPoints = listOf(
                                    AnnotationPoint(
                                        x = pdfRelativeX,
                                        y = pdfRelativeY,
                                        pressure = 1f,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )

                                // Now track drag movements
                                drag(down.id) { change ->
                                    change.consume() // Consume touch events to prevent conflicts

                                    // CONVERT drag coordinates to PDF-relative
                                    val dragPdfRelativeX = (change.position.x - pdfOffsetX) / effectiveWidth
                                    val dragPdfRelativeY = (change.position.y - pdfOffsetY) / effectiveHeight

                                    // Add point to PDF-relative coordinates list for real-time drawing
                                    currentPoints = currentPoints + AnnotationPoint(
                                        x = dragPdfRelativeX,
                                        y = dragPdfRelativeY,
                                        pressure = 1f,
                                        timestamp = System.currentTimeMillis()
                                    )
                                }

                                // Drag ended - save the stroke
                                if (currentPoints.isNotEmpty()) {
                                    val stroke = AnnotationStroke(
                                        id = UUID.randomUUID().toString(),
                                        points = currentPoints,
                                        color = drawingState.color.toArgb().toUInt().toLong(),
                                        strokeWidth = drawingState.strokeWidth,
                                        opacity = drawingState.opacity,
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
                        }
                    }

                    // Legacy tools - no drawing handler (can't create new strokes)
                    DrawingTool.HIGHLIGHTER, DrawingTool.ERASER -> {
                        // These tools are deprecated - only kept for rendering existing strokes
                        // No drawing interaction allowed
                    }
                    
                    DrawingTool.SELECT -> {
                        // Use awaitPointerEventScope for fine-grained control over tap vs drag
                        awaitPointerEventScope {
                            while (true) {
                                // Wait for first down event
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPosition = down.position
                                val downTime = System.currentTimeMillis()

                                // Find which stroke was tapped
                                val allStrokes = annotations.flatMap { it.strokes } + localStrokes
                                val tappedStroke = findStrokeAtPoint(allStrokes, downPosition, effectiveWidth, effectiveHeight, pdfOffsetX, pdfOffsetY)

                                // Check if we should prepare for drag-to-move or resize
                                var canDragSelectedStroke = false
                                var resizeHandle: ResizeHandle? = null

                                if (tappedStroke?.id == drawingState.selectedStroke?.id && drawingState.selectedStroke != null && onStrokeUpdated != null) {
                                    // Check if inside bounding box or on a resize handle
                                    val selectedStroke = drawingState.selectedStroke
                                    val canvasPoints = selectedStroke.points.map { point ->
                                        AnnotationPoint(
                                            x = point.x * effectiveWidth + pdfOffsetX,
                                            y = point.y * effectiveHeight + pdfOffsetY,
                                            pressure = point.pressure,
                                            timestamp = point.timestamp
                                        )
                                    }
                                    val bounds = calculateStrokeBounds(canvasPoints)

                                    // Check if tapping a resize handle first (priority over move)
                                    resizeHandle = detectResizeHandle(downPosition, bounds)

                                    if (resizeHandle == null) {
                                        // Not on a handle, check if inside bounds for move
                                        canDragSelectedStroke = downPosition.x >= bounds.left &&
                                                               downPosition.x <= bounds.right &&
                                                               downPosition.y >= bounds.top &&
                                                               downPosition.y <= bounds.bottom

                                        if (canDragSelectedStroke) {
                                            currentPoints = listOf(AnnotationPoint(downPosition.x, downPosition.y, 1f, downTime))
                                        }
                                    }
                                }

                                // Track drag
                                var totalDrag = 0f
                                var hasDragged = false
                                val tapThreshold = 10f
                                // Start with the current UI version (which has latest slider/color edits AND resize/move changes)
                                // We'll use this as both the starting point and to track changes
                                var currentStroke = drawingState.selectedStroke
                                // For persistence, we need the version that's in the database right now
                                // But after resize/move, drawingState already has the latest version we want to start from
                                // So we use drawingState.selectedStroke as "old" for the update operation
                                val strokeAtDragStart = drawingState.selectedStroke

                                // Store initial bounds for resize calculation
                                val initialBounds = if (resizeHandle != null && currentStroke != null) {
                                    val canvasPoints = currentStroke.points.map { point ->
                                        AnnotationPoint(
                                            x = point.x * effectiveWidth + pdfOffsetX,
                                            y = point.y * effectiveHeight + pdfOffsetY,
                                            pressure = point.pressure,
                                            timestamp = point.timestamp
                                        )
                                    }
                                    calculateStrokeBounds(canvasPoints)
                                } else null

                                // Wait for drag or release
                                val result = drag(down.id) { change ->
                                    val dragAmount = change.position - change.previousPosition
                                    totalDrag += kotlin.math.sqrt(dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y)

                                    if (totalDrag > tapThreshold) {
                                        hasDragged = true

                                        if (resizeHandle != null && currentStroke != null && initialBounds != null) {
                                            // Resize the selected stroke
                                            change.consume()

                                            val newBounds = calculateNewBounds(initialBounds, resizeHandle, change.position, downPosition)

                                            // Calculate scale factors
                                            val scaleX = newBounds.width / initialBounds.width
                                            val scaleY = newBounds.height / initialBounds.height

                                            // Get current bounds in PDF coordinates for transformation
                                            val oldStroke = currentStroke!! // Save reference before using
                                            val pdfBounds = calculateStrokeBoundsInPdfCoords(oldStroke.points)

                                            val updatedStroke = oldStroke.copy(
                                                points = oldStroke.points.map { point ->
                                                    // Scale each point relative to the bounds origin
                                                    val relativeX = (point.x - pdfBounds.left) / pdfBounds.width
                                                    val relativeY = (point.y - pdfBounds.top) / pdfBounds.height

                                                    // Calculate new bounds in PDF coordinates
                                                    val newPdfLeft = (newBounds.left - pdfOffsetX) / effectiveWidth
                                                    val newPdfTop = (newBounds.top - pdfOffsetY) / effectiveHeight
                                                    val newPdfWidth = newBounds.width / effectiveWidth
                                                    val newPdfHeight = newBounds.height / effectiveHeight

                                                    point.copy(
                                                        x = newPdfLeft + relativeX * newPdfWidth,
                                                        y = newPdfTop + relativeY * newPdfHeight
                                                    )
                                                }
                                                // Keep original strokeWidth and opacity unchanged
                                            )

                                            // Update state for live feedback (no persistence yet)
                                            onDrawingStateChanged(drawingState.copy(selectedStroke = updatedStroke))
                                            currentStroke = updatedStroke // Update our local reference

                                        } else if (canDragSelectedStroke && currentStroke != null) {
                                            // Drag the selected stroke - use currentStroke, not drawingState.selectedStroke
                                            change.consume()

                                            val pdfTranslationX = dragAmount.x / effectiveWidth
                                            val pdfTranslationY = dragAmount.y / effectiveHeight

                                            val oldStroke = currentStroke!!  // Force unwrap since we checked != null
                                            val updatedStroke = oldStroke.copy(
                                                points = oldStroke.points.map { point ->
                                                    point.copy(
                                                        x = point.x + pdfTranslationX,
                                                        y = point.y + pdfTranslationY
                                                    )
                                                }
                                            )

                                            // Update state for live feedback (no persistence yet)
                                            onDrawingStateChanged(drawingState.copy(selectedStroke = updatedStroke))
                                            currentStroke = updatedStroke // Update our local reference
                                        } else if (tappedStroke == null && onZoomGesture != null) {
                                            // Pan empty space
                                            change.consume()
                                            onZoomGesture.invoke(1f, dragAmount)
                                        }
                                    }
                                }

                                // After drag ends, persist the final position/size AND any color/width/opacity edits
                                if (hasDragged && (canDragSelectedStroke || resizeHandle != null) && currentStroke != null && strokeAtDragStart != null && onStrokeUpdated != null) {
                                    // Use the stroke state from before drag as "old" and the final UI version as "new"
                                    // This preserves all edits (color/width/opacity from before drag + position/size from drag)
                                    onStrokeUpdated.invoke(strokeAtDragStart, currentStroke!!)
                                    // Keep the updated stroke selected so we can drag/resize again without reselecting
                                    // (the onDrawingStateChanged was already called during drag with the final position/size)
                                }

                                currentPoints = emptyList()

                                // If didn't drag much, treat as tap
                                if (!hasDragged && totalDrag <= tapThreshold) {
                                    onDrawingStateChanged(
                                        drawingState.copy(
                                            selectedStroke = if (drawingState.selectedStroke?.id == tappedStroke?.id) {
                                                null // Deselect if tapping same stroke
                                            } else {
                                                tappedStroke // Select the tapped stroke (can be null to deselect)
                                            }
                                        )
                                    )
                                }
                            }
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

        // Calculate effective display area using shared utility
        val displayArea = calculateEffectiveDisplayArea(
            bitmap = backgroundBitmap,
            canvasSize = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight)
        )

        // Update state variables
        effectiveWidth = displayArea.width
        effectiveHeight = displayArea.height
        pdfOffsetX = displayArea.offsetX
        pdfOffsetY = displayArea.offsetY
        
        // Draw existing annotation strokes (background handled by parent)
        val allStrokes = annotations.flatMap { it.strokes } + localStrokes
        val selectedStroke = drawingState.selectedStroke

        // First pass: draw all strokes
        // Important: If a stroke is selected, draw it from drawingState (for live updates during drag)
        // rather than from the annotations list (which updates async from database)
        allStrokes.forEach { stroke ->
            val isSelected = selectedStroke?.id == stroke.id
            // Skip drawing the selected stroke here - we'll draw it separately from drawingState for live updates
            if (!isSelected && stroke.points.isNotEmpty()) {
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
                        // Draw the stroke path (bounding box will be drawn later on top)
                        drawPath(
                            path = path,
                            color = strokeColor.copy(alpha = stroke.opacity),
                            style = Stroke(
                                width = stroke.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    DrawingTool.HIGHLIGHTER -> {
                        // Legacy tool - use opacity from stroke or default to 0.45f
                        val opacity = if (stroke.opacity > 0f) stroke.opacity else 0.45f

                        // Draw the stroke path (bounding box will be drawn later on top)
                        drawPath(
                            path = path,
                            color = strokeColor.copy(alpha = opacity),
                            style = Stroke(
                                width = stroke.strokeWidth * 1.3f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    DrawingTool.ERASER -> {
                        // Legacy tool - keep white color with full opacity
                        // Draw the stroke path (bounding box will be drawn later on top)
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
                        // Draw text annotation (bounding box will be drawn later on top)
                        stroke.text?.let { text ->
                            if (canvasPoints.isNotEmpty()) {
                                val position = canvasPoints.first()
                                val textColor = Color.Black
                                val fontSize = maxOf(stroke.strokeWidth * 3, 18f).sp

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

        // Draw the selected stroke separately from drawingState (for live updates during drag/edit)
        if (selectedStroke != null && selectedStroke.points.isNotEmpty()) {
            // CONVERT from PDF-relative coordinates (0.0-1.0) to effective PDF display area pixels
            val canvasPoints = selectedStroke.points.map { point ->
                point.copy(
                    x = point.x * effectiveWidth + pdfOffsetX,   // PDF-relative to effective area pixels
                    y = point.y * effectiveHeight + pdfOffsetY   // PDF-relative to effective area pixels
                )
            }
            val path = createPathFromPoints(canvasPoints)
            val strokeColor = try {
                Color(selectedStroke.color.toUInt().toInt())
            } catch (e: Exception) {
                Color.Black // Fallback to black
            }

            when (selectedStroke.tool) {
                DrawingTool.PEN -> {
                    drawPath(
                        path = path,
                        color = strokeColor.copy(alpha = selectedStroke.opacity),
                        style = Stroke(
                            width = selectedStroke.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                DrawingTool.HIGHLIGHTER -> {
                    val opacity = if (selectedStroke.opacity > 0f) selectedStroke.opacity else 0.45f
                    drawPath(
                        path = path,
                        color = strokeColor.copy(alpha = opacity),
                        style = Stroke(
                            width = selectedStroke.strokeWidth * 1.3f,
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
                            width = selectedStroke.strokeWidth * 3f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                DrawingTool.TEXT -> {
                    selectedStroke.text?.let { text ->
                        if (canvasPoints.isNotEmpty()) {
                            val position = canvasPoints.first()
                            val textColor = Color.Black
                            val fontSize = maxOf(selectedStroke.strokeWidth * 3, 18f).sp

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
                else -> {}
            }
        }

        // Second pass: draw bounding box and handles on top of all strokes for selected stroke
        if (selectedStroke != null) {
            // Use selectedStroke from drawingState directly (not from allStrokes)
            if (selectedStroke.points.isNotEmpty()) {
                val canvasPoints = selectedStroke.points.map { point ->
                    point.copy(
                        x = point.x * effectiveWidth + pdfOffsetX,
                        y = point.y * effectiveHeight + pdfOffsetY
                    )
                }

                when (selectedStroke.tool) {
                    DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.ERASER -> {
                        val bounds = calculateStrokeBounds(canvasPoints)
                        val boxColor = if (selectedStroke.tool == DrawingTool.ERASER) Color.Red else Color.Blue

                        // Draw bounding box with dashed border
                        drawRect(
                            color = boxColor,
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
                    DrawingTool.TEXT -> {
                        selectedStroke.text?.let { text ->
                            val position = canvasPoints.first()
                            val fontSize = maxOf(selectedStroke.strokeWidth * 3, 18f).sp
                            val textWidth = text.length * fontSize.value * 0.6f
                            val textHeight = fontSize.value * 1.2f

                            val bounds = androidx.compose.ui.geometry.Rect(
                                offset = Offset(position.x - 8f, position.y - 8f),
                                size = androidx.compose.ui.geometry.Size(textWidth + 16f, textHeight + 16f)
                            )

                            // Draw semi-transparent background
                            drawRect(
                                color = Color.Blue.copy(alpha = 0.1f),
                                topLeft = bounds.topLeft,
                                size = bounds.size
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
                        }
                    }
                    else -> {} // No bounding box for other tools
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
                        color = strokeColor.copy(alpha = drawingState.opacity),
                        style = Stroke(
                            width = drawingState.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                // Legacy tools don't have drawing preview (deprecated)
                DrawingTool.HIGHLIGHTER, DrawingTool.ERASER -> {
                    // No preview for deprecated tools
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
