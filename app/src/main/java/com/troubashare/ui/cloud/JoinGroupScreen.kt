package com.troubashare.ui.cloud

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.troubashare.domain.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(
    onNavigateBack: () -> Unit,
    onGroupJoined: (Group) -> Unit,
    viewModel: JoinGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var shareCode by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Group") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Header Icon and Text
            Icon(
                imageVector = Icons.Default.GroupAdd,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Join a Shared Group",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Enter the share code you received from your band leader to join their group and access shared songs and setlists.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Share Code Input
            OutlinedTextField(
                value = shareCode,
                onValueChange = { shareCode = it.uppercase().replace(" ", "") },
                label = { Text("Share Code") },
                placeholder = { Text("TB-XXXXXXXX") },
                leadingIcon = {
                    Icon(Icons.Default.Key, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                isError = uiState.errorMessage != null
            )
            
            uiState.errorMessage?.let { errorMessage ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Join Button
            Button(
                onClick = { viewModel.joinGroup(shareCode) },
                enabled = shareCode.isNotBlank() && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Joining Group...")
                } else {
                    Icon(Icons.Default.Group, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join Group")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Alternative Methods
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Other Ways to Join",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // QR Code Scanner (Future feature)
                OutlinedButton(
                    onClick = { viewModel.scanQRCode() },
                    enabled = false // TODO: Enable when QR scanning is implemented
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Scan QR")
                    }
                }
                
                // Join from Link (Future feature)
                OutlinedButton(
                    onClick = { viewModel.joinFromLink() },
                    enabled = false // TODO: Enable when deep linking is implemented
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("From Link")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Help Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Help,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Need Help?",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "• Share codes look like: TB-JAZZ2024\n" +
                              "• Ask your band leader for the group share code\n" +
                              "• Make sure you have an internet connection\n" +
                              "• You'll need to sign in to Google Drive when prompted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Handle successful group join
            LaunchedEffect(uiState.joinedGroup) {
                uiState.joinedGroup?.let { group ->
                    onGroupJoined(group)
                }
            }
        }
    }
}