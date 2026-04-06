package com.troubashare.ui.screens.file

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    val activeLayerIsShared by viewModel.activeLayerIsShared.collectAsState()
    val showPersonalLayer by viewModel.showPersonalLayer.collectAsState()
    val showSharedLayer by viewModel.showSharedLayer.collectAsState()
    val isFileLevelView = viewModel.isFileLevelView

    var showLayerMenu by remember { mutableStateOf(false) }

    // Layer display helpers
    val activeLayerName = if (isFileLevelView || activeLayerIsShared) "Group" else "Personal"
    val activeLayerIcon = if (isFileLevelView || activeLayerIsShared) Icons.Default.Group else Icons.Default.Person
    val activeLayerColor = if (isFileLevelView || activeLayerIsShared)
        MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

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
                actions = {
                    Box {
                        IconButton(onClick = { showLayerMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Annotation layers",
                                tint = if (isFileLevelView || activeLayerIsShared)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showLayerMenu,
                            onDismissRequest = { showLayerMenu = false }
                        ) {
                            if (isFileLevelView) {
                                // Pool view: only shared layer — just show it as active
                                Text(
                                    text = "Annotation layer:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text("Group", color = MaterialTheme.colorScheme.tertiary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Group,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Text(
                                            "active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    },
                                    onClick = { showLayerMenu = false }
                                )
                            } else {
                                // Member view: always draw on personal; shared is visibility-only
                                Text(
                                    text = "Drawing on:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text("Personal", color = MaterialTheme.colorScheme.primary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Text(
                                            "active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = { showLayerMenu = false }
                                )
                                HorizontalDivider()
                                Text(
                                    text = "Show layers:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text("Personal")
                                            }
                                            Switch(
                                                checked = showPersonalLayer,
                                                onCheckedChange = { viewModel.setPersonalLayerVisible(it) },
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    },
                                    onClick = { viewModel.setPersonalLayerVisible(!showPersonalLayer) }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Group,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.tertiary
                                                )
                                                Text("Group")
                                            }
                                            Switch(
                                                checked = showSharedLayer,
                                                onCheckedChange = { viewModel.setSharedLayerVisible(it) },
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    },
                                    onClick = { viewModel.setSharedLayerVisible(!showSharedLayer) }
                                )
                            }
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
        ) {
            // Active layer indicator — only visible when in drawing mode
            AnimatedVisibility(
                visible = drawingState.isDrawing,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Surface(
                    color = activeLayerColor.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = activeLayerIcon,
                            contentDescription = null,
                            tint = activeLayerColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeLayerName,
                            style = MaterialTheme.typography.labelMedium,
                            color = activeLayerColor
                        )
                        if (!isFileLevelView) {
                            Text(
                                text = "layer (group layer is read-only)",
                                style = MaterialTheme.typography.labelSmall,
                                color = activeLayerColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

        // Viewer + error overlay
        Box(modifier = Modifier.weight(1f)) {
            AnnotatableFileViewer(
                filePath = songFile.filePath,
                fileName = songFile.fileName,
                fileType = songFile.fileType.name,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )

            // Error/success toast — overlaid at the top so it never blocks the FAB
            uiState.errorMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
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
        } // end Column
    }
}