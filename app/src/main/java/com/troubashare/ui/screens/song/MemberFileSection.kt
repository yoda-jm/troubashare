package com.troubashare.ui.screens.song

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.troubashare.domain.model.FileType
import com.troubashare.domain.model.Member
import com.troubashare.domain.model.SongFile
import com.troubashare.ui.components.ImagePickerButton
import com.troubashare.ui.components.PDFPickerButton

@Composable
fun MemberFileSection(
    member: Member,
    files: List<SongFile>,
    onFileUpload: (Uri, String) -> Unit,
    onFileDelete: (SongFile) -> Unit,
    onFileView: (SongFile) -> Unit,
    onFileMoveUp: (SongFile, Int) -> Unit,
    onFileMoveDown: (SongFile, Int) -> Unit,
    isUploading: Boolean,
    modifier: Modifier = Modifier
) {
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
            // Member name
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Upload buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PDFPickerButton(
                    onPdfSelected = onFileUpload,
                    text = "PDF",
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f)
                )

                ImagePickerButton(
                    onImageSelected = onFileUpload,
                    text = "Image",
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f)
                )
            }

            // Display uploaded files - hide annotation files since they're managed through properties dialog
            val displayFiles = files.filter { file ->
                file.fileType != FileType.ANNOTATION
            }

            if (displayFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                displayFiles.forEachIndexed { index, file ->
                    FileItem(
                        file = file,
                        allFiles = files, // Pass all member files to check for annotations
                        position = index,
                        totalFiles = displayFiles.size,
                        onDelete = { onFileDelete(file) },
                        onView = { onFileView(file) },
                        onMoveUp = { onFileMoveUp(file, index) },
                        onMoveDown = { onFileMoveDown(file, index) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
