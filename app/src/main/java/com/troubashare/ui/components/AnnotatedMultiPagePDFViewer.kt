package com.troubashare.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
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
 * Displays all pages with their saved annotations in either scroll or swipe mode.
 *
 * @param useScrollMode If true, displays pages in a scrollable column. If false, uses swipe/page mode.
 * @param onPageChanged Callback invoked when current page changes (only in swipe mode). Receives current page (0-based) and total pages.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnnotatedMultiPagePDFViewer(
    filePath: String,
    fileId: String,
    memberId: String,
    annotations: List<Annotation>,
    useScrollMode: Boolean = true,
    onPageChanged: ((currentPage: Int, totalPages: Int) -> Unit)? = null,
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
                if (useScrollMode) {
                    // Scroll mode: Show all pages in a scrollable column with annotations
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background), // Theme-aware background
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        itemsIndexed(pageBitmaps) { pageIndex, bitmap ->
                            PDFPageWithAnnotations(
                                pageIndex = pageIndex,
                                bitmap = bitmap,
                                annotations = annotations
                            )
                        }
                    }
                } else {
                    // Swipe mode: Show pages one at a time with horizontal paging and vertical scroll
                    val pagerState = rememberPagerState(pageCount = { pageBitmaps.size })

                    // Report page changes to parent
                    LaunchedEffect(pagerState.currentPage, pageBitmaps.size) {
                        if (pageBitmaps.isNotEmpty()) {
                            onPageChanged?.invoke(pagerState.currentPage, pageBitmaps.size)
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) { pageIndex ->
                        // Each page is independently scrollable vertically
                        val scrollState = rememberScrollState()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(8.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            PDFPageWithAnnotations(
                                pageIndex = pageIndex,
                                bitmap = pageBitmaps[pageIndex],
                                annotations = annotations,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PDFPageWithAnnotations(
    pageIndex: Int,
    bitmap: Bitmap,
    annotations: List<Annotation>,
    modifier: Modifier = Modifier
) {
    // Get annotations for this specific page
    val pageAnnotations = annotations.filter { it.pageNumber == pageIndex }

    Surface(
        modifier = modifier
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

/**
 * Single-page PDF viewer with annotations for concert mode.
 * Shows only the specified page from a PDF.
 */
@Composable
fun AnnotatedSinglePagePDFViewer(
    filePath: String,
    fileId: String,
    memberId: String,
    pageIndex: Int,
    annotations: List<Annotation>,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath, pageIndex) {
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

                if (pageIndex >= renderer.pageCount) {
                    error = "Page not found"
                    renderer.close()
                    pfd.close()
                    return@withContext
                }

                val page = renderer.openPage(pageIndex)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )

                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                pageBitmap = bitmap
                page.close()
                renderer.close()
                pfd.close()
                isLoading = false
            }
        } catch (e: Exception) {
            error = "Error loading PDF page: ${e.message}"
            isLoading = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator()
            }
            error != null -> {
                Text(
                    text = error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            pageBitmap != null -> {
                // Scrollable single page
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    PDFPageWithAnnotations(
                        pageIndex = pageIndex,
                        bitmap = pageBitmap!!,
                        annotations = annotations,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Image viewer with annotations for concert mode.
 * Shows an image with its annotation overlay.
 */
@Composable
fun AnnotatedImageViewer(
    filePath: String,
    fileId: String,
    memberId: String,
    annotations: List<Annotation>,
    modifier: Modifier = Modifier
) {
    val file = File(filePath)
    var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Load image bitmap for aspect ratio calculation
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    imageBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                }
            } catch (e: Exception) {
                // If bitmap loading fails, we'll still show the image but overlay might not align perfectly
                imageBitmap = null
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (file.exists()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Display image
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Annotation overlay (if there are annotations for page 0 - images are single page)
                    val imageAnnotations = annotations.filter { it.pageNumber == 0 }
                    if (imageAnnotations.isNotEmpty()) {
                        AnnotationOverlayForImage(
                            annotations = imageAnnotations,
                            imageBitmap = imageBitmap,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Image file not found",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AnnotationOverlayForImage(
    annotations: List<Annotation>,
    imageBitmap: android.graphics.Bitmap? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calculate effective image display area (must match ContentScale.Fit behavior)
        // ContentScale.Fit scales to fit within bounds while maintaining aspect ratio
        val effectiveWidth: Float
        val effectiveHeight: Float
        val pdfOffsetX: Float
        val pdfOffsetY: Float

        if (imageBitmap != null) {
            val imageAspectRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
            val canvasAspectRatio = canvasWidth / canvasHeight

            if (imageAspectRatio > canvasAspectRatio) {
                // Image is wider - fit to width, letterbox top/bottom
                effectiveWidth = canvasWidth
                effectiveHeight = canvasWidth / imageAspectRatio
                pdfOffsetX = 0f
                pdfOffsetY = (canvasHeight - effectiveHeight) / 2f
            } else {
                // Image is taller - fit to height, letterbox left/right
                effectiveHeight = canvasHeight
                effectiveWidth = canvasHeight * imageAspectRatio
                pdfOffsetX = (canvasWidth - effectiveWidth) / 2f
                pdfOffsetY = 0f
            }
        } else {
            // Fallback: assume full canvas (for backward compatibility)
            effectiveWidth = canvasWidth
            effectiveHeight = canvasHeight
            pdfOffsetX = 0f
            pdfOffsetY = 0f
        }

        // For images, annotations are stored in relative coordinates (0.0-1.0)
        // We need to scale them to the effective image display area
        annotations.forEach { annotationItem ->
            annotationItem.strokes.forEach { stroke ->
                if (stroke.points.isNotEmpty()) {
                    val canvasPoints = stroke.points.map { point ->
                        point.copy(
                            x = point.x * effectiveWidth + pdfOffsetX,
                            y = point.y * effectiveHeight + pdfOffsetY
                        )
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
                                    width = stroke.strokeWidth,
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
                                    width = stroke.strokeWidth * 1.3f,
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
                                    width = stroke.strokeWidth * 3f,
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
                        else -> { /* Other tools don't render in read-only mode */ }
                    }
                }
            }
        }
    }
}
