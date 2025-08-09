package com.troubashare.ui.screens.setlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.repository.SetlistRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.file.FileManager
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.Setlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistEditorScreen(
    setlistId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { TroubaShareDatabase.getInstance(context) }
    val fileManager = remember { FileManager(context) }
    val songRepository = remember { SongRepository(database, fileManager) }
    val setlistRepository = remember { SetlistRepository(database, songRepository) }
    val viewModel: SetlistEditorViewModel = viewModel { 
        SetlistEditorViewModel(setlistRepository, songRepository, setlistId) 
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val setlist by viewModel.setlist.collectAsState()
    val availableSongs by viewModel.availableSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    setlist?.let { currentSetlist ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text(
                                text = "Edit Setlist",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentSetlist.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        // Save changes
                        IconButton(
                            onClick = viewModel::saveSetlist,
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save setlist"
                                )
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current setlist songs
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Songs in Setlist (${currentSetlist.items.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        if (currentSetlist.items.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No songs in this setlist yet. Add songs from the library below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            currentSetlist.items.sortedBy { it.position }.forEach { item ->
                                SetlistSongItem(
                                    song = item.song,
                                    position = item.position,
                                    onRemove = { viewModel.removeSongFromSetlist(item.song.id) },
                                    onMoveUp = { 
                                        if (item.position > 0) {
                                            viewModel.moveSong(item.song.id, item.position - 1)
                                        }
                                    },
                                    onMoveDown = { 
                                        if (item.position < currentSetlist.items.size - 1) {
                                            viewModel.moveSong(item.song.id, item.position + 1)
                                        }
                                    },
                                    canMoveUp = item.position > 0,
                                    canMoveDown = item.position < currentSetlist.items.size - 1
                                )
                            }
                        }
                    }
                }
                
                // Add songs section
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Add Songs from Library",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
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
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Available songs
                        if (availableSongs.isEmpty()) {
                            Text(
                                text = if (searchQuery.isBlank()) "No songs available" else "No songs found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(300.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(availableSongs, key = { it.id }) { song ->
                                    AvailableSongItem(
                                        song = song,
                                        onAdd = { viewModel.addSongToSetlist(song.id) },
                                        isAdding = uiState.isAddingSong && uiState.addingSongId == song.id
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Error display
                uiState.errorMessage?.let { errorMessage ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistSongItem(
    song: Song,
    position: Int,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Order number
            Text(
                text = "${position + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp)
            )
            
            // Song info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (song.artist != null) {
                    Text(
                        text = "by ${song.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Move controls
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move up"
                )
            }
            
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down"
                )
            }
            
            // Remove button
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove from setlist",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableSongItem(
    song: Song,
    onAdd: () -> Unit,
    isAdding: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = if (!isAdding) onAdd else { {} },
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (song.artist != null) {
                    Text(
                        text = "by ${song.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isAdding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to setlist",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}