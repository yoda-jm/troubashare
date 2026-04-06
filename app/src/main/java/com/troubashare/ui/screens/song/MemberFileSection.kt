@file:OptIn(ExperimentalMaterial3Api::class)

package com.troubashare.ui.screens.song

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.troubashare.data.preferences.AnnotationPreferencesManager
import com.troubashare.di.RepositoryEntryPoint
import com.troubashare.domain.model.AnnotationLayer
import com.troubashare.domain.model.FileType
import com.troubashare.domain.model.Member
import com.troubashare.domain.model.SongFile
import com.troubashare.ui.screens.file.LAYER_COLORS
import com.troubashare.ui.screens.file.layerColor
import dagger.hilt.android.EntryPointAccessors

@Composable
fun MemberFileSection(
    member: Member,
    assignedFiles: List<SongFile>,
    poolFiles: List<SongFile>,
    onRemoveAssignment: (SongFile) -> Unit,
    onAssign: (SongFile) -> Unit,
    onFileView: (SongFile) -> Unit,
    onFileMoveUp: (SongFile, Int) -> Unit,
    onFileMoveDown: (SongFile, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPickerDialog by remember { mutableStateOf(false) }

    // Pool files not yet assigned to this member
    val assignedIds = assignedFiles.map { it.id }.toSet()
    val availableToAdd = poolFiles.filter { it.id !in assignedIds }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleSmall
                )
                if (availableToAdd.isNotEmpty()) {
                    TextButton(
                        onClick = { showPickerDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (assignedFiles.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No files assigned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                assignedFiles.forEachIndexed { index, file ->
                    AssignedFileRow(
                        file = file,
                        memberId = member.id,
                        position = index,
                        totalFiles = assignedFiles.size,
                        onRemove = { onRemoveAssignment(file) },
                        onView = { onFileView(file) },
                        onMoveUp = { onFileMoveUp(file, index) },
                        onMoveDown = { onFileMoveDown(file, index) }
                    )
                    if (index < assignedFiles.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }

    if (showPickerDialog) {
        AddFromPoolDialog(
            availableFiles = availableToAdd,
            onAssign = { file ->
                onAssign(file)
                showPickerDialog = false
            },
            onDismiss = { showPickerDialog = false }
        )
    }
}

@Composable
private fun AssignedFileRow(
    file: SongFile,
    memberId: String,
    position: Int,
    totalFiles: Int,
    onRemove: () -> Unit,
    onView: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember { AnnotationPreferencesManager(context) }
    var showSettings by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (file.fileType) {
                FileType.PDF -> Icons.Default.PictureAsPdf
                FileType.IMAGE -> Icons.Default.Image
                FileType.ANNOTATION -> Icons.Default.Edit
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = file.fileName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Reorder arrows (only when multiple files)
        if (totalFiles > 1) {
            IconButton(
                onClick = onMoveUp,
                enabled = position > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move up",
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = position < totalFiles - 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        IconButton(onClick = { showSettings = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "File settings",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove from my list",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showSettings) {
        MemberFileSettingsDialog(
            file = file,
            memberId = memberId,
            preferencesManager = preferencesManager,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun MemberFileSettingsDialog(
    file: SongFile,
    memberId: String,
    preferencesManager: AnnotationPreferencesManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var displayName by remember {
        mutableStateOf(preferencesManager.getAnnotationLayerName(file.id, memberId) ?: file.fileName)
    }
    var useScrollMode by remember {
        mutableStateOf(preferencesManager.getScrollMode(file.id, memberId))
    }
    var hiddenLayerIds by remember {
        mutableStateOf(preferencesManager.getHiddenLayerIds(file.id, memberId))
    }

    val annotationRepository = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, RepositoryEntryPoint::class.java)
            .annotationRepository()
    }

    // Observe layers live
    val allLayers by annotationRepository
        .getLayersForFile(file.id)
        .collectAsState(initial = emptyList())

    // Only layers this member can see: their own + shared
    val viewerLayers = allLayers.filter { it.ownerId == memberId || it.isShared }

    // All annotations for visible layers in a single flow — then filter per layer in the UI
    val viewerLayerIds = viewerLayers.map { it.id }
    val allLayerAnnotations by remember(viewerLayerIds) {
        annotationRepository.getAnnotationsByLayers(viewerLayerIds)
    }.collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings for ${file.fileName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        preferencesManager.setAnnotationLayerName(
                            file.id, memberId, it.trim().takeIf { s -> s != file.fileName }
                        )
                    },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Name shown in concert mode") }
                )

                // Layer visibility toggles
                if (viewerLayers.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        "Layers",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    viewerLayers.forEach { layer ->
                        val isVisible = layer.id !in hiddenLayerIds
                        val strokes = allLayerAnnotations.filter { it.layerId == layer.id }.sumOf { it.strokes.size }
                        val layerColor = layerColor(
                            layer,
                            sharedColor = MaterialTheme.colorScheme.tertiary,
                            personalColor = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (layer.isShared) Icons.Default.Group else Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = layerColor
                                )
                                Column {
                                    Text(
                                        layer.name + if (layer.isShared) " (read-only)" else "",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (strokes > 0) {
                                        Text(
                                            "$strokes stroke${if (strokes != 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Switch(
                                checked = isVisible,
                                onCheckedChange = { visible ->
                                    hiddenLayerIds = if (visible) hiddenLayerIds - layer.id
                                                    else hiddenLayerIds + layer.id
                                    preferencesManager.setLayerHidden(
                                        file.id, memberId, layer.id, !visible
                                    )
                                },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }

                if (file.fileType == FileType.PDF) {
                    HorizontalDivider()
                    Text("View mode", style = MaterialTheme.typography.bodyMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !useScrollMode,
                            onClick = {
                                useScrollMode = false
                                preferencesManager.setScrollMode(file.id, memberId, false)
                            },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text("Swipe") }
                        SegmentedButton(
                            selected = useScrollMode,
                            onClick = {
                                useScrollMode = true
                                preferencesManager.setScrollMode(file.id, memberId, true)
                            },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text("Scroll") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun AddFromPoolDialog(
    availableFiles: List<SongFile>,
    onAssign: (SongFile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add from song pool") },
        text = {
            Column {
                availableFiles.forEach { file ->
                    TextButton(
                        onClick = { onAssign(file) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = when (file.fileType) {
                                FileType.PDF -> Icons.Default.PictureAsPdf
                                FileType.IMAGE -> Icons.Default.Image
                                FileType.ANNOTATION -> Icons.Default.Edit
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
