package com.troubashare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.toArgb
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
    onSaveAnnotations: (() -> Unit)? = null,
    onStrokeUpdated: ((old: com.troubashare.domain.model.AnnotationStroke, new: com.troubashare.domain.model.AnnotationStroke) -> Unit)? = null
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
            // Vertical layout for landscape mode - use Column with verticalScroll for better slider interaction
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToolbarContent(
                    drawingState = drawingState,
                    onDrawingStateChanged = onDrawingStateChanged,
                    isVertical = true,
                    annotations = annotations,
                    onDeleteStroke = onDeleteStroke,
                    onSaveAnnotations = onSaveAnnotations,
                    onStrokeUpdated = onStrokeUpdated
                )
                // Add bottom spacer to ensure delete/clear buttons are visible
                Spacer(modifier = Modifier.height(80.dp))
            }
        } else {
            // Horizontal layout for portrait mode with minimal height since tools moved to FAB
            // Use LazyColumn for scrolling when in SELECT mode
            LazyColumn(
                modifier = Modifier
                    .padding(16.dp)
                    .let {
                        // Allow more height when in SELECT mode to show editing panel
                        if (drawingState.tool == DrawingTool.SELECT) {
                            it.heightIn(min = 90.dp, max = 450.dp)
                        } else {
                            it.height(90.dp) // Minimal height - only color and size controls
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ToolbarContent(
                        drawingState = drawingState,
                        onDrawingStateChanged = onDrawingStateChanged,
                        isVertical = false,
                        annotations = annotations,
                        onDeleteStroke = onDeleteStroke,
                        onSaveAnnotations = onSaveAnnotations,
                        onStrokeUpdated = onStrokeUpdated
                    )
                }
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
    onSaveAnnotations: (() -> Unit)? = null,
    onStrokeUpdated: ((old: com.troubashare.domain.model.AnnotationStroke, new: com.troubashare.domain.model.AnnotationStroke) -> Unit)? = null
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

    // Helper function to update stroke property immediately
    fun updateStrokeProperty(updatedStroke: com.troubashare.domain.model.AnnotationStroke) {
        val oldStroke = drawingState.selectedStroke
        if (oldStroke != null && onStrokeUpdated != null) {
            // Update UI state immediately
            onDrawingStateChanged(drawingState.copy(selectedStroke = updatedStroke))
            // Update memory (triggers auto-save in ViewModel)
            onStrokeUpdated.invoke(oldStroke, updatedStroke)
        }
    }

    // Drawing tools section removed - now handled by expandable FAB

    if (isVertical) {
        // Stroke width in vertical layout (not shown for PAN_ZOOM or SELECT tools)
        if (drawingState.tool != DrawingTool.PAN_ZOOM && drawingState.tool != DrawingTool.SELECT) {
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

        // Opacity slider for PEN tool
        if (drawingState.tool == DrawingTool.PEN) {
            val opacityPercentage = (drawingState.opacity * 100).toInt()
            val isHighlighterMode = drawingState.opacity in 0.35f..0.55f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Opacity: $opacityPercentage%",
                    style = MaterialTheme.typography.labelMedium
                )
                if (isHighlighterMode) {
                    Icon(
                        imageVector = Icons.Default.FormatColorFill,
                        contentDescription = "Highlighter mode",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Slider(
                value = drawingState.opacity,
                onValueChange = { rawOpacity ->
                    // Magnetic snap to highlighter mode (0.45)
                    val snappedOpacity = if (rawOpacity in 0.40f..0.50f) {
                        0.45f
                    } else {
                        rawOpacity
                    }
                    onDrawingStateChanged(drawingState.copy(opacity = snappedOpacity))
                },
                valueRange = 0.1f..1f,
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

        // Show annotation list and editing controls when in SELECT mode (VERTICAL/LANDSCAPE LAYOUT)
        if (drawingState.tool == DrawingTool.SELECT && onDeleteStroke != null) {
            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            // Stroke selection dropdown
            StrokeSelectionDropdown(
                annotations = annotations,
                selectedStroke = drawingState.selectedStroke,
                onStrokeSelected = { stroke ->
                    onDrawingStateChanged(drawingState.copy(selectedStroke = stroke))
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Selected stroke editing panel
            if (drawingState.selectedStroke != null) {
                Text(
                    text = "Edit Selected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = when (drawingState.selectedStroke.tool) {
                        DrawingTool.TEXT -> drawingState.selectedStroke.text ?: "Text annotation"
                        else -> "${drawingState.selectedStroke.tool.displayName} stroke"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                // Color picker
                if (drawingState.selectedStroke.tool in listOf(DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.TEXT)) {
                    Text(
                        text = "Color:",
                        style = MaterialTheme.typography.labelSmall
                    )
                    availableColors.chunked(3).forEach { colorRow ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            colorRow.forEach { color ->
                                val isCurrentColor = try {
                                    Color(drawingState.selectedStroke.color.toUInt().toInt()) == color
                                } catch (e: Exception) {
                                    false
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isCurrentColor) 3.dp else 1.dp,
                                            color = if (isCurrentColor)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            // Update immediately and persist to memory
                                            val updatedStroke = drawingState.selectedStroke.copy(
                                                color = color.toArgb().toUInt().toLong()
                                            )
                                            updateStrokeProperty(updatedStroke)
                                        }
                                ) {
                                    if (isCurrentColor) {
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

                // Width slider
                if (drawingState.selectedStroke.tool in listOf(DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.ERASER)) {
                    Text(
                        text = "Width: ${drawingState.selectedStroke.strokeWidth.toInt()}px",
                        style = MaterialTheme.typography.labelSmall
                    )

                    Slider(
                        value = drawingState.selectedStroke.strokeWidth,
                        onValueChange = { newWidth ->
                            // Update immediately and persist to memory
                            val updatedStroke = drawingState.selectedStroke.copy(strokeWidth = newWidth)
                            updateStrokeProperty(updatedStroke)
                        },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Opacity slider for PEN tool
                if (drawingState.selectedStroke.tool == DrawingTool.PEN) {
                    val opacityPercentage = (drawingState.selectedStroke.opacity * 100).toInt()
                    val isHighlighterMode = drawingState.selectedStroke.opacity in 0.35f..0.55f

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Opacity: $opacityPercentage%",
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (isHighlighterMode) {
                            Icon(
                                imageVector = Icons.Default.FormatColorFill,
                                contentDescription = "Highlighter mode",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Slider(
                        value = drawingState.selectedStroke.opacity,
                        onValueChange = { newOpacity ->
                            // Magnetic snap to highlighter mode
                            val snappedOpacity = if (newOpacity in 0.40f..0.50f) {
                                0.45f
                            } else {
                                newOpacity
                            }
                            // Update immediately and persist to memory
                            val updatedStroke = drawingState.selectedStroke.copy(opacity = snappedOpacity)
                            updateStrokeProperty(updatedStroke)
                        },
                        valueRange = 0.1f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Delete and clear selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onDeleteStroke.invoke(drawingState.selectedStroke)
                            onDrawingStateChanged(drawingState.copy(selectedStroke = null))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onDrawingStateChanged(drawingState.copy(selectedStroke = null))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // Empty state message when nothing is selected
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap a stroke to select it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    } else {
        // Horizontal layout - no tool selection since it's handled by FAB

        // Stroke width slider (not shown for PAN_ZOOM or SELECT tools)
        if (drawingState.tool != DrawingTool.PAN_ZOOM && drawingState.tool != DrawingTool.SELECT) {
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

        // Opacity slider for PEN tool
        if (drawingState.tool == DrawingTool.PEN) {
            val opacityPercentage = (drawingState.opacity * 100).toInt()
            val isHighlighterMode = drawingState.opacity in 0.35f..0.55f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Opacity:",
                    style = MaterialTheme.typography.labelMedium
                )

                Slider(
                    value = drawingState.opacity,
                    onValueChange = { rawOpacity ->
                        // Magnetic snap to highlighter mode (0.45)
                        val snappedOpacity = if (rawOpacity in 0.40f..0.50f) {
                            0.45f
                        } else {
                            rawOpacity
                        }
                        onDrawingStateChanged(drawingState.copy(opacity = snappedOpacity))
                    },
                    valueRange = 0.1f..1f,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "$opacityPercentage%",
                    style = MaterialTheme.typography.bodySmall
                )

                if (isHighlighterMode) {
                    Icon(
                        imageVector = Icons.Default.FormatColorFill,
                        contentDescription = "Highlighter mode",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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
        
        // Show annotation list and editing controls when in SELECT mode
        if (drawingState.tool == DrawingTool.SELECT && onDeleteStroke != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Selected stroke editing panel
            if (drawingState.selectedStroke != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Edit Selected",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Row {
                                // Delete button
                                IconButton(
                                    onClick = {
                                        onDeleteStroke.invoke(drawingState.selectedStroke)
                                        onDrawingStateChanged(drawingState.copy(selectedStroke = null))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }

                                // Clear selection button
                                IconButton(
                                    onClick = {
                                        onDrawingStateChanged(drawingState.copy(selectedStroke = null))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear selection",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Show tool type
                        Text(
                            text = when (drawingState.selectedStroke.tool) {
                                DrawingTool.TEXT -> drawingState.selectedStroke.text ?: "Text annotation"
                                else -> "${drawingState.selectedStroke.tool.displayName} stroke"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // Color picker for pen, highlighter, and text
                        if (drawingState.selectedStroke.tool in listOf(DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.TEXT)) {
                            Text(
                                text = "Color:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                availableColors.forEach { color ->
                                    val isCurrentColor = try {
                                        Color(drawingState.selectedStroke.color.toUInt().toInt()) == color
                                    } catch (e: Exception) {
                                        false
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isCurrentColor) 3.dp else 1.dp,
                                                color = if (isCurrentColor)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                // Update immediately and persist to memory
                                                val updatedStroke = drawingState.selectedStroke.copy(
                                                    color = color.toArgb().toUInt().toLong()
                                                )
                                                updateStrokeProperty(updatedStroke)
                                            }
                                    ) {
                                        if (isCurrentColor) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (color == Color.White || color == Color.Yellow) Color.Black else Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Width slider for pen, highlighter, eraser
                        if (drawingState.selectedStroke.tool in listOf(DrawingTool.PEN, DrawingTool.HIGHLIGHTER, DrawingTool.ERASER)) {
                            Text(
                                text = "Width: ${drawingState.selectedStroke.strokeWidth.toInt()}px",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Slider(
                                value = drawingState.selectedStroke.strokeWidth,
                                onValueChange = { newWidth ->
                                    // Update immediately and persist to memory
                                    val updatedStroke = drawingState.selectedStroke.copy(strokeWidth = newWidth)
                                    updateStrokeProperty(updatedStroke)
                                },
                                valueRange = 1f..20f,
                                steps = 18
                            )
                        }

                        // Opacity slider for PEN tool strokes
                        if (drawingState.selectedStroke.tool == DrawingTool.PEN) {
                            val opacityPercentage = (drawingState.selectedStroke.opacity * 100).toInt()
                            val isHighlighterMode = drawingState.selectedStroke.opacity in 0.35f..0.55f

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Opacity: $opacityPercentage%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (isHighlighterMode) {
                                    Icon(
                                        imageVector = Icons.Default.FormatColorFill,
                                        contentDescription = "Highlighter mode",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Slider(
                                value = drawingState.selectedStroke.opacity,
                                onValueChange = { newOpacity ->
                                    // Magnetic snap to highlighter mode (0.45)
                                    val snappedOpacity = if (newOpacity in 0.40f..0.50f) {
                                        0.45f
                                    } else {
                                        newOpacity
                                    }
                                    // Update immediately and persist to memory
                                    val updatedStroke = drawingState.selectedStroke.copy(opacity = snappedOpacity)
                                    updateStrokeProperty(updatedStroke)
                                },
                                valueRange = 0.1f..1f
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // Empty state message when nothing is selected
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap a stroke to select it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Stroke selection dropdown
            StrokeSelectionDropdown(
                annotations = annotations,
                selectedStroke = drawingState.selectedStroke,
                onStrokeSelected = { stroke ->
                    onDrawingStateChanged(drawingState.copy(selectedStroke = stroke))
                },
                modifier = Modifier.fillMaxWidth()
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrokeSelectionDropdown(
    annotations: List<com.troubashare.domain.model.Annotation>,
    selectedStroke: com.troubashare.domain.model.AnnotationStroke?,
    onStrokeSelected: (com.troubashare.domain.model.AnnotationStroke?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val allStrokes = annotations.flatMap { it.strokes }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedStroke?.let { stroke ->
                "${stroke.tool.displayName} - ${stroke.strokeWidth.toInt()}px"
            } ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Select Stroke") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // "None" option
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onStrokeSelected(null)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            )

            HorizontalDivider()

            // Stroke items
            allStrokes.forEach { stroke ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Color square
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        try {
                                            Color(stroke.color.toUInt().toInt())
                                        } catch (e: Exception) {
                                            Color.Black
                                        },
                                        CircleShape
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )

                            Column {
                                Text(
                                    text = when (stroke.tool) {
                                        DrawingTool.TEXT -> stroke.text ?: "Text"
                                        else -> stroke.tool.displayName
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Size: ${stroke.strokeWidth.toInt()}px • ${(stroke.opacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onStrokeSelected(stroke)
                        expanded = false
                    }
                )
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