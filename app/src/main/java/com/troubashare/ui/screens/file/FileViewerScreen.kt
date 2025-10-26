package com.troubashare.ui.screens.file

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troubashare.ui.components.AnnotatableFileViewer
import com.troubashare.domain.model.SongFile
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.repository.AnnotationRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.file.FileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    songFile: SongFile,
    songTitle: String,
    memberName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use the file's member ID - this represents the member who owns this specific file
    // For annotation purposes, we should use the file owner's member ID
    val currentMemberId = songFile.memberId.ifBlank { 
        println("DEBUG FileViewerScreen: WARNING - songFile.memberId is blank, using fallback")
        "unknown-member" 
    }
    
    // DEBUG: Log member ID being used
    println("DEBUG FileViewerScreen: Using memberId: '$currentMemberId' (from songFile.memberId='${songFile.memberId}')")
    
    val context = LocalContext.current
    val database = remember { TroubaShareDatabase.getInstance(context) }
    val fileManager = remember { FileManager(context) }
    val annotationRepository = remember { AnnotationRepository(database) }
    val songRepository = remember { SongRepository(database, fileManager, annotationRepository) }
    
    val viewModel: FileViewerViewModel = viewModel { 
        println("DEBUG FileViewerScreen: Creating ViewModel with fileId='${songFile.id}', memberId='$currentMemberId', songId='${songFile.songId}'")
        println("DEBUG FileViewerScreen: SongFile details - fileName='${songFile.fileName}', filePath='${songFile.filePath}'")
        FileViewerViewModel(
            annotationRepository = annotationRepository,
            songRepository = songRepository,
            fileId = songFile.id,
            memberId = currentMemberId,
            songId = songFile.songId,
            filePath = songFile.filePath,
            context = context
        ) 
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val drawingState by viewModel.drawingState.collectAsState()

    // Save annotations when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            println("DEBUG FileViewerScreen: Screen disposing, saving annotations immediately")
            viewModel.saveAnnotationsNow()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = songFile.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$songTitle • $memberName",
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
                // Removed actions - using FAB for drawing mode toggle
            )
        }
    ) { paddingValues ->
        // Always use AnnotatableFileViewer - it handles both viewing and annotation modes
        AnnotatableFileViewer(
            filePath = songFile.filePath,
            fileName = songFile.fileName,
            fileType = songFile.fileType.name,
            viewModel = viewModel,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
        
        // Show error/success message if any
        uiState.errorMessage?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.contains("success", ignoreCase = true)) 
                        MaterialTheme.colorScheme.primaryContainer
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    color = if (message.contains("success", ignoreCase = true))
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else 
                        MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
        }
    }
}