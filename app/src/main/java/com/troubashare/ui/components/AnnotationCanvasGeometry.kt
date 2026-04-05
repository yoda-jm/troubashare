package com.troubashare.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.troubashare.domain.model.AnnotationPoint
import com.troubashare.domain.model.AnnotationStroke
import com.troubashare.domain.model.DrawingTool

internal fun createPathFromPoints(points: List<AnnotationPoint>): Path {
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
internal fun findStrokeAtPoint(
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
internal fun calculateStrokeBounds(points: List<AnnotationPoint>): androidx.compose.ui.geometry.Rect {
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
internal fun DrawScope.drawCornerHandles(
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

/**
 * Enum representing which resize handle was grabbed
 */
internal enum class ResizeHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * Detect which resize handle (if any) was tapped
 */
internal fun detectResizeHandle(
    tapPosition: Offset,
    bounds: androidx.compose.ui.geometry.Rect
): ResizeHandle? {
    val handleRadius = 20f // Hit area larger than visual handle for easier interaction

    // Check each corner
    val corners = listOf(
        ResizeHandle.TOP_LEFT to Offset(bounds.left, bounds.top),
        ResizeHandle.TOP_RIGHT to Offset(bounds.right, bounds.top),
        ResizeHandle.BOTTOM_LEFT to Offset(bounds.left, bounds.bottom),
        ResizeHandle.BOTTOM_RIGHT to Offset(bounds.right, bounds.bottom)
    )

    for ((handle, position) in corners) {
        val distance = kotlin.math.sqrt(
            (tapPosition.x - position.x) * (tapPosition.x - position.x) +
            (tapPosition.y - position.y) * (tapPosition.y - position.y)
        )
        if (distance <= handleRadius) {
            return handle
        }
    }

    return null
}

/**
 * Calculate new bounding box based on which handle is being dragged
 */
internal fun calculateNewBounds(
    initialBounds: androidx.compose.ui.geometry.Rect,
    handle: ResizeHandle,
    currentPosition: Offset,
    initialPosition: Offset
): androidx.compose.ui.geometry.Rect {
    val dragDelta = currentPosition - initialPosition

    return when (handle) {
        ResizeHandle.TOP_LEFT -> {
            androidx.compose.ui.geometry.Rect(
                left = initialBounds.left + dragDelta.x,
                top = initialBounds.top + dragDelta.y,
                right = initialBounds.right,
                bottom = initialBounds.bottom
            )
        }
        ResizeHandle.TOP_RIGHT -> {
            androidx.compose.ui.geometry.Rect(
                left = initialBounds.left,
                top = initialBounds.top + dragDelta.y,
                right = initialBounds.right + dragDelta.x,
                bottom = initialBounds.bottom
            )
        }
        ResizeHandle.BOTTOM_LEFT -> {
            androidx.compose.ui.geometry.Rect(
                left = initialBounds.left + dragDelta.x,
                top = initialBounds.top,
                right = initialBounds.right,
                bottom = initialBounds.bottom + dragDelta.y
            )
        }
        ResizeHandle.BOTTOM_RIGHT -> {
            androidx.compose.ui.geometry.Rect(
                left = initialBounds.left,
                top = initialBounds.top,
                right = initialBounds.right + dragDelta.x,
                bottom = initialBounds.bottom + dragDelta.y
            )
        }
    }
}

/**
 * Calculate stroke bounds in PDF coordinates (0.0 to 1.0 range)
 */
internal fun calculateStrokeBoundsInPdfCoords(points: List<AnnotationPoint>): androidx.compose.ui.geometry.Rect {
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

    return androidx.compose.ui.geometry.Rect(
        left = minX,
        top = minY,
        right = maxX,
        bottom = maxY
    )
}
