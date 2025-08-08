package com.troubashare.ui.screens.song

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onViewFile: (SongFile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { TroubaShareDatabase.getInstance(context) }
    val fileManager = remember { FileManager(context) }
    val songRepository = remember { SongRepository(database, fileManager) }
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
                            imageVector = Icons.Default.ArrowBack,
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
                                        MemberFileSection(
                                            member = member,
                                            files = currentSong.files.filter { it.memberId == member.id },
                                            onFileUpload = { uri, fileName ->
                                                viewModel.uploadFile(member.id, fileName, uri, context)
                                            },
                                            onFileDelete = { file ->
                                                viewModel.deleteFile(file)
                                            },
                                            onFileView = onViewFile,
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
            
            // Display uploaded files
            if (files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                files.forEach { file ->
                    FileItem(
                        file = file,
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
    onDelete: () -> Unit,
    onView: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Icon(
                imageVector = if (file.fileType.name == "PDF") Icons.Default.Star else Icons.Default.Add,
                contentDescription = file.fileType.name,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = file.fileType.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Delete file",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}