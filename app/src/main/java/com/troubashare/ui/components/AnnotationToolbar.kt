package com.troubashare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowDropDown
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
    modifier: Modifier = Modifier,
    annotations: List<com.troubashare.domain.model.Annotation> = emptyList(),
    onDeleteStroke: ((com.troubashare.domain.model.AnnotationStroke) -> Unit)? = null
) {
    Surface(
        modifier = if (isVertical) 
            modifier
                .fillMaxHeight()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        else 
            modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
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
                    isVertical = true,
                    annotations = annotations,
                    onDeleteStroke = onDeleteStroke
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
                    isVertical = false,
                    annotations = annotations,
                    onDeleteStroke = onDeleteStroke
                )
            }
        }
    }
}

@Composable
private fun ToolbarContent(
    drawingState: DrawingState,
    onDrawingStateChanged: (DrawingState) -> Unit,
    isVertical: Boolean,
    annotations: List<com.troubashare.domain.model.Annotation> = emptyList(),
    onDeleteStroke: ((com.troubashare.domain.model.AnnotationStroke) -> Unit)? = null
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
    
    // Drawing tools
    if (isVertical) {
        // Vertical layout - tools in a column
        // Tool selector dropdown
        var expanded by remember { mutableStateOf(false) }
        
        Text(
            text = "Tool",
            style = MaterialTheme.typography.labelLarge
        )
        
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(drawingState.tool.displayName)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown"
                    )
                }
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DrawingTool.values().forEach { tool ->
                    val icon = when (tool) {
                        DrawingTool.PEN -> Icons.Default.Edit
                        DrawingTool.HIGHLIGHTER -> Icons.Default.FormatColorFill
                        DrawingTool.ERASER -> Icons.AutoMirrored.Filled.Backspace
                        DrawingTool.TEXT -> Icons.Default.TextFields
                        DrawingTool.SELECT -> Icons.Default.TouchApp
                        DrawingTool.PAN_ZOOM -> Icons.Default.OpenWith
                    }
                    
                    DropdownMenuItem(
                        text = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tool.displayName,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tool.displayName)
                            }
                        },
                        onClick = {
                            onDrawingStateChanged(drawingState.copy(tool = tool))
                            expanded = false
                        }
                    )
                }
            }
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
        if (drawingState.tool == DrawingTool.PEN || drawingState.tool == DrawingTool.HIGHLIGHTER || drawingState.tool == DrawingTool.TEXT) {
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
                    DrawingTool.TEXT -> Icons.Default.TextFields
                    DrawingTool.SELECT -> Icons.Default.TouchApp
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
        if (drawingState.tool == DrawingTool.PEN || drawingState.tool == DrawingTool.HIGHLIGHTER || drawingState.tool == DrawingTool.TEXT) {
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
        
        // Show annotation list when in SELECT mode
        if (drawingState.tool == DrawingTool.SELECT && onDeleteStroke != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text(
                text = "Annotations",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            val allStrokes = annotations.flatMap { annotation -> 
                annotation.strokes.map { stroke -> annotation to stroke }
            }
            
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(allStrokes) { (annotation, stroke) ->
                    AnnotationListItem(
                        stroke = stroke,
                        onDelete = { onDeleteStroke.invoke(stroke) }
                    )
                }
            }
        }
    }
}

@Composable
fun AnnotationListItem(
    stroke: com.troubashare.domain.model.AnnotationStroke,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (stroke.tool) {
                        DrawingTool.TEXT -> stroke.text ?: "Text annotation"
                        else -> "${stroke.tool.displayName} stroke"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                try {
                                    androidx.compose.ui.graphics.Color(stroke.color.toULong())
                                } catch (e: Exception) {
                                    androidx.compose.ui.graphics.Color.Red
                                },
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Size: ${stroke.strokeWidth.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}