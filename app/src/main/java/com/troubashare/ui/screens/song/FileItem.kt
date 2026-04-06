package com.troubashare.ui.screens.song

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.troubashare.domain.model.FileType
import com.troubashare.domain.model.SHARED_ANNOTATION_LAYER
import com.troubashare.domain.model.SongFile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileItem(
    file: SongFile,
    allFiles: List<SongFile> = emptyList(), // All files to check for annotation layers
    position: Int,
    totalFiles: Int,
    canAdmin: Boolean = true,
    onDelete: () -> Unit,
    onView: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPropertiesDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Check if this file has associated annotation layers - match by file ID only
    val fileAnnotationFiles = allFiles.filter { annotationFile ->
        annotationFile.fileType == FileType.ANNOTATION &&
        file.id.isNotBlank() &&
        annotationFile.fileName.contains("annotations_${file.id}_")
    }
    val hasAnnotations = fileAnnotationFiles.isNotEmpty()

    Card(
        onClick = {
            // Don't allow direct viewing of annotation files - they need to be loaded into a PDF viewer
            if (file.fileType != FileType.ANNOTATION) {
                onView()
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = if (file.fileType == FileType.ANNOTATION) {
            // Make annotation files visually distinct and less clickable-looking
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon
            Icon(
                imageVector = when (file.fileType) {
                    FileType.PDF -> Icons.Default.PictureAsPdf
                    FileType.IMAGE -> Icons.Default.Image
                    FileType.ANNOTATION -> Icons.Default.Edit
                },
                contentDescription = file.fileType.name,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Show "annotation layer" label for annotation files
                    if (file.fileType == FileType.ANNOTATION) {
                        Text(
                            text = "(Not directly viewable)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }

                // DEBUG: Show fileId to help identify which file is which
                Text(
                    text = "ID: ${file.id.take(6)}...${file.id.takeLast(6)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Text(
                    text = file.fileType.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Annotation indicator: shown only when the shared layer has strokes
            if (hasAnnotations
                && file.fileType != FileType.ANNOTATION
                && (file.fileType == FileType.PDF || file.fileType == FileType.IMAGE)) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Has group annotations",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Properties button for PDF and Image files
            if ((file.fileType == FileType.PDF || file.fileType == FileType.IMAGE)
                && file.fileType != FileType.ANNOTATION) {
                IconButton(
                    onClick = { showPropertiesDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "File properties",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Reordering controls with drag handle style (only show for non-annotation files with multiple files)
            if (file.fileType != FileType.ANNOTATION && totalFiles > 1) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = position > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Drag handle visual
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    IconButton(
                        onClick = onMoveDown,
                        enabled = position < totalFiles - 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (canAdmin) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete file",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // Properties dialog for PDF and Image files
    if (showPropertiesDialog) {
        // Load annotations and calculate stroke count
        val annotationRepository = remember {
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(context.applicationContext, com.troubashare.di.RepositoryEntryPoint::class.java)
                .annotationRepository()
        }
        // Pool-level view: count strokes on the shared/group layer only
        val annotations by annotationRepository.getAnnotationsByFileAndMember(file.id, SHARED_ANNOTATION_LAYER)
            .collectAsState(initial = emptyList())

        // Calculate total stroke count
        val totalStrokes = annotations.sumOf { it.strokes.size }

        // Get PDF page count (only for PDFs)
        var pageCount by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(file.filePath) {
            if (file.fileType == FileType.PDF) {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val pdfFile = java.io.File(file.filePath)
                        if (pdfFile.exists()) {
                            val pfd = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = android.graphics.pdf.PdfRenderer(pfd)
                            pageCount = renderer.pageCount
                            renderer.close()
                            pfd.close()
                        }
                    }
                } catch (e: Exception) {
                    pageCount = null
                }
            }
        }

        // State for showing delete confirmation
        var showDeleteConfirmation by remember { mutableStateOf(false) }

        // Debug state
        var debugText by remember { mutableStateOf<String?>(null) }
        var debugLoading by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(if (file.fileType == FileType.PDF) "PDF Properties" else "Image Properties")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // File Information Section
                    Text(
                        text = "File Information",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PropertyRow(
                                label = "File Name",
                                value = file.fileName
                            )
                            // Only show page count for PDFs
                            if (file.fileType == FileType.PDF) {
                                PropertyRow(
                                    label = "Pages",
                                    value = pageCount?.toString() ?: "Loading..."
                                )
                            }
                            PropertyRow(
                                label = "File ID",
                                value = file.id.take(6) + "..." + file.id.takeLast(6)
                            )
                            if (hasAnnotations) {
                                val annotationFile = fileAnnotationFiles.firstOrNull()
                                annotationFile?.let {
                                    PropertyRow(
                                        label = "Annotation ID",
                                        value = it.id.take(6) + "..." + it.id.takeLast(6)
                                    )
                                }
                            }
                        }
                    }

                    // Annotations Section
                    if (hasAnnotations) {
                        Text(
                            text = "Annotations",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PropertyRow(
                                    label = "Total Strokes",
                                    value = totalStrokes.toString()
                                )

                                HorizontalDivider()

                                // Delete annotations button
                                OutlinedButton(
                                    onClick = { showDeleteConfirmation = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete All Annotations")
                                }
                            }
                        }
                    }

                    // ── DEBUG PANEL ───────────────────────────────────────────
                    HorizontalDivider()
                    Text(
                        text = "Debug",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "file.id (queried):\n${file.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                    OutlinedButton(
                        onClick = {
                            debugLoading = true
                            debugText = null
                            coroutineScope.launch {
                                debugText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    buildString {
                                        appendLine("=== Annotations for this file ===")
                                        appendLine(annotationRepository.debugDumpForFile(file.id))
                                        appendLine()
                                        appendLine("=== All annotations in DB ===")
                                        appendLine(annotationRepository.debugDumpAll())
                                    }
                                }
                                debugLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (debugLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Dump DB state")
                    }
                    debugText?.let { dump ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = dump,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                    // ── END DEBUG PANEL ───────────────────────────────────────

                }
            },
            confirmButton = {
                TextButton(onClick = { showPropertiesDialog = false }) {
                    Text("Close")
                }
            }
        )

        // Delete confirmation dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text("Delete Annotations?")
                    }
                },
                text = {
                    Text("This will permanently delete all annotations for this PDF. This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Delete the annotation file for this specific file
                            fileAnnotationFiles.firstOrNull()?.let { annotFile ->
                                onDelete()  // This will delete the annotation file
                            }
                            showDeleteConfirmation = false
                            showPropertiesDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PropertyRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
