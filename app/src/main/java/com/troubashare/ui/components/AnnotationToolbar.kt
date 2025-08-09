package com.troubashare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.troubashare.domain.model.DrawingState
import com.troubashare.domain.model.DrawingTool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationToolbar(
    drawingState: DrawingState,
    onDrawingStateChanged: (DrawingState) -> Unit,
    onToggleDrawing: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableColors = listOf(
        Color.Red,
        Color.Blue,
        Color.Green,
        Color.Yellow,
        Color.Magenta,
        Color.Cyan,
        Color.Black
    )
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Drawing mode toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onToggleDrawing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (drawingState.isDrawing) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(
                        imageVector = if (drawingState.isDrawing) Icons.Default.Edit else Icons.Default.TouchApp,
                        contentDescription = if (drawingState.isDrawing) "Stop drawing" else "Start drawing"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (drawingState.isDrawing) "Drawing" else "View")
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                IconButton(
                    onClick = onClearAll,
                    enabled = !drawingState.isDrawing
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear all annotations",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            if (drawingState.isDrawing) {
                // Drawing tools
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tools:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    
                    DrawingTool.values().forEach { tool ->
                        val icon = when (tool) {
                            DrawingTool.PEN -> Icons.Default.Edit
                            DrawingTool.HIGHLIGHTER -> Icons.Default.FormatColorFill
                            DrawingTool.ERASER -> Icons.AutoMirrored.Filled.Backspace
                        }
                        
                        FilterChip(
                            onClick = {
                                onDrawingStateChanged(drawingState.copy(tool = tool))
                            },
                            label = { Text(tool.displayName) },
                            selected = drawingState.tool == tool,
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tool.displayName
                                )
                            }
                        )
                    }
                }
                
                // Stroke width slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Size:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    
                    Slider(
                        value = drawingState.strokeWidth,
                        onValueChange = { width ->
                            onDrawingStateChanged(drawingState.copy(strokeWidth = width))
                        },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = "${drawingState.strokeWidth.toInt()}px",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // Color palette
                if (drawingState.tool != DrawingTool.ERASER) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Color:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (drawingState.color == color) 3.dp else 1.dp,
                                            color = if (drawingState.color == color) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onDrawingStateChanged(drawingState.copy(color = color))
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}