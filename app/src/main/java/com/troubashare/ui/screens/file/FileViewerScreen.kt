package com.troubashare.ui.screens.file

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troubashare.domain.model.AnnotationLayer
import com.troubashare.domain.model.SongFile
import com.troubashare.ui.components.AnnotatableFileViewer
import kotlinx.coroutines.launch

/** Fixed colour palette for layer indicators (index matches AnnotationLayer.colorIndex). */
val LAYER_COLORS: List<Color> = listOf(
    Color(0xFF2196F3),  // Blue
    Color(0xFF4CAF50),  // Green
    Color(0xFFFF9800),  // Orange
    Color(0xFF9C27B0),  // Purple
    Color(0xFFE91E63),  // Pink
    Color(0xFF00BCD4),  // Cyan
)

fun layerColor(layer: AnnotationLayer, sharedColor: Color, personalColor: Color): Color =
    if (layer.isShared) sharedColor
    else LAYER_COLORS.getOrElse(layer.colorIndex) { personalColor }

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
    val layers by viewModel.layers.collectAsState()
    val activeLayerId by viewModel.activeLayerId.collectAsState()
    val activeLayer by viewModel.activeLayer.collectAsState()
    val hiddenLayerIds by viewModel.hiddenLayerIds.collectAsState()
    val isFileLevelView = viewModel.isFileLevelView

    var showLayerSheet by remember { mutableStateOf(false) }

    // Active layer display helpers
    val sharedColor = MaterialTheme.colorScheme.tertiary
    val personalColor = MaterialTheme.colorScheme.primary
    val activeColor = activeLayer?.let { layerColor(it, sharedColor, personalColor) }
        ?: MaterialTheme.colorScheme.onSurface

    // Save annotations when leaving the screen
    DisposableEffect(Unit) {
        onDispose { viewModel.saveAnnotationsNow() }
    }

    if (showLayerSheet) {
        LayerManagementSheet(
            layers = layers,
            activeLayerId = activeLayerId,
            hiddenLayerIds = hiddenLayerIds,
            isFileLevelView = isFileLevelView,
            viewModel = viewModel,
            onDismiss = { showLayerSheet = false }
        )
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
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showLayerSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Annotation layers",
                            tint = activeColor
                        )
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
            // Active-layer banner — shown during drawing
            AnimatedVisibility(
                visible = drawingState.isDrawing,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Surface(
                    color = activeColor.copy(alpha = 0.12f),
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
                            imageVector = if (activeLayer?.isShared == true) Icons.Default.Group else Icons.Default.Person,
                            contentDescription = null,
                            tint = activeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeLayer?.name ?: "—",
                            style = MaterialTheme.typography.labelMedium,
                            color = activeColor
                        )
                        if (!isFileLevelView && activeLayer?.isShared == true) {
                            Text(
                                text = "(read-only)",
                                style = MaterialTheme.typography.labelSmall,
                                color = activeColor.copy(alpha = 0.7f)
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

                uiState.errorMessage?.let { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.contains("success", ignoreCase = true) ||
                                               message.startsWith("✅"))
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = if (message.contains("success", ignoreCase = true) ||
                                       message.startsWith("✅"))
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
    }
}

// ── Layer management bottom sheet ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerManagementSheet(
    layers: List<AnnotationLayer>,
    activeLayerId: String?,
    hiddenLayerIds: Set<String>,
    isFileLevelView: Boolean,
    viewModel: FileViewerViewModel,
    onDismiss: () -> Unit
) {
    val sharedColor = MaterialTheme.colorScheme.tertiary
    val personalColor = MaterialTheme.colorScheme.primary

    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AnnotationLayer?>(null) }
    var deleteTarget by remember { mutableStateOf<AnnotationLayer?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Properly hide sheet with animation before removing from composition
    fun dismissSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Layers", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add layer")
                }
            }
            HorizontalDivider()

            if (layers.isEmpty()) {
                Text(
                    text = "No layers yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(layers, key = { it.id }) { layer ->
                        val isActive = layer.id == activeLayerId
                        val isVisible = layer.id !in hiddenLayerIds
                        val canEdit = viewModel.canEditLayer(layer.id)
                        val color = layerColor(layer, sharedColor, personalColor)

                        LayerRow(
                            layer = layer,
                            isActive = isActive,
                            isVisible = isVisible,
                            canEdit = canEdit,
                            color = color,
                            onSelect = {
                                viewModel.setActiveLayer(layer.id)
                                dismissSheet()
                            },
                            onToggleVisible = { viewModel.toggleLayerVisible(layer.id) },
                            onRename = { renameTarget = layer },
                            onDelete = { deleteTarget = layer }
                        )
                    }
                }
            }
        }
    }

    // Add layer dialog
    if (showAddDialog) {
        LayerNameDialog(
            title = "New layer",
            initial = "",
            onConfirm = { name ->
                viewModel.createLayer(name)
                showAddDialog = false
                dismissSheet()
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Rename dialog
    renameTarget?.let { target ->
        LayerNameDialog(
            title = "Rename layer",
            initial = target.name,
            onConfirm = { name ->
                viewModel.renameLayer(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    // Delete confirmation
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("All strokes on this layer will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLayer(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LayerRow(
    layer: AnnotationLayer,
    isActive: Boolean,
    isVisible: Boolean,
    canEdit: Boolean,
    color: Color,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val sharedColor = MaterialTheme.colorScheme.tertiary
    val personalColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visibility toggle
        IconButton(onClick = onToggleVisible) {
            Icon(
                imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isVisible) "Hide layer" else "Show layer",
                tint = if (isVisible) color else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }

        // Layer icon (shared vs personal)
        Icon(
            imageVector = if (layer.isShared) Icons.Default.Group else Icons.Default.Person,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Name + active chip — tapping selects as active layer (if writable)
        TextButton(
            onClick = onSelect,
            modifier = Modifier.weight(1f),
            enabled = canEdit
        ) {
            Text(
                text = layer.name,
                style = if (isActive) MaterialTheme.typography.labelLarge
                        else MaterialTheme.typography.bodyMedium,
                color = if (isActive) color else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isActive) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "active",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (!canEdit) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Read-only",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Edit actions (only for writable layers)
        if (canEdit) {
            IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LayerNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Layer name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
