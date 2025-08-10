package com.troubashare.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.troubashare.domain.model.DrawingState
import com.troubashare.domain.model.DrawingTool

@Composable
fun ExpandableToolsFab(
    drawingState: DrawingState,
    onDrawingStateChanged: (DrawingState) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    // Animation for rotation
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = tween(300),
        label = "fab_rotation"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Individual tool FABs (shown when expanded)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(animationSpec = tween(200))
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Include all tools in the FAB
                val allTools = DrawingTool.values()
                
                allTools.reversed().forEach { tool ->
                    ToolFab(
                        tool = tool,
                        isSelected = drawingState.tool == tool,
                        onClick = {
                            onDrawingStateChanged(drawingState.copy(tool = tool))
                            isExpanded = false // Collapse after selection
                        }
                    )
                }
            }
        }
        
        // Stack indicators (show when collapsed to indicate more options)
        Box {
            // Background shadow FABs to indicate stack
            if (!isExpanded) {
                FloatingActionButton(
                    onClick = { },
                    modifier = Modifier.offset(x = (-4).dp, y = (-4).dp),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    contentColor = Color.Transparent
                ) { }
                
                FloatingActionButton(
                    onClick = { },
                    modifier = Modifier.offset(x = (-2).dp, y = (-2).dp),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    contentColor = Color.Transparent
                ) { }
            }
            
            // Main FAB
            FloatingActionButton(
                onClick = { isExpanded = !isExpanded },
                containerColor = if (isExpanded) 
                    MaterialTheme.colorScheme.secondary 
                else 
                    MaterialTheme.colorScheme.primary,
                contentColor = if (isExpanded)
                    MaterialTheme.colorScheme.onSecondary
                else
                    MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else getToolIcon(drawingState.tool),
                    contentDescription = if (isExpanded) "Close tools" else "Select tool",
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }
}

@Composable
private fun ToolFab(
    tool: DrawingTool,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = if (isSelected) 
            MaterialTheme.colorScheme.tertiary 
        else 
            MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected)
            MaterialTheme.colorScheme.onTertiary
        else
            MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Icon(
            imageVector = getToolIcon(tool),
            contentDescription = tool.displayName,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun getToolIcon(tool: DrawingTool): ImageVector {
    return when (tool) {
        DrawingTool.PEN -> Icons.Default.Edit
        DrawingTool.HIGHLIGHTER -> Icons.Default.FormatColorFill
        DrawingTool.ERASER -> Icons.AutoMirrored.Filled.Backspace
        DrawingTool.TEXT -> Icons.Default.TextFields
        DrawingTool.SELECT -> Icons.Default.TouchApp
        DrawingTool.PAN_ZOOM -> Icons.Default.OpenWith
    }
}