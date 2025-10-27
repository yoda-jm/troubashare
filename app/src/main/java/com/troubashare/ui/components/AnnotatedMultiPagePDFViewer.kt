package com.troubashare.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troubashare.domain.model.Annotation
import com.troubashare.domain.model.AnnotationPoint
import com.troubashare.domain.model.DrawingTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Multi-page PDF viewer with annotation overlay support for Concert Mode.
 * Displays all pages with their saved annotations in a scrollable column.
 */
@Composable
fun AnnotatedMultiPagePDFViewer(
    filePath: String,
    fileId: String,
    memberId: String,
    annotations: List<Annotation>,
    modifier: Modifier = Modifier
) {
    var pageCount by remember { mutableIntStateOf(0) }
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        isLoading = true
        error = null

        try {
            withContext(Dispatchers.IO) {
                val file = File(filePath)
                if (!file.exists()) {
                    error = "File not found"
                    return@withContext
                }

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount

                val bitmaps = mutableListOf<Bitmap>()

                // Render all pages at high resolution
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val pageBitmap = Bitmap.createBitmap(
                        page.width * 2, // Higher resolution for better quality
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )

                    // Fill with white background before rendering
                    val canvas = android.graphics.Canvas(pageBitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)

                    page.render(
                        pageBitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    bitmaps.add(pageBitmap)
                    page.close()
                }

                pageBitmaps = bitmaps
                renderer.close()
                pfd.close()
                isLoading = false
            }
        } catch (e: Exception) {
            error = "Error loading PDF: ${e.message}"
            isLoading = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading PDF pages...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            error != null -> {
                Text(
                    text = error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            pageBitmaps.isNotEmpty() -> {
                // Show all pages in a scrollable column with annotations
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background), // Theme-aware background
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    itemsIndexed(pageBitmaps) { pageIndex, bitmap ->
                        // Get annotations for this specific page
                        val pageAnnotations = annotations.filter { it.pageNumber == pageIndex }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            ) {
                                // PDF page image
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "PDF Page ${pageIndex + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentScale = ContentScale.FillWidth
                                )

                                // Annotation overlay - must match Image size exactly
                                if (pageAnnotations.isNotEmpty()) {
                                    AnnotationOverlayForPage(
                                        annotations = pageAnnotations,
                                        pdfBitmap = bitmap,
                                        modifier = Modifier.matchParentSize() // Match the Image size exactly
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationOverlayForPage(
    annotations: List<Annotation>,
    pdfBitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calculate effective PDF display area (must match ContentScale.FillWidth behavior)
        // ContentScale.FillWidth scales to fit width, so height scales proportionally
        val bitmapAspectRatio = pdfBitmap.width.toFloat() / pdfBitmap.height.toFloat()

        // With FillWidth: width = canvasWidth, height scales based on aspect ratio
        val effectiveWidth = canvasWidth
        val effectiveHeight = canvasWidth / bitmapAspectRatio

        // Since Canvas matchParentSize makes it same size as Image, and Image uses FillWidth,
        // the actual drawable area of the image within the canvas is:
        // - Width: full canvas width (FillWidth behavior)
        // - Height: scaled height (may be taller or shorter than canvas)

        // The Image is centered vertically if effectiveHeight < canvasHeight
        val pdfOffsetX = 0f
        val pdfOffsetY = if (effectiveHeight < canvasHeight) {
            (canvasHeight - effectiveHeight) / 2f
        } else {
            0f
        }

        // Calculate scale factor for stroke widths
        // The PDF bitmap is rendered at 2x resolution (see line 74), so we need to account for that
        // effectiveWidth is the display width, pdfBitmap.width is the rendered bitmap width (2x PDF size)
        // Scale factor = (current display width) / (original PDF width at 1x)
        val strokeScaleFactor = effectiveWidth / (pdfBitmap.width / 2f)

        // Draw annotations
        annotations.forEach { annotationItem ->
            annotationItem.strokes.forEach { stroke ->
                if (stroke.points.isNotEmpty()) {
                    // Convert from PDF-relative coordinates (0.0-1.0) to canvas pixels
                    val canvasPoints = stroke.points.map { point ->
                        val canvasX = point.x * effectiveWidth + pdfOffsetX
                        val canvasY = point.y * effectiveHeight + pdfOffsetY
                        point.copy(x = canvasX, y = canvasY)
                    }

                    val path = createPathFromPoints(canvasPoints)
                    val strokeColor = try {
                        Color(stroke.color.toUInt().toInt())
                    } catch (e: Exception) {
                        Color.Black
                    }

                    when (stroke.tool) {
                        DrawingTool.PEN -> {
                            drawPath(
                                path = path,
                                color = strokeColor.copy(alpha = stroke.opacity),
                                style = Stroke(
                                    width = stroke.strokeWidth * strokeScaleFactor,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        DrawingTool.HIGHLIGHTER -> {
                            drawPath(
                                path = path,
                                color = strokeColor.copy(alpha = stroke.opacity),
                                style = Stroke(
                                    width = stroke.strokeWidth * 1.3f * strokeScaleFactor,
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
                                    width = stroke.strokeWidth * 3f * strokeScaleFactor,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        DrawingTool.TEXT -> {
                            stroke.text?.let { text ->
                                if (canvasPoints.isNotEmpty()) {
                                    val position = canvasPoints.first()
                                    val textColor = Color.Black
                                    // Scale font size with strokeScaleFactor
                                    val fontSize = maxOf(stroke.strokeWidth * 3 * strokeScaleFactor, 18f).sp

                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = text,
                                        topLeft = androidx.compose.ui.geometry.Offset(position.x, position.y),
                                        style = TextStyle(
                                            color = textColor,
                                            fontSize = fontSize
                                        )
                                    )
                                }
                            }
                        }
                        else -> { /* Other tools don't render in read-only mode */ }
                    }
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
        return Path()
    }
    return path
}
