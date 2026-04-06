@file:OptIn(ExperimentalMaterial3Api::class)

package com.troubashare.ui.screens.song

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.troubashare.domain.model.FileType
import com.troubashare.domain.model.Member
import com.troubashare.domain.model.SHARED_ANNOTATION_LAYER
import com.troubashare.domain.model.SongFile
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
    var showPersonalLayer by remember {
        mutableStateOf(preferencesManager.getAnnotationLayerVisibility(file.id, memberId))
    }
    var showSharedLayer by remember {
        mutableStateOf(preferencesManager.getSharedLayerVisible(file.id, memberId))
    }
    var useScrollMode by remember {
        mutableStateOf(preferencesManager.getScrollMode(file.id, memberId))
    }

    // Annotation stats
    val annotationRepository = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, RepositoryEntryPoint::class.java)
            .annotationRepository()
    }
    val personalAnnotations by annotationRepository
        .getAnnotationsByFileAndMember(file.id, memberId)
        .collectAsState(initial = emptyList())
    val sharedAnnotations by annotationRepository
        .getAnnotationsByFileAndMember(file.id, SHARED_ANNOTATION_LAYER)
        .collectAsState(initial = emptyList())
    val personalStrokes = personalAnnotations.sumOf { it.strokes.size }
    val sharedStrokes = sharedAnnotations.sumOf { it.strokes.size }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings for ${file.fileName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Personal layer", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = showPersonalLayer,
                        onCheckedChange = {
                            showPersonalLayer = it
                            preferencesManager.setAnnotationLayerVisibility(file.id, memberId, it)
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text("Group layer (read-only)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = showSharedLayer,
                        onCheckedChange = {
                            showSharedLayer = it
                            preferencesManager.setSharedLayerVisible(file.id, memberId, it)
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                if (file.fileType == FileType.PDF) {
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

                // Annotation stats
                if (personalStrokes > 0 || sharedStrokes > 0) {
                    HorizontalDivider()
                    Text(
                        text = "Annotations",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (personalStrokes > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Personal layer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$personalStrokes stroke${if (personalStrokes != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (sharedStrokes > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Shared (group) layer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$sharedStrokes stroke${if (sharedStrokes != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
