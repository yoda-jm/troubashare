package com.troubashare.ui.screens.file

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troubashare.ui.components.FileViewer
import com.troubashare.ui.components.AnnotatableFileViewer
import com.troubashare.domain.model.SongFile
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.repository.AnnotationRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    songFile: SongFile,
    songTitle: String,
    memberName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // For now, using a placeholder member ID. In a real implementation, 
    // this would come from the current user's member ID
    val currentMemberId = "current-member-id"
    
    val context = LocalContext.current
    val database = remember { TroubaShareDatabase.getInstance(context) }
    val annotationRepository = remember { AnnotationRepository(database) }
    
    val viewModel: FileViewerViewModel = viewModel { 
        FileViewerViewModel(annotationRepository, songFile.id, currentMemberId) 
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val drawingState by viewModel.drawingState.collectAsState()
    var showAnnotationMode by remember { mutableStateOf(false) }
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
                actions = {
                    IconButton(
                        onClick = { 
                            showAnnotationMode = !showAnnotationMode
                            if (!showAnnotationMode) {
                                // Exit drawing mode when closing annotations
                                viewModel.updateDrawingState(drawingState.copy(isDrawing = false))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (showAnnotationMode) Icons.Default.Close else Icons.Default.Edit,
                            contentDescription = if (showAnnotationMode) "Close annotations" else "Annotate",
                            tint = if (showAnnotationMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { /* TODO: Share file */ }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showAnnotationMode) {
            AnnotatableFileViewer(
                filePath = songFile.filePath,
                fileName = songFile.fileName,
                fileType = songFile.fileType.name,
                viewModel = viewModel,
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            FileViewer(
                filePath = songFile.filePath,
                fileName = songFile.fileName,
                fileType = songFile.fileType.name,
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
        
        // Show error message if any
        uiState.errorMessage?.let { errorMessage ->
            LaunchedEffect(errorMessage) {
                // You could show a snackbar here
                viewModel.clearError()
            }
        }
    }
}