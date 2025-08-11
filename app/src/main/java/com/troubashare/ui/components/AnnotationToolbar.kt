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
    onDeleteStroke: ((com.troubashare.domain.model.AnnotationStroke) -> Unit)? = null,
    onSaveAnnotations: (() -> Unit)? = null
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
                    onDeleteStroke = onDeleteStroke,
                    onSaveAnnotations = onSaveAnnotations
                )
            }
        } else {
            // Horizontal layout for portrait mode with minimal height since tools moved to FAB
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .height(90.dp), // Minimal height - only color and size controls
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolbarContent(
                    drawingState = drawingState,
                    onDrawingStateChanged = onDrawingStateChanged,
                    isVertical = false,
                    annotations = annotations,
                    onDeleteStroke = onDeleteStroke,
                    onSaveAnnotations = onSaveAnnotations
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
    onDeleteStroke: ((com.troubashare.domain.model.AnnotationStroke) -> Unit)? = null,
    onSaveAnnotations: (() -> Unit)? = null
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
    
    // Drawing tools section removed - now handled by expandable FAB
    
    if (isVertical) {
        // Stroke width in vertical layout (not shown for PAN_ZOOM tool)
        if (drawingState.tool != DrawingTool.PAN_ZOOM) {
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
        }
        
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
                                    width = if (drawingState.color == color) 4.dp else 2.dp,
                                    color = if (drawingState.color == color) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    onDrawingStateChanged(drawingState.copy(color = color))
                                }
                        ) {
                            // Add checkmark for selected color for better visibility
                            if (drawingState.color == color) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (color == Color.White || color == Color.Yellow) Color.Black else Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Horizontal layout - no tool selection since it's handled by FAB
        
        // Stroke width slider (not shown for PAN_ZOOM tool)
        if (drawingState.tool != DrawingTool.PAN_ZOOM) {
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
                                    width = if (drawingState.color == color) 3.dp else 2.dp,
                                    color = if (drawingState.color == color) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    onDrawingStateChanged(drawingState.copy(color = color))
                                }
                        ) {
                            // Add checkmark for selected color for better visibility
                            if (drawingState.color == color) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (color == Color.White || color == Color.Yellow) Color.Black else Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Show annotation list when in SELECT mode
        if (drawingState.tool == DrawingTool.SELECT && onDeleteStroke != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Annotations",
                    style = MaterialTheme.typography.labelLarge
                )
                
                if (drawingState.selectedStroke != null) {
                    TextButton(
                        onClick = {
                            onDrawingStateChanged(drawingState.copy(selectedStroke = null))
                        }
                    ) {
                        Text("Clear Selection")
                    }
                }
            }
            
            val allStrokes = annotations.flatMap { annotation -> 
                annotation.strokes.map { stroke -> annotation to stroke }
            }
            
            if (allStrokes.isEmpty()) {
                Text(
                    text = "No annotations on this page",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(allStrokes) { (annotation, stroke) ->
                        SelectableAnnotationListItem(
                            stroke = stroke,
                            isSelected = drawingState.selectedStroke?.id == stroke.id,
                            onSelect = { 
                                onDrawingStateChanged(drawingState.copy(selectedStroke = stroke))
                            },
                            onDelete = { onDeleteStroke.invoke(stroke) }
                        )
                    }
                }
            }
        }
        
        // Save annotations button
        if (onSaveAnnotations != null && annotations.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            OutlinedButton(
                onClick = onSaveAnnotations,
                modifier = if (isVertical) Modifier.fillMaxWidth() else Modifier
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save Annotations",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save as PDF")
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
                                    Color(stroke.color.toUInt().toInt())
                                } catch (e: Exception) {
                                    Color.Black
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

@Composable
fun SelectableAnnotationListItem(
    stroke: com.troubashare.domain.model.AnnotationStroke,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onSelect() },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection indicator
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Column {
                    Text(
                        text = when (stroke.tool) {
                            DrawingTool.TEXT -> stroke.text ?: "Text annotation"
                            else -> "${stroke.tool.displayName} stroke"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    try {
                                        Color(stroke.color.toUInt().toInt())
                                    } catch (e: Exception) {
                                        Color.Black
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Size: ${stroke.strokeWidth.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) 
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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