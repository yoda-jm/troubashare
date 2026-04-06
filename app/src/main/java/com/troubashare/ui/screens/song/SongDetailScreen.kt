package com.troubashare.ui.screens.song

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troubashare.domain.model.FileType
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.SongFile
import com.troubashare.ui.components.ImagePickerButton
import com.troubashare.ui.components.PDFPickerButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    groupId: String,
    songId: String,
    onNavigateBack: () -> Unit,
    onViewFile: (SongFile, String, String, String) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: SongDetailViewModel = hiltViewModel()

    val uiState by viewModel.uiState.collectAsState()
    val song by viewModel.song.collectAsState()
    val currentGroup by viewModel.currentGroup.collectAsState()
    val fileSelections by viewModel.fileSelections.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    var showMatrixDialog by remember { mutableStateOf(false) }

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
                // Song Info
                item {
                    SongInfoCard(song = currentSong)
                }

                // File Pool + per-member sections
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Song Files",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Files added here are shared with all members",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isAdmin) {
                                Spacer(modifier = Modifier.height(12.dp))

                                val uploaderMemberId = currentGroup?.members?.firstOrNull()?.id ?: ""
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    PDFPickerButton(
                                        onPdfSelected = { uri, fileName ->
                                            viewModel.uploadFile(uploaderMemberId, fileName, uri, context)
                                        },
                                        text = "Add PDF",
                                        enabled = !uiState.isUploading,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ImagePickerButton(
                                        onImageSelected = { uri, fileName ->
                                            viewModel.uploadFile(uploaderMemberId, fileName, uri, context)
                                        },
                                        text = "Add Image",
                                        enabled = !uiState.isUploading,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (uiState.isUploading) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }

                            // Pool file list — each can be deleted from the pool entirely
                            val poolFiles = currentSong.files.filter { it.fileType != FileType.ANNOTATION }

                            // Matrix assignment dialog (admin only)
                            val members = currentGroup?.members ?: emptyList()
                            if (isAdmin && poolFiles.isNotEmpty() && members.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { showMatrixDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Manage Assignments")
                                }
                            }
                            if (showMatrixDialog) {
                                FileSelectionMatrixDialog(
                                    poolFiles = poolFiles,
                                    members = members,
                                    selections = fileSelections,
                                    onToggle = { fileId, memberId, selected ->
                                        viewModel.toggleFileSelection(fileId, memberId, selected)
                                    },
                                    onDismiss = { showMatrixDialog = false }
                                )
                            }

                            if (poolFiles.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                poolFiles.forEachIndexed { index, file ->
                                    FileItem(
                                        file = file,
                                        allFiles = currentSong.files,
                                        position = index,
                                        totalFiles = poolFiles.size,
                                        canAdmin = isAdmin,
                                        onDelete = { viewModel.deleteFile(file) },
                                        onView = { onViewFile(file, currentSong.title, "", "") },
                                        onMoveUp = { /* pool order not managed here */ },
                                        onMoveDown = { }
                                    )
                                    if (index < poolFiles.lastIndex) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }

                            // Per-member assignment sections
                            currentGroup?.let { group ->
                                if (group.members.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Member Assignments",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    group.members.forEach { member ->
                                        val memberFiles = viewModel.getFilesForMember(
                                            currentSong.files, member.id, member.partIds, fileSelections
                                        )
                                        MemberFileSection(
                                            member = member,
                                            assignedFiles = memberFiles,
                                            poolFiles = poolFiles,
                                            onRemoveAssignment = { file ->
                                                viewModel.removeFileAssignment(file.id, member.id)
                                            },
                                            onAssign = { file ->
                                                viewModel.assignFileToMember(file.id, member.id)
                                            },
                                            onFileView = { file ->
                                                onViewFile(file, currentSong.title, member.name, member.id)
                                            },
                                            onFileMoveUp = { file, position ->
                                                viewModel.moveFile(member.id, file.id, position - 1)
                                            },
                                            onFileMoveDown = { file, position ->
                                                viewModel.moveFile(member.id, file.id, position + 1)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
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
