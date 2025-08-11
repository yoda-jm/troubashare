package com.troubashare.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MultiPagePDFViewer(
    filePath: String,
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
                
                // Render all pages
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val pageBitmap = Bitmap.createBitmap(
                        page.width * 2, // Higher resolution for better quality
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )
                    
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
                // Show all pages in a scrollable column
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    itemsIndexed(pageBitmaps) { index, bitmap ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column {
                                // Page number indicator
                                Text(
                                    text = "Page ${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(8.dp)
                                )
                                
                                // Page image
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "PDF Page ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}