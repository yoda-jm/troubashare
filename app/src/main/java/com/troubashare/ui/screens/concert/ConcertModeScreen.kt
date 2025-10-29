package com.troubashare.ui.screens.concert

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.repository.SetlistRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.repository.GroupRepository
import com.troubashare.data.file.FileManager
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * IMPORTANT: ConcertModeScreen is READ-ONLY for performance viewing.
 *
 * NO ANNOTATION EDITING should be implemented here - this is pure read-only display.
 * All annotation management (save, edit, toggle) belongs in the song editing interface (SongDetailScreen).
 *
 * Concert mode uses basic FileViewer, not AnnotatableFileViewer.
 */

/**
 * Represents a single page in the concert mode pager.
 * Can represent:
 * - A single image file (1 page)
 * - A PDF in scroll mode (counted as 1 page)
 * - A single page from a PDF in swipe mode
 */
data class ConcertPage(
    val file: com.troubashare.domain.model.SongFile,
    val pdfPageIndex: Int? = null // null for images and scroll-mode PDFs, page index for swipe-mode PDFs
)

/**
 * Calculate all pages for concert mode display based on member preferences.
 *
 * Rules:
 * - Image: 1 page
 * - PDF in scroll mode: 1 page (all pages scrollable)
 * - PDF in swipe mode: N pages (one page per PDF page)
 */
suspend fun calculateConcertPages(
    files: List<com.troubashare.domain.model.SongFile>,
    memberId: String,
    context: android.content.Context
): List<ConcertPage> {
    val pages = mutableListOf<ConcertPage>()
    val preferencesManager = com.troubashare.data.preferences.AnnotationPreferencesManager(context)

    // Filter out annotation files - they are not displayable content
    val displayableFiles = files.filter { it.fileType != com.troubashare.domain.model.FileType.ANNOTATION }

    displayableFiles.forEach { file ->
        when {
            file.fileType == com.troubashare.domain.model.FileType.IMAGE -> {
                // Images always count as 1 page
                pages.add(ConcertPage(file, pdfPageIndex = null))
            }
            file.fileType == com.troubashare.domain.model.FileType.PDF -> {
                val useScrollMode = preferencesManager.getScrollMode(file.id, memberId)

                if (useScrollMode) {
                    // Scroll mode PDF counts as 1 page
                    pages.add(ConcertPage(file, pdfPageIndex = null))
                } else {
                    // Swipe mode PDF: add one page per PDF page
                    val pdfFile = File(file.filePath)
                    if (pdfFile.exists()) {
                        try {
                            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = PdfRenderer(pfd)
                            val pageCount = renderer.pageCount
                            renderer.close()
                            pfd.close()

                            // Add each PDF page as a separate concert page
                            for (i in 0 until pageCount) {
                                pages.add(ConcertPage(file, pdfPageIndex = i))
                            }
                        } catch (e: Exception) {
                            // If we can't read the PDF, treat it as 1 page
                            pages.add(ConcertPage(file, pdfPageIndex = null))
                        }
                    } else {
                        // File doesn't exist, still add 1 page to show error
                        pages.add(ConcertPage(file, pdfPageIndex = null))
                    }
                }
            }
            else -> {
                // Other file types (shouldn't happen in concert mode, but handle gracefully)
                pages.add(ConcertPage(file, pdfPageIndex = null))
            }
        }
    }

    return pages
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    // Page tracking for PDF swipe mode
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }

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
                // Display current song's files with pager
                val currentSong = uiState.songs[uiState.currentSongIndex]
                if (currentSong.files.isNotEmpty()) {
                    // Calculate all pages for the current song
                    var concertPages by remember { mutableStateOf<List<ConcertPage>>(emptyList()) }

                    LaunchedEffect(uiState.currentSongIndex, memberId) {
                        concertPages = withContext(Dispatchers.IO) {
                            calculateConcertPages(currentSong.files, memberId, context)
                        }
                        totalPages = concertPages.size
                    }

                    if (concertPages.isNotEmpty()) {
                        val pagerState = rememberPagerState(pageCount = { concertPages.size })

                        // Update current page when pager changes
                        LaunchedEffect(pagerState.currentPage) {
                            currentPage = pagerState.currentPage
                        }

                        // Reset to first page when song changes
                        LaunchedEffect(uiState.currentSongIndex) {
                            currentPage = 0
                            pagerState.scrollToPage(0)
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            val page = concertPages[pageIndex]
                            val file = page.file

                            // Load annotations for this file
                            val annotations by remember(file.id, memberId) {
                                annotationRepository.getAnnotationsByFileAndMember(file.id, memberId)
                            }.collectAsState(initial = emptyList())

                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    file.fileType == com.troubashare.domain.model.FileType.PDF -> {
                                        if (page.pdfPageIndex != null) {
                                            // Swipe mode PDF - show single page
                                            com.troubashare.ui.components.AnnotatedSinglePagePDFViewer(
                                                filePath = file.filePath,
                                                fileId = file.id,
                                                memberId = memberId,
                                                pageIndex = page.pdfPageIndex,
                                                annotations = annotations,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            // Scroll mode PDF - show all pages with internal scrolling
                                            com.troubashare.ui.components.AnnotatedMultiPagePDFViewer(
                                                filePath = file.filePath,
                                                fileId = file.id,
                                                memberId = memberId,
                                                annotations = annotations,
                                                useScrollMode = true,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    file.fileType == com.troubashare.domain.model.FileType.IMAGE -> {
                                        // For images, show with annotations
                                        com.troubashare.ui.components.AnnotatedImageViewer(
                                            filePath = file.filePath,
                                            fileId = file.id,
                                            memberId = memberId,
                                            annotations = annotations,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    else -> {
                                        // Fallback for other file types
                                        com.troubashare.ui.components.FileViewer(
                                            filePath = file.filePath,
                                            fileName = file.fileName,
                                            fileType = file.fileType.name,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
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
                    // Show song position, and page position if in PDF swipe mode
                    val songPosition = "Song ${uiState.currentSongIndex + 1} of ${uiState.totalSongs}"
                    val pagePosition = if (totalPages > 0) " (Page ${currentPage + 1}/$totalPages)" else ""
                    Text(
                        text = songPosition + pagePosition,
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