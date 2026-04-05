package com.troubashare.ui.screens.song

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.SongFile

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
    val viewModel: SongDetailViewModel = hiltViewModel()

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
                                        val memberFiles = viewModel.getFilesForMember(
                                            currentSong.files, member.id, member.partIds
                                        )
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
                                                onViewFile(file, currentSong.title, member.name)
                                            },
                                            onFileMoveUp = { file, position ->
                                                viewModel.moveFile(member.id, file.id, position - 1)
                                            },
                                            onFileMoveDown = { file, position ->
                                                viewModel.moveFile(member.id, file.id, position + 1)
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
