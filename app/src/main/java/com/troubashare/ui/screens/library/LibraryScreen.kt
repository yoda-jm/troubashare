package com.troubashare.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troubashare.R
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.repository.SongRepository
import com.troubashare.domain.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onSongClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { TroubaShareDatabase.getInstance(context) }
    val songRepository = remember { SongRepository(database) }
    val groupRepository = remember { com.troubashare.data.repository.GroupRepository(database) }
    val viewModel: LibraryViewModel = viewModel { LibraryViewModel(songRepository, groupId) }
    
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val createSongState by viewModel.createSongState.collectAsState()
    val songs by viewModel.songs.collectAsState()
    
    // Get current group for display
    val currentGroup by remember {
        flow { emit(groupRepository.getGroupById(groupId)) }
    }.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.nav_library))
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateSongDialog() }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add song"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Search songs...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No songs yet" else "No songs found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.showCreateSongDialog() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Song")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongCard(
                            song = song,
                            onClick = { onSongClick(song.id) },
                            onDelete = { viewModel.deleteSong(song) }
                        )
                    }
                }
            }
        }
    }

    // Create Song Dialog
    if (uiState.showCreateDialog) {
        CreateSongDialog(
            state = createSongState,
            onTitleChange = viewModel::updateSongTitle,
            onArtistChange = viewModel::updateSongArtist,
            onKeyChange = viewModel::updateSongKey,
            onTempoChange = viewModel::updateSongTempo,
            onNotesChange = viewModel::updateSongNotes,
            onTagInputChange = viewModel::updateTagInput,
            onAddTag = viewModel::addTag,
            onRemoveTag = viewModel::removeTag,
            onCreate = viewModel::createSong,
            onDismiss = viewModel::hideCreateSongDialog
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongCard(
    song: Song,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    if (song.artist != null) {
                        Text(
                            text = "by ${song.artist}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (song.key != null || song.tempo != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            if (song.key != null) {
                                Text(
                                    text = "Key: ${song.key}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (song.key != null && song.tempo != null) {
                                Text(
                                    text = " • ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (song.tempo != null) {
                                Text(
                                    text = "${song.tempo} BPM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    if (song.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            song.tags.take(3).forEach { tag ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            if (song.tags.size > 3) {
                                Text(
                                    text = "+${song.tags.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                IconButton(
                    onClick = { showDeleteDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info, // Will use Delete icon when available
                        contentDescription = "Delete song",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            if (song.files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star, // Will use file icon when available
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${song.files.size} files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Song") },
            text = { Text("Are you sure you want to delete \"${song.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CreateSongDialog(
    state: CreateSongUiState,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onTempoChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Song") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text("Song Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = state.errorMessage != null
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = state.artist,
                    onValueChange = onArtistChange,
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row {
                    OutlinedTextField(
                        value = state.key,
                        onValueChange = onKeyChange,
                        label = { Text("Key") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    OutlinedTextField(
                        value = state.tempoInput,
                        onValueChange = onTempoChange,
                        label = { Text("BPM") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = onNotesChange,
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                
                if (state.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.tags) { tag ->
                            InputChip(
                                onClick = { onRemoveTag(tag) },
                                label = { Text(tag) },
                                selected = false,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add, // Will use X icon when available
                                        contentDescription = "Remove tag",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = state.tagInput,
                    onValueChange = onTagInputChange,
                    label = { Text("Add tags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (state.tagInput.isNotBlank()) {
                                onAddTag(state.tagInput)
                                keyboardController?.hide()
                            }
                        }
                    ),
                    trailingIcon = {
                        if (state.tagInput.isNotBlank()) {
                            IconButton(onClick = { onAddTag(state.tagInput) }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add tag"
                                )
                            }
                        }
                    }
                )
                
                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = state.isValid && !state.isCreating
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}