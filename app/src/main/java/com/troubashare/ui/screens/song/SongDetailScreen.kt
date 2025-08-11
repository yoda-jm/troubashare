package com.troubashare.ui.screens.song

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troubashare.R
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.file.FileManager
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.repository.GroupRepository
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.SongFile
import com.troubashare.domain.model.Member
import com.troubashare.ui.components.FilePickerButton
import com.troubashare.ui.components.ImagePickerButton
import com.troubashare.ui.components.PDFPickerButton
import kotlinx.coroutines.flow.flow

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
    var showAnnotations by remember { mutableStateOf(true) }
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
            // Member name and annotation toggle
            val hasAnnotations = files.any { it.fileType == com.troubashare.domain.model.FileType.ANNOTATION }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleSmall
                )
                
                // Annotation visibility toggle (only show if there are annotations)
                if (hasAnnotations) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show annotations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = showAnnotations,
                            onCheckedChange = { showAnnotations = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
            
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
            
            // Display uploaded files
            val displayFiles = files.filter { file ->
                // Show all non-annotation files, and annotation files only if toggle is enabled
                file.fileType != com.troubashare.domain.model.FileType.ANNOTATION || showAnnotations
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
    // State for annotation visibility in concert mode (per file)
    var showAnnotationsInConcert by remember { mutableStateOf(true) }
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
        onClick = onView,
        modifier = modifier.fillMaxWidth()
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
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Annotation layer indicator
                    if (hasAnnotations && file.fileType != com.troubashare.domain.model.FileType.ANNOTATION) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = "Has annotation layer",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                
                // DEBUG: Show fileId to help identify which file is which
                Text(
                    text = "ID: ${file.id.take(8)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.fileType.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (hasAnnotations && file.fileType != com.troubashare.domain.model.FileType.ANNOTATION) {
                        Text(
                            text = " • Annotated",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            
            // Concert mode annotation toggle (only for files that have annotations)
            if (hasAnnotations && file.fileType != com.troubashare.domain.model.FileType.ANNOTATION) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Concert",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = showAnnotationsInConcert,
                        onCheckedChange = { showAnnotationsInConcert = it },
                        modifier = Modifier.scale(0.7f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
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
}