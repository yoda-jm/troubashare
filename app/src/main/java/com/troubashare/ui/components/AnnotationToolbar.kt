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
    isVertical: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = if (isVertical) modifier.fillMaxHeight() else modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        if (isVertical) {
            // Vertical layout for landscape mode
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToolbarContent(
                    drawingState = drawingState,
                    onDrawingStateChanged = onDrawingStateChanged,
                    isVertical = true
                )
            }
        } else {
            // Horizontal layout for portrait mode
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolbarContent(
                    drawingState = drawingState,
                    onDrawingStateChanged = onDrawingStateChanged,
                    isVertical = false
                )
            }
        }
    }
}

@Composable
private fun ToolbarContent(
    drawingState: DrawingState,
    onDrawingStateChanged: (DrawingState) -> Unit,
    isVertical: Boolean
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
    
    // Debug info - will remove later
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "DEBUG: Tool=${drawingState.tool.displayName}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Drawing=${drawingState.isDrawing}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Should show colors=${drawingState.tool != DrawingTool.ERASER && drawingState.tool != DrawingTool.PAN_ZOOM}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    
    // Drawing tools
    if (isVertical) {
        // Vertical layout - tools in a column
        Text(
            text = "Tools",
            style = MaterialTheme.typography.labelLarge
        )
        
        DrawingTool.values().forEach { tool ->
            val icon = when (tool) {
                DrawingTool.PEN -> Icons.Default.Edit
                DrawingTool.HIGHLIGHTER -> Icons.Default.FormatColorFill
                DrawingTool.ERASER -> Icons.AutoMirrored.Filled.Backspace
                DrawingTool.PAN_ZOOM -> Icons.Default.OpenWith
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
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Stroke width in vertical layout
        Text(
            text = "Size: ${drawingState.strokeWidth.toInt()}px",
            style = MaterialTheme.typography.labelMedium
        )
        
        Slider(
            value = drawingState.strokeWidth,
            onValueChange = { width ->
                onDrawingStateChanged(drawingState.copy(strokeWidth = width))
            },
            valueRange = 1f..20f,
            steps = 18,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Colors in vertical layout
        if (drawingState.tool != DrawingTool.ERASER && drawingState.tool != DrawingTool.PAN_ZOOM) {
            Text(
                text = "Colors",
                style = MaterialTheme.typography.labelMedium
            )
            
            availableColors.chunked(2).forEach { colorRow ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorRow.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
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
    } else {
        // Horizontal layout - tools in rows
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
                    DrawingTool.PAN_ZOOM -> Icons.Default.OpenWith
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
        if (drawingState.tool != DrawingTool.ERASER && drawingState.tool != DrawingTool.PAN_ZOOM) {
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