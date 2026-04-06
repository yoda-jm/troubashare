package com.troubashare.ui.screens.song

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troubashare.domain.model.FileSelection
import com.troubashare.domain.model.FileType
import com.troubashare.domain.model.Member
import com.troubashare.domain.model.SelectionType
import com.troubashare.domain.model.SongFile

/**
 * Matrix dialog showing which files are assigned to which members.
 * Rows = pool files, Columns = members.
 * Checkbox at each intersection controls FileSelection.
 */
@Composable
fun FileSelectionMatrixDialog(
    poolFiles: List<SongFile>,
    members: List<Member>,
    selections: List<FileSelection>,
    onToggle: (songFileId: String, memberId: String, selected: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val displayFiles = poolFiles.filter { it.fileType != FileType.ANNOTATION }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File Assignments") },
        text = {
            if (displayFiles.isEmpty()) {
                Text(
                    text = "No files in the pool yet. Add some files first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Build a lookup: (fileId, memberId) -> hasSelection
                val selectionSet = selections
                    .filter { it.selectionType == SelectionType.MEMBER }
                    .map { it.songFileId to it.memberId }
                    .toSet()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Horizontal scroll for many members
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        // First column: file names
                        Column {
                            // Header spacer
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .width(140.dp)
                            )
                            displayFiles.forEach { file ->
                                Box(
                                    modifier = Modifier
                                        .height(48.dp)
                                        .width(140.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = file.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }

                        // One column per member
                        members.forEach { member ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Member name header
                                Box(
                                    modifier = Modifier
                                        .height(48.dp)
                                        .width(72.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }

                                // Checkbox per file
                                displayFiles.forEach { file ->
                                    val isSelected = (file.id to member.id) in selectionSet
                                    Box(
                                        modifier = Modifier
                                            .height(48.dp)
                                            .width(72.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                onToggle(file.id, member.id, checked)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
