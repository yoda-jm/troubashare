package com.troubashare.ui.screens.song

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.file.FileManager
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.repository.GroupRepository
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.SongFile
import com.troubashare.domain.model.Member
import com.troubashare.ui.components.ImagePickerButton
import com.troubashare.ui.components.PDFPickerButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    groupId: String,
    songId: String,
    onNavigateBack: () -> Unit,
    onViewFile: (SongFile, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { TroubaShareDatabase.getInstance(context) }
    val fileManager = remember { FileManager(context) }
    val annotationRepository = remember { com.troubashare.data.repository.AnnotationRepository(database) }
    val songRepository = remember { SongRepository(database, fileManager, annotationRepository) }
    val groupRepository = remember { GroupRepository(database) }
    val viewModel: SongDetailViewModel = viewModel { 
        SongDetailViewModel(songRepository, groupRepository, songId, groupId) 
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val song by viewModel.song.collectAsState()
    val currentGroup by viewModel.currentGroup.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = song?.title ?: "Song Details",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        currentGroup?.let { group ->
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        song?.let { currentSong ->
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Song Info Section
                item {
                    SongInfoCard(song = currentSong)
                }
                
                // File Management Section
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Files & Sheet Music",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // File Upload Buttons
                            currentGroup?.let { group ->
                                if (group.members.isNotEmpty()) {
                                    Text(
                                        text = "Add files for:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Member selection and file upload
                                    group.members.forEach { member ->
                                        val memberFiles = currentSong.files.filter { it.memberId == member.id }
                                        println("DEBUG SongDetailScreen: Member '${member.name}' (${member.id}) files: ${memberFiles.size}")
                                        memberFiles.forEachIndexed { index, file ->
                                            println("DEBUG SongDetailScreen: Member file $index - id='${file.id}', songId='${file.songId}', fileName='${file.fileName}', memberId='${file.memberId}'")
                                        }
                                        MemberFileSection(
                                            member = member,
                                            files = memberFiles,
                                            onFileUpload = { uri, fileName ->
                                                viewModel.uploadFile(member.id, fileName, uri, context)
                                            },
                                            onFileDelete = { file ->
                                                viewModel.deleteFile(file)
                                            },
                                            onFileView = { file ->
                                                println("DEBUG SongDetailScreen: Navigating to file - id='${file.id}', songId='${file.songId}', fileName='${file.fileName}'")
                                                onViewFile(file, currentSong.title, member.name)
                                            },
                                            isUploading = uiState.isUploading
                                        )
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                } else {
                                    Text(
                                        text = "Add members to your group to upload files",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Error display
                uiState.errorMessage?.let { errorMessage ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        } ?: run {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun SongInfoCard(
    song: Song,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall
            )
            
            if (song.artist != null) {
                Text(
                    text = "by ${song.artist}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (song.key != null || song.tempo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    if (song.key != null) {
                        Text(
                            text = "Key: ${song.key}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (song.key != null && song.tempo != null) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (song.tempo != null) {
                        Text(
                            text = "${song.tempo} BPM",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (song.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    song.tags.forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
            
            if (song.notes != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Notes:",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = song.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MemberFileSection(
    member: Member,
    files: List<SongFile>,
    onFileUpload: (Uri, String) -> Unit,
    onFileDelete: (SongFile) -> Unit,
    onFileView: (SongFile) -> Unit,
    isUploading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Member name
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Upload buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PDFPickerButton(
                    onPdfSelected = onFileUpload,
                    text = "PDF",
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f)
                )

                ImagePickerButton(
                    onImageSelected = onFileUpload,
                    text = "Image",
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f)
                )
            }

            // Display uploaded files - hide annotation files since they're managed through properties dialog
            val displayFiles = files.filter { file ->
                file.fileType != com.troubashare.domain.model.FileType.ANNOTATION
            }
            
            if (displayFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                displayFiles.forEach { file ->
                    FileItem(
                        file = file,
                        allFiles = files, // Pass all member files to check for annotations
                        onDelete = { onFileDelete(file) },
                        onView = { onFileView(file) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileItem(
    file: SongFile,
    allFiles: List<SongFile> = emptyList(), // All files to check for annotation layers
    onDelete: () -> Unit,
    onView: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    // State for annotation visibility in concert mode (per file)
    val context = LocalContext.current
    val preferencesManager = remember { com.troubashare.data.preferences.AnnotationPreferencesManager(context) }
    var showAnnotationsInConcert by remember {
        mutableStateOf(preferencesManager.getAnnotationLayerVisibility(file.id, file.memberId))
    }

    // Scroll mode state (for PDFs in concert mode)
    var useScrollMode by remember {
        mutableStateOf(preferencesManager.getScrollMode(file.id, file.memberId))
    }

    // Layer name state
    var layerName by remember {
        mutableStateOf(preferencesManager.getAnnotationLayerName(file.id, file.memberId) ?: file.fileName)
    }
    // DEBUG: Log annotation files for debugging  
    val annotationFiles = allFiles.filter { it.fileType == com.troubashare.domain.model.FileType.ANNOTATION }
    if (annotationFiles.isNotEmpty()) {
        println("DEBUG FileItem: Found ${annotationFiles.size} annotation files for file '${file.fileName}' (ID: '${file.id}')")
        annotationFiles.forEach { annotationFile ->
            println("DEBUG FileItem: - Annotation file: '${annotationFile.fileName}' (ID: '${annotationFile.id}')")
        }
    }
    
    // Check if this file has associated annotation layers
    // Try multiple identification patterns since navigation can cause ID inconsistencies  
    val hasAnnotations = allFiles.any { annotationFile ->
        annotationFile.fileType == com.troubashare.domain.model.FileType.ANNOTATION && (
            // Pattern 1: Direct file ID match (when file ID is available)
            (file.id.isNotBlank() && annotationFile.fileName.contains("annotations_${file.id}_")) ||
            // Pattern 2: For now, assume any annotation file in the song belongs to this file
            // (This is a simple assumption that works when there's only one main file per song)
            annotationFile.fileName.startsWith("annotations_")
        )
    }
    
    Card(
        onClick = {
            // Don't allow direct viewing of annotation files - they need to be loaded into a PDF viewer
            if (file.fileType != com.troubashare.domain.model.FileType.ANNOTATION) {
                onView()
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = if (file.fileType == com.troubashare.domain.model.FileType.ANNOTATION) {
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
                    com.troubashare.domain.model.FileType.PDF -> Icons.Default.PictureAsPdf
                    com.troubashare.domain.model.FileType.IMAGE -> Icons.Default.Image
                    com.troubashare.domain.model.FileType.ANNOTATION -> Icons.Default.Edit
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
                        // Show custom layer name if available, otherwise show filename
                        Text(
                            text = if (hasAnnotations && file.fileType != com.troubashare.domain.model.FileType.ANNOTATION) {
                                layerName
                            } else {
                                file.fileName
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // Show original filename if custom name is different
                        if (hasAnnotations && file.fileType != com.troubashare.domain.model.FileType.ANNOTATION && layerName != file.fileName) {
                            Text(
                                text = file.fileName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Show "annotation layer" label for annotation files
                    if (file.fileType == com.troubashare.domain.model.FileType.ANNOTATION) {
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
                    text = "ID: ${file.id.take(8)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                
                Text(
                    text = file.fileType.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Annotation indicator icon for PDFs
            if (file.fileType == com.troubashare.domain.model.FileType.PDF && file.fileType != com.troubashare.domain.model.FileType.ANNOTATION) {
                Icon(
                    imageVector = if (hasAnnotations) Icons.Default.Edit else Icons.Default.EditOff,
                    contentDescription = if (hasAnnotations) "Has annotations" else "No annotations",
                    modifier = Modifier.size(20.dp),
                    tint = if (hasAnnotations)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Properties button for PDF files
            if (file.fileType == com.troubashare.domain.model.FileType.PDF && file.fileType != com.troubashare.domain.model.FileType.ANNOTATION) {
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

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete file",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    // Layer naming dialog
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Edit Layer Name") },
            text = {
                Column {
                    Text(
                        text = "Enter a custom name for this annotation layer:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Layer Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalName = nameInput.trim().ifEmpty { file.fileName }
                        preferencesManager.setAnnotationLayerName(file.id, file.memberId, finalName.takeIf { it != file.fileName })
                        layerName = finalName
                        showNameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Properties dialog for PDF files
    if (showPropertiesDialog) {
        // Load annotations and calculate stroke count
        val database = remember { TroubaShareDatabase.getInstance(context) }
        val annotationRepository = remember { com.troubashare.data.repository.AnnotationRepository(database) }
        val annotations by annotationRepository.getAnnotationsByFileAndMember(file.id, file.memberId)
            .collectAsState(initial = emptyList())

        // Calculate total stroke count
        val totalStrokes = annotations.sumOf { it.strokes.size }

        // Get PDF page count
        var pageCount by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(file.filePath) {
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

        // State for showing delete confirmation
        var showDeleteConfirmation by remember { mutableStateOf(false) }

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
                    Text("PDF Properties")
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
                            PropertyRow(
                                label = "Pages",
                                value = pageCount?.toString() ?: "Loading..."
                            )
                            PropertyRow(
                                label = "File ID",
                                value = file.id.take(8) + "..."
                            )
                            if (hasAnnotations) {
                                val annotationFile = annotationFiles.firstOrNull()
                                annotationFile?.let {
                                    PropertyRow(
                                        label = "Annotation ID",
                                        value = it.id.take(8) + "..."
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

                                // Layer name editing
                                OutlinedTextField(
                                    value = layerName,
                                    onValueChange = { newName ->
                                        layerName = newName
                                        val finalName = newName.trim().ifEmpty { file.fileName }
                                        preferencesManager.setAnnotationLayerName(
                                            file.id,
                                            file.memberId,
                                            finalName.takeIf { it != file.fileName }
                                        )
                                    },
                                    label = { Text("Layer Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
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

                    // Concert Mode Settings Section
                    Text(
                        text = "Concert Mode Settings",
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
                            // Show annotations in concert toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Show Annotations",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = showAnnotationsInConcert,
                                    onCheckedChange = {
                                        showAnnotationsInConcert = it
                                        preferencesManager.setAnnotationLayerVisibility(file.id, file.memberId, it)
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }

                            HorizontalDivider()

                            // View mode selection
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "View Mode",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SegmentedButton(
                                        selected = !useScrollMode,
                                        onClick = {
                                            useScrollMode = false
                                            preferencesManager.setScrollMode(file.id, file.memberId, false)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                    ) {
                                        Text("Swipe")
                                    }
                                    SegmentedButton(
                                        selected = useScrollMode,
                                        onClick = {
                                            useScrollMode = true
                                            preferencesManager.setScrollMode(file.id, file.memberId, true)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                    ) {
                                        Text("Scroll")
                                    }
                                }
                                Text(
                                    text = if (useScrollMode) "Continuous vertical scrolling through all pages" else "Swipe horizontally between pages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                            // Delete the annotation file
                            annotationFiles.firstOrNull()?.let { annotFile ->
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