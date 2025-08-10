package com.troubashare.ui.screens.concert

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.troubashare.domain.model.SongFile

@Composable
fun ConcertSongCard(
    songItem: ConcertSongItem,
    isCurrentSong: Boolean,
    isPlayed: Boolean,
    onClick: () -> Unit,
    onFileClick: (SongFile) -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrentSong -> MaterialTheme.colorScheme.primaryContainer
                isPlayed -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentSong) 8.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Song header with reorder controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = songItem.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (songItem.artist != null) {
                        Text(
                            text = songItem.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Reorder controls
                if (onMoveUp != null || onMoveDown != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (onMoveUp != null) {
                            IconButton(
                                onClick = onMoveUp,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (onMoveDown != null) {
                            IconButton(
                                onClick = onMoveDown,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                
                // Status indicator
                when {
                    isCurrentSong -> {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Current song",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    isPlayed -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Played",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Notes if available
            if (!songItem.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = songItem.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 8.dp,
                            end = 8.dp,
                            top = 4.dp,
                            bottom = 4.dp
                        )
                )
            }
            
            // Files
            if (songItem.files.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(songItem.files) { file ->
                        FileChip(
                            file = file,
                            onClick = { onFileClick(file) }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No files available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun FileChip(
    file: SongFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (file.fileType) {
        com.troubashare.domain.model.FileType.PDF -> Icons.Default.PictureAsPdf
        com.troubashare.domain.model.FileType.IMAGE -> Icons.Default.Image
        com.troubashare.domain.model.FileType.ANNOTATION -> Icons.Default.Edit
    }
    
    val containerColor = when (file.fileType) {
        com.troubashare.domain.model.FileType.PDF -> MaterialTheme.colorScheme.errorContainer
        com.troubashare.domain.model.FileType.IMAGE -> MaterialTheme.colorScheme.tertiaryContainer
        com.troubashare.domain.model.FileType.ANNOTATION -> MaterialTheme.colorScheme.secondaryContainer
    }
    
    AssistChip(
        onClick = onClick,
        label = { 
            Text(
                text = file.fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            ) 
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor
        ),
        modifier = modifier
    )
}