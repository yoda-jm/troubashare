package com.troubashare.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troubashare.R
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.repository.GroupRepository
import com.troubashare.domain.model.Group
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    groupId: String,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSetlists: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSwitchGroup: (String) -> Unit = {},
    onCreateNewGroup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { TroubaShareDatabase.getInstance(context) }
    val repository = remember { GroupRepository(database) }
    val viewModel: HomeViewModel = viewModel { HomeViewModel(repository, groupId) }
    
    val uiState by viewModel.uiState.collectAsState()
    val currentGroup by viewModel.currentGroup.collectAsState()
    val allGroups by viewModel.allGroups.collectAsState()
    val editGroupState by viewModel.editGroupState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("TroubaShare")
                        currentGroup?.let { group ->
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showEditGroupDialog() }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit group"
                        )
                    }
                    
                    IconButton(onClick = { viewModel.showGroupSwitcher() }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Switch group"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = currentGroup?.let { "Welcome back to ${it.name}!" } ?: "Welcome to TroubaShare",
                style = MaterialTheme.typography.headlineMedium
            )
            
            currentGroup?.let { group ->
                if (group.members.isNotEmpty()) {
                    Text(
                        text = "Band members: ${group.members.joinToString(", ") { it.name }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Text(
                text = "Manage your band's music, setlists, and performances.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCard(
                    title = stringResource(R.string.nav_library),
                    description = "Manage your songs and sheet music",
                    icon = Icons.Default.Home,
                    onClick = onNavigateToLibrary,
                    modifier = Modifier.weight(1f)
                )
                
                QuickActionCard(
                    title = stringResource(R.string.nav_setlists),
                    description = "Create and organize setlists",
                    icon = Icons.Default.Star,
                    onClick = onNavigateToSetlists,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCard(
                    title = "Concert Mode",
                    description = "Performance-ready display",
                    icon = Icons.Default.Add,
                    onClick = { /* TODO: Navigate to Concert Mode */ },
                    modifier = Modifier.weight(1f)
                )
                
                QuickActionCard(
                    title = stringResource(R.string.nav_settings),
                    description = "App preferences and sync",
                    icon = Icons.Default.Settings,
                    onClick = onNavigateToSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    
    // Group Switcher Dialog
    if (uiState.showGroupSwitcher) {
        GroupSwitcherDialog(
            currentGroupId = groupId,
            groups = allGroups,
            onGroupSelected = { selectedGroupId ->
                viewModel.hideGroupSwitcher()
                if (selectedGroupId != groupId) {
                    onSwitchGroup(selectedGroupId)
                }
            },
            onCreateNewGroup = {
                viewModel.hideGroupSwitcher()
                onCreateNewGroup()
            },
            onDismiss = { viewModel.hideGroupSwitcher() }
        )
    }
    
    // Edit Group Dialog
    if (uiState.showEditGroupDialog) {
        EditGroupDialog(
            state = editGroupState,
            onGroupNameChange = viewModel::updateEditGroupName,
            onMemberNameChange = viewModel::updateEditMemberName,
            onAddMember = viewModel::addEditMemberField,
            onRemoveMember = viewModel::removeEditMemberField,
            onUpdate = viewModel::updateGroup,
            onDismiss = viewModel::hideEditGroupDialog
        )
    }
}

@Composable
fun GroupSwitcherDialog(
    currentGroupId: String,
    groups: List<Group>,
    onGroupSelected: (String) -> Unit,
    onCreateNewGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch Group") },
        text = {
            LazyColumn {
                // Add "Create New Group" option at the top
                item {
                    Card(
                        onClick = onCreateNewGroup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create new group",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Create New Group",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Existing Groups:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                items(groups, key = { it.id }) { group ->
                    Card(
                        onClick = { 
                            if (group.id != currentGroupId) {
                                onGroupSelected(group.id)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (group.id == currentGroupId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (group.id == currentGroupId) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                
                                if (group.id == currentGroupId) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Current group",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            if (group.members.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${group.members.size} members: ${group.members.take(3).joinToString(", ") { it.name }}${if (group.members.size > 3) "..." else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (group.id == currentGroupId) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EditGroupDialog(
    state: EditGroupUiState,
    onGroupNameChange: (String) -> Unit,
    onMemberNameChange: (Int, String) -> Unit,
    onAddMember: () -> Unit,
    onRemoveMember: (Int) -> Unit,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_group)) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.groupName,
                    onValueChange = onGroupNameChange,
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = state.errorMessage != null
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Members",
                    style = MaterialTheme.typography.labelLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                state.members.forEachIndexed { index, member ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = member,
                            onValueChange = { onMemberNameChange(index, it) },
                            label = { Text("Member ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (state.members.size > 1) {
                            IconButton(
                                onClick = { onRemoveMember(index) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove member"
                                )
                            }
                        }
                    }
                    
                    if (index < state.members.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onAddMember,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Member")
                }
                
                state.errorMessage?.let { errorMessage ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = state.isValid && !state.isUpdating
            ) {
                if (state.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}