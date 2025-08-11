package com.troubashare.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.troubashare.ui.screens.file.FileViewerViewModel
import com.troubashare.domain.model.Annotation as DomainAnnotation
import com.troubashare.domain.model.DrawingTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AnnotatableFileViewer(
    filePath: String,
    fileName: String,
    fileType: String,
    viewModel: FileViewerViewModel,
    modifier: Modifier = Modifier
) {
    when (fileType.uppercase()) {
        "PDF" -> AnnotatablePDFViewer(
            filePath = filePath,
            viewModel = viewModel,
            modifier = modifier
        )
        "JPG", "JPEG", "PNG", "GIF", "WEBP" -> AnnotatableImageViewer(
            filePath = filePath,
            fileName = fileName,
            viewModel = viewModel,
            modifier = modifier
        )
        else -> {
            // Fallback to regular file viewer
            FileViewer(filePath, fileName, fileType, modifier)
        }
    }
}

@Composable
fun AnnotatablePDFViewer(
    filePath: String,
    viewModel: FileViewerViewModel,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // PDF zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    val drawingState by viewModel.drawingState.collectAsState()
    val annotations by viewModel.currentPageAnnotations.collectAsState()
    
    // Update viewModel's current page when local page changes
    LaunchedEffect(currentPage) {
        viewModel.setCurrentPage(currentPage)
    }

    LaunchedEffect(filePath, currentPage) {
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

                if (currentPage >= pageCount) {
                    currentPage = 0
                }

                val page = renderer.openPage(currentPage)
                val pageBitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                
                page.render(
                    pageBitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                bitmap = pageBitmap
                page.close()
                renderer.close()
                pfd.close()
                isLoading = false
            }
        } catch (e: Exception) {
            error = "Error loading PDF: ${e.message}"
            isLoading = false
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    if (isLandscape) {
        // Landscape layout with sidebar
        Row(modifier = modifier.fillMaxSize()) {
            // Drawing toolbar on the left in landscape
            if (drawingState.isDrawing) {
                Surface(
                    modifier = Modifier.width(220.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    AnnotationToolbar(
                        drawingState = drawingState,
                        onDrawingStateChanged = viewModel::updateDrawingState,
                        isVertical = true,
                        modifier = Modifier.fillMaxHeight(),
                        annotations = annotations,
                        onDeleteStroke = viewModel::deleteStroke,
                        onSaveAnnotations = {
                            // TODO: Implement save logic with file picker
                        }
                    )
                }
            }
            
            // PDF content - ensure it doesn't overlap with toolbar  
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds() // Prevent content from escaping bounds
                    // DEBUG: Remove conditional padding to test coordinate alignment
            ) {
                PDFContent(
                    pageCount = pageCount,
                    currentPage = currentPage,
                    onPageChanged = { currentPage = it },
                    isLoading = isLoading,
                    error = error,
                    bitmap = bitmap,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    onScaleChanged = { newScale -> scale = newScale },
                    onOffsetChanged = { newX, newY -> offsetX = newX; offsetY = newY },
                    drawingState = drawingState,
                    annotations = annotations,
                    viewModel = viewModel
                )
            }
        }
    } else {
        // Portrait layout
        Column(modifier = modifier.fillMaxSize()) {
            // PDF Controls
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0 && !isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }
                    
                    Text(
                        text = "Page ${currentPage + 1} of $pageCount",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    IconButton(
                        onClick = { if (currentPage < pageCount - 1) currentPage++ },
                        enabled = currentPage < pageCount - 1 && !isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                }
            }
            
            // Drawing toolbar on top in portrait (only when drawing)
            if (drawingState.isDrawing) {
                AnnotationToolbar(
                    drawingState = drawingState,
                    onDrawingStateChanged = viewModel::updateDrawingState,
                    isVertical = false,
                    annotations = annotations,
                    onDeleteStroke = viewModel::deleteStroke,
                    onSaveAnnotations = {
                        // TODO: Implement save logic with file picker
                    }
                )
            }
            
            // PDF content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds() // Prevent content from escaping bounds
            ) {
                PDFContent(
                    pageCount = pageCount,
                    currentPage = currentPage,
                    onPageChanged = { currentPage = it },
                    isLoading = isLoading,
                    error = error,
                    bitmap = bitmap,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    onScaleChanged = { newScale -> scale = newScale },
                    onOffsetChanged = { newX, newY -> offsetX = newX; offsetY = newY },
                    drawingState = drawingState,
                    annotations = annotations,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Drawing mode toggle and tools FAB
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Save annotation layer FAB (when in drawing mode - always show to enable saving)
                    if (drawingState.isDrawing) {
                        FloatingActionButton(
                            onClick = {
                                viewModel.saveAnnotationLayer()
                            },
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Annotation Layer"
                            )
                        }
                    }
                    
                    // Expandable tools FAB (only when in drawing mode)
                    if (drawingState.isDrawing) {
                        ExpandableToolsFab(
                            drawingState = drawingState,
                            onDrawingStateChanged = viewModel::updateDrawingState
                        )
                    }
                    
                    // Main drawing mode toggle FAB
                    FloatingActionButton(
                        onClick = viewModel::toggleDrawingMode,
                        containerColor = if (drawingState.isDrawing)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = if (drawingState.isDrawing) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = if (drawingState.isDrawing) "Exit Drawing Mode" else "Enter Drawing Mode"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PDFContent(
    pageCount: Int,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    isLoading: Boolean,
    error: String?,
    bitmap: Bitmap?,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChanged: (Float) -> Unit,
    onOffsetChanged: (Float, Float) -> Unit,
    drawingState: com.troubashare.domain.model.DrawingState,
    annotations: List<com.troubashare.domain.model.Annotation>,
    viewModel: FileViewerViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator()
            }
            error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            bitmap != null -> {
                var currentScale by remember { mutableFloatStateOf(scale) }
                var currentOffsetX by remember { mutableFloatStateOf(offsetX) }
                var currentOffsetY by remember { mutableFloatStateOf(offsetY) }
                
                LaunchedEffect(currentScale, currentOffsetX, currentOffsetY) {
                    onScaleChanged(currentScale)
                    onOffsetChanged(currentOffsetX, currentOffsetY)
                }
                
                Box(modifier = Modifier.fillMaxSize()) {
                    // PDF base layer with zoom/pan gestures when not drawing
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = currentScale,
                                scaleY = currentScale,
                                translationX = currentOffsetX,
                                translationY = currentOffsetY
                            )
                            .let { baseModifier ->
                                if (!drawingState.isDrawing) {
                                    // Add zoom/pan gestures when not in drawing mode
                                    baseModifier.pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newScale = (currentScale * zoom).coerceIn(0.5f, 3f)
                                            currentScale = newScale
                                            // Adjust pan sensitivity based on current scale for more natural feel
                                            currentOffsetX += pan.x * currentScale
                                            currentOffsetY += pan.y * currentScale
                                        }
                                    }.pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { tapOffset ->
                                                if (currentScale > 1f) {
                                                    currentScale = 1f
                                                    currentOffsetX = 0f
                                                    currentOffsetY = 0f
                                                } else {
                                                    currentScale = 2f
                                                    currentOffsetX = (size.width / 2f - tapOffset.x) * (currentScale - 1f)
                                                    currentOffsetY = (size.height / 2f - tapOffset.y) * (currentScale - 1f)
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    baseModifier
                                }
                            }
                    ) {
                        // Display PDF bitmap
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF Page",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit // Ensure PDF maintains aspect ratio
                        )
                    }
                    
                    // Annotation layer only when drawing mode is active
                    if (drawingState.isDrawing) {
                        AnnotationCanvas(
                            backgroundBitmap = bitmap?.asImageBitmap(),
                            annotations = annotations,
                            drawingState = drawingState,
                            onStrokeAdded = viewModel::addStroke,
                            onDrawingStateChanged = viewModel::updateDrawingState,
                            onStrokeUpdated = viewModel::updateStroke,
                            onZoomGesture = { zoom, pan ->
                                val newScale = (currentScale * zoom).coerceIn(0.5f, 3f)
                                currentScale = newScale
                                // Adjust pan sensitivity based on current scale for more natural feel
                                currentOffsetX += pan.x * currentScale
                                currentOffsetY += pan.y * currentScale
                            },
                            onDoubleTap = { tapOffset, canvasSize ->
                                if (currentScale > 1f) {
                                    currentScale = 1f
                                    currentOffsetX = 0f
                                    currentOffsetY = 0f
                                } else {
                                    currentScale = 2f
                                    currentOffsetX = (canvasSize.width / 2f - tapOffset.x) * (currentScale - 1f)
                                    currentOffsetY = (canvasSize.height / 2f - tapOffset.y) * (currentScale - 1f)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = currentScale,
                                    scaleY = currentScale,
                                    translationX = currentOffsetX,
                                    translationY = currentOffsetY
                                )
                        )
                    } else {
                        // Show annotations overlay when not in drawing mode
                        val configuration = LocalConfiguration.current
                        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        
                        // ROOT CAUSE FIX: No offset needed since coordinates are now normalized
                        val toolbarOffsetX = 0f
                        
                        AnnotationOverlay(
                            annotations = annotations,
                            toolbarOffsetX = toolbarOffsetX,
                            pdfBitmap = bitmap,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = currentScale,
                                    scaleY = currentScale,
                                    translationX = currentOffsetX,
                                    translationY = currentOffsetY
                                )
                        )
                    }
                    
                    // Drawing mode toggle and tools FAB
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Save annotation layer FAB (when in drawing mode - always show to enable saving)
                        if (drawingState.isDrawing) {
                            FloatingActionButton(
                                onClick = {
                                    viewModel.saveAnnotationLayer()
                                },
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save Annotation Layer"
                                )
                            }
                        }
                        
                        // Expandable tools FAB (only when in drawing mode)
                        if (drawingState.isDrawing) {
                            ExpandableToolsFab(
                                drawingState = drawingState,
                                onDrawingStateChanged = viewModel::updateDrawingState
                            )
                        }
                        
                        // Main drawing mode toggle FAB
                        FloatingActionButton(
                            onClick = viewModel::toggleDrawingMode,
                            containerColor = if (drawingState.isDrawing)
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = if (drawingState.isDrawing) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = if (drawingState.isDrawing) "Exit Drawing Mode" else "Enter Drawing Mode"
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Text input dialog
    if (drawingState.showTextDialog && drawingState.textDialogPosition != null) {
        TextInputDialog(
            onTextEntered = { text ->
                viewModel.addTextAnnotation(text, drawingState.textDialogPosition!!)
            },
            onDismiss = {
                viewModel.updateDrawingState(
                    drawingState.copy(
                        showTextDialog = false,
                        textDialogPosition = null
                    )
                )
            }
        )
    }
}

@Composable
fun TextInputDialog(
    onTextEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Text Annotation") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Enter text") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTextEntered(text)
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AnnotationOverlay(
    annotations: List<com.troubashare.domain.model.Annotation>,
    modifier: Modifier = Modifier,
    toolbarOffsetX: Float = 0f, // Offset to account for toolbar in drawing mode
    pdfBitmap: Bitmap? = null // PDF bitmap to calculate actual display area
) {
    val textMeasurer = rememberTextMeasurer()
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // Calculate effective PDF display area (accounting for aspect ratio and ContentScale.Fit)
        var effectiveWidth = canvasWidth
        var effectiveHeight = canvasHeight
        var pdfOffsetX = 0f
        var pdfOffsetY = 0f
        
        if (pdfBitmap != null) {
            val bitmapAspectRatio = pdfBitmap.width.toFloat() / pdfBitmap.height.toFloat()
            val canvasAspectRatio = canvasWidth / canvasHeight
            
            if (bitmapAspectRatio > canvasAspectRatio) {
                // PDF is wider - fit to width, letterbox top/bottom
                effectiveWidth = canvasWidth
                effectiveHeight = canvasWidth / bitmapAspectRatio
                pdfOffsetY = (canvasHeight - effectiveHeight) / 2f
            } else {
                // PDF is taller - fit to height, letterbox left/right  
                effectiveHeight = canvasHeight
                effectiveWidth = canvasHeight * bitmapAspectRatio
                pdfOffsetX = (canvasWidth - effectiveWidth) / 2f
            }
        }
        
        // Draw existing annotation strokes (read-only)
        annotations.forEach { annotationItem ->
            annotationItem.strokes.forEach { stroke ->
                if (stroke.points.isNotEmpty()) {
                    // CONVERT from PDF-relative coordinates (0.0-1.0) to effective PDF display area pixels
                    val originalFirstPoint = stroke.points.firstOrNull()
                    val canvasPoints = stroke.points.map { point ->
                        // Convert from PDF-relative (0.0-1.0) to effective PDF area pixels + offset
                        val canvasX = point.x * effectiveWidth + pdfOffsetX   // PDF-relative to effective area pixels
                        val canvasY = point.y * effectiveHeight + pdfOffsetY  // PDF-relative to effective area pixels
                        point.copy(x = canvasX, y = canvasY)
                    }
                    
                    
                    val path = createPathFromPoints(canvasPoints)
                    val strokeColor = try {
                        androidx.compose.ui.graphics.Color(stroke.color.toUInt().toInt())
                    } catch (e: Exception) {
                        androidx.compose.ui.graphics.Color.Black // Fallback to black
                    }
                    
                    when (stroke.tool) {
                        com.troubashare.domain.model.DrawingTool.PEN -> {
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = stroke.strokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                        com.troubashare.domain.model.DrawingTool.HIGHLIGHTER -> {
                            drawPath(
                                path = path,
                                color = strokeColor.copy(alpha = 0.5f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = stroke.strokeWidth * 1.3f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                        com.troubashare.domain.model.DrawingTool.ERASER -> {
                            drawPath(
                                path = path,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 1f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = stroke.strokeWidth * 3f, // Make eraser thicker
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                        com.troubashare.domain.model.DrawingTool.SELECT -> {
                            // SELECT tool doesn't create strokes
                        }
                        com.troubashare.domain.model.DrawingTool.TEXT -> {
                            // Draw text annotation - ensure it's always visible
                            stroke.text?.let { text ->
                                if (canvasPoints.isNotEmpty()) {
                                    val position = canvasPoints.first()
                                    // Use black color for maximum visibility, regardless of original color
                                    val textColor = androidx.compose.ui.graphics.Color.Black
                                    
                                    // Ensure minimum readable size
                                    val fontSize = maxOf(stroke.strokeWidth * 3, 18f).sp
                                    
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = text,
                                        topLeft = androidx.compose.ui.geometry.Offset(position.x, position.y),
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = textColor,
                                            fontSize = fontSize
                                        )
                                    )
                                }
                            }
                        }
                        com.troubashare.domain.model.DrawingTool.PAN_ZOOM -> {
                            // PAN_ZOOM tool doesn't create strokes
                        }
                    }
                }
            }
        }
    }
}

private fun createPathFromPoints(points: List<com.troubashare.domain.model.AnnotationPoint>): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
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
        return androidx.compose.ui.graphics.Path()
    }
    return path
}

@Composable
fun AnnotatableImageViewer(
    filePath: String,
    fileName: String,
    viewModel: FileViewerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val file = File(filePath)
    
    val drawingState by viewModel.drawingState.collectAsState()
    val annotations by viewModel.currentPageAnnotations.collectAsState()
    
    // Images are single page, so always page 0
    LaunchedEffect(Unit) {
        viewModel.setCurrentPage(0)
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Annotation Toolbar
        AnnotationToolbar(
            drawingState = drawingState,
            onDrawingStateChanged = viewModel::updateDrawingState,
            annotations = annotations,
            onDeleteStroke = viewModel::deleteStroke,
            onSaveAnnotations = {
                // TODO: Implement save logic with file picker
            }
        )
        
        // Image Content with Annotations
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (file.exists()) {
                // For images, we would need to load the image as a bitmap first
                // and then overlay it with annotations. This is a simplified version.
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = fileName,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
                
                // Overlay annotation canvas
                if (drawingState.isDrawing || annotations.isNotEmpty()) {
                    AnnotationCanvas(
                        backgroundBitmap = null, // Would need to load image as bitmap
                        annotations = annotations,
                        drawingState = drawingState,
                        onStrokeAdded = viewModel::addStroke,
                        onDrawingStateChanged = viewModel::updateDrawingState,
                        onStrokeUpdated = viewModel::updateStroke,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Image file not found",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}