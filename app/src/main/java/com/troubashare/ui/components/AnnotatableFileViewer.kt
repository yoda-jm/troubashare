package com.troubashare.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.troubashare.ui.screens.file.FileViewerViewModel
import com.troubashare.domain.model.Annotation as DomainAnnotation
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
        
        // Annotation Toolbar
        AnnotationToolbar(
            drawingState = drawingState,
            onDrawingStateChanged = viewModel::updateDrawingState,
            onToggleDrawing = viewModel::toggleDrawingMode,
            onClearAll = viewModel::clearAllAnnotations
        )
        
        // PDF Content with Annotations
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
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
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                bitmap != null -> {
                    AnnotationCanvas(
                        backgroundBitmap = bitmap!!.asImageBitmap(),
                        annotations = annotations,
                        drawingState = drawingState,
                        onStrokeAdded = viewModel::addStroke,
                        onDrawingStateChanged = viewModel::updateDrawingState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }
            }
        }
    }
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
            onToggleDrawing = viewModel::toggleDrawingMode,
            onClearAll = viewModel::clearAllAnnotations
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