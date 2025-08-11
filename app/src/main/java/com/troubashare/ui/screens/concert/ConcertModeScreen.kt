package com.troubashare.ui.screens.concert

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.repository.SetlistRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.repository.GroupRepository
import com.troubashare.data.file.FileManager

/**
 * IMPORTANT: ConcertModeScreen is READ-ONLY for performance viewing.
 * 
 * NO ANNOTATION EDITING should be implemented here - this is pure read-only display.
 * All annotation management (save, edit, toggle) belongs in the song editing interface (SongDetailScreen).
 * 
 * Concert mode uses basic FileViewer, not AnnotatableFileViewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcertModeScreen(
    setlistId: String,
    memberId: String,
    onNavigateBack: () -> Unit,
    onNavigateToFile: (String, String, String, String, String) -> Unit, // filePath, fileName, fileType, songTitle, memberName
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { TroubaShareDatabase.getInstance(context) }
    val fileManager = remember { FileManager(context) }
    val annotationRepository = remember { com.troubashare.data.repository.AnnotationRepository(database) }
    val songRepository = remember { SongRepository(database, fileManager, annotationRepository) }
    val setlistRepository = remember { SetlistRepository(database, songRepository) }
    val groupRepository = remember { GroupRepository(database) }
    
    val viewModel: ConcertModeViewModel = viewModel { 
        ConcertModeViewModel(setlistRepository, songRepository, groupRepository) 
    }
    
    LaunchedEffect(setlistId, memberId) {
        viewModel.loadConcertData(setlistId, memberId)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Controls visibility state
    var showControls by remember { mutableStateOf(true) }
    
    // Auto-hide controls after 10 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            kotlinx.coroutines.delay(10000)
            showControls = false
        }
    }
    
    // System UI management for fullscreen
    val view = LocalView.current
    val window = (view.context as ComponentActivity).window
    
    DisposableEffect(Unit) {
        // Hide system UI completely for true fullscreen
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        // Enable edge-to-edge mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, view)
        
        // Hide all system bars and ensure they stay hidden
        windowInsetsController.hide(
            WindowInsetsCompat.Type.statusBars() or 
            WindowInsetsCompat.Type.navigationBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.systemGestures()
        )
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // Also hide action bar if present
        (view.context as? ComponentActivity)?.actionBar?.hide()
        
        onDispose {
            // Restore system UI when leaving concert mode
            WindowCompat.setDecorFitsSystemWindows(window, true)
            windowInsetsController.show(
                WindowInsetsCompat.Type.statusBars() or 
                WindowInsetsCompat.Type.navigationBars()
            )
            (view.context as? ComponentActivity)?.actionBar?.show()
        }
    }
    
    // Fullscreen Concert Mode Interface with Drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SongListDrawer(
                songs = uiState.songs,
                currentSongIndex = uiState.currentSongIndex,
                onSongSelected = { index ->
                    viewModel.goToSong(index)
                    scope.launch { drawerState.close() }
                },
                onClose = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // Toggle controls - if visible, hide them; if hidden, show them
                            showControls = !showControls
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { 
                            // Hide controls immediately when any drag starts
                            if (showControls) {
                                showControls = false
                            }
                        },
                        onDrag = { _, _ -> 
                            // Keep controls hidden during drag
                        }
                    )
                }
        ) {
        // Main content area - display current song's file fullscreen
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading concert...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
            uiState.songs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "No songs in this setlist",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
            uiState.currentSongIndex >= 0 && uiState.currentSongIndex < uiState.songs.size -> {
                // Display current song's file
                val currentSong = uiState.songs[uiState.currentSongIndex]
                if (currentSong.files.isNotEmpty()) {
                    // For now, display the first available file
                    // TODO: Add file selection or show all files
                    val file = currentSong.files.first()
                    
                    // Use multi-page PDF viewer for concert mode to show all pages at once
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.White
                        ) {
                            // Use MultiPagePDFViewer for PDFs to show all pages, regular FileViewer for other types
                            if (file.fileType.name.uppercase() == "PDF") {
                                com.troubashare.ui.components.MultiPagePDFViewer(
                                    filePath = file.filePath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                com.troubashare.ui.components.FileViewer(
                                    filePath = file.filePath,
                                    fileName = file.fileName,
                                    fileType = file.fileType.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                } else {
                    // No files available for current song - show on white background for consistency
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .background(
                                color = Color.White,
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentSong.title,
                                color = Color.Black,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No files available for ${uiState.memberName}",
                                color = Color.Black.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
        
        // Top overlay with song title, position, and menu button - positioned to avoid system UI
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { -it },
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Menu button to open drawer
            FloatingActionButton(
                onClick = {
                    scope.launch { drawerState.open() }
                },
                modifier = Modifier.size(56.dp),
                containerColor = Color.Black.copy(alpha = 0.8f),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Song list",
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Song title and position
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.currentSongIndex >= 0 && uiState.currentSongIndex < uiState.songs.size) {
                        Text(
                            text = uiState.songs[uiState.currentSongIndex].title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${uiState.currentSongIndex + 1} of ${uiState.totalSongs}",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Exit button
            FloatingActionButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(56.dp),
                containerColor = Color.Red.copy(alpha = 0.8f),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit concert mode",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        }
        
        // Bottom navigation bar - more prominent and positioned properly
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { it },
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous song button
            FloatingActionButton(
                onClick = viewModel::previousSong,
                modifier = Modifier.size(64.dp),
                containerColor = if (uiState.currentSongIndex > 0) {
                    Color.Black.copy(alpha = 0.8f)
                } else {
                    Color.Gray.copy(alpha = 0.5f)
                },
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous song",
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Next song button
            FloatingActionButton(
                onClick = viewModel::nextSong,
                modifier = Modifier.size(64.dp),
                containerColor = if (uiState.currentSongIndex < uiState.totalSongs - 1) {
                    Color.Black.copy(alpha = 0.8f)
                } else {
                    Color.Gray.copy(alpha = 0.5f)
                },
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next song",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        }
    }
    }
}

@Composable
fun SongListDrawer(
    songs: List<ConcertSongItem>,
    currentSongIndex: Int,
    onSongSelected: (Int) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Setlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Song list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(songs) { index, song ->
                    Card(
                        onClick = { onSongSelected(index) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == currentSongIndex) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = if (index == currentSongIndex) {
                            BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary
                            )
                        } else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Song number
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (index == currentSongIndex) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.width(32.dp)
                            )
                            
                            // Song info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (index == currentSongIndex) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                song.artist?.let { artist ->
                                    Text(
                                        text = "by $artist",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (index == currentSongIndex) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            // Current song indicator
                            if (index == currentSongIndex) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Currently playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}