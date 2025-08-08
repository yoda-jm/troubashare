package com.troubashare.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troubashare.R
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.repository.GroupRepository
import com.troubashare.domain.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleGroupSelectionScreen(
    onGroupSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val database = remember { TroubaShareDatabase.getInstance(context) }
    val repository = remember { GroupRepository(database) }
    val viewModel: GroupSelectionViewModel = viewModel { GroupSelectionViewModel(repository) }
    
    val uiState by viewModel.uiState.collectAsState()
    val createGroupState by viewModel.createGroupState.collectAsState()
    val groups by viewModel.groups.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_selection_title)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateGroupDialog() }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.create_group)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.no_groups),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.showCreateGroupDialog() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.create_group))
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups) { group ->
                        GroupCard(
                            group = group,
                            onClick = { onGroupSelected(group.id) }
                        )
                    }
                }
            }
        }
    }

    // Create Group Dialog
    if (uiState.showCreateDialog) {
        CreateGroupDialog(
            state = createGroupState,
            onGroupNameChange = viewModel::updateGroupName,
            onMemberNameChange = viewModel::updateMemberName,
            onAddMember = viewModel::addMemberField,
            onRemoveMember = viewModel::removeMemberField,
            onCreate = viewModel::createGroup,
            onDismiss = viewModel::hideCreateGroupDialog
        )
    }
    
    // Handle group selection after creation
    LaunchedEffect(uiState.selectedGroupId) {
        uiState.selectedGroupId?.let { groupId ->
            onGroupSelected(groupId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCard(
    group: Group,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium
            )
            
            if (group.members.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${group.members.size} members",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CreateGroupDialog(
    state: CreateGroupUiState,
    onGroupNameChange: (String) -> Unit,
    onMemberNameChange: (Int, String) -> Unit,
    onAddMember: () -> Unit,
    onRemoveMember: (Int) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_group)) },
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
                                    imageVector = Icons.Default.Add, // Will use X icon when available
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
                onClick = onCreate,
                enabled = state.isValid && !state.isCreating
            ) {
                if (state.isCreating) {
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