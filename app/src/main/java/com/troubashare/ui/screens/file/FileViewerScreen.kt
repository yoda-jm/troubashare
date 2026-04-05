package com.troubashare.ui.screens.file

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troubashare.ui.components.AnnotatableFileViewer
import com.troubashare.domain.model.SongFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    songFile: SongFile,
    songTitle: String,
    memberName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: FileViewerViewModel = hiltViewModel()
    
    val uiState by viewModel.uiState.collectAsState()
    val drawingState by viewModel.drawingState.collectAsState()

    // Save annotations when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
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