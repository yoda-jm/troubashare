package com.troubashare.ui.cloud

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.cloud.CloudSyncManager
import com.troubashare.data.repository.GroupRepository
import com.troubashare.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudSyncUiState(
    val isConnected: Boolean = false,
    val accountInfo: String? = null,
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val syncProgress: String? = null,
    val syncResult: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    application: Application,
    private val cloudSyncManager: CloudSyncManager,
    private val groupRepository: GroupRepository
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(CloudSyncUiState())
    val uiState: StateFlow<CloudSyncUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            combine(
                cloudSyncManager.isConnected,
                groupRepository.getAllGroups()
            ) { isConnected: Boolean, groups: List<Group> ->
                CloudSyncUiState(
                    isConnected = isConnected,
                    groups = groups,
                    accountInfo = if (isConnected) "Connected" else null
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
    
    fun connectToGoogleDrive() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                // Check if already authenticated
                val result = cloudSyncManager.checkConnection()
                if (result) {
                    _uiState.value = _uiState.value.copy(
                        isConnected = true,
                        isLoading = false,
                        accountInfo = "Already connected to Google Drive"
                    )
                } else {
                    // Launch the authentication activity
                    val context = getApplication<Application>()
                    val intent = Intent(context, CloudAuthActivity::class.java).apply {
                        putExtra(CloudAuthActivity.EXTRA_PROVIDER, "google_drive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    
                    // Update UI to show that auth activity was launched
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Authentication window opened. Please complete sign-in and return to this screen."
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to start authentication: ${e.message}"
                )
            }
        }
    }
    
    fun refreshConnectionStatus() {
        viewModelScope.launch {
            try {
                // Clear any previous error messages
                _uiState.value = _uiState.value.copy(errorMessage = null)
                
                val result = cloudSyncManager.checkConnection()
                _uiState.value = _uiState.value.copy(
                    isConnected = result,
                    accountInfo = if (result) "Connected to Google Drive" else null,
                    errorMessage = if (!result) "Not connected - please try connecting again" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    accountInfo = null,
                    errorMessage = "Connection check failed: ${e.message}"
                )
            }
        }
    }
    
    fun disconnectFromCloud() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                cloudSyncManager.disconnect()
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    accountInfo = null,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to disconnect: ${e.message}"
                )
            }
        }
    }
    
    fun syncGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true, 
                errorMessage = null, 
                syncProgress = "Starting sync...",
                syncResult = null
            )
            
            try {
                // Set up progress callback
                cloudSyncManager.setProgressCallback { progress ->
                    _uiState.value = _uiState.value.copy(syncProgress = progress)
                }
                
                val result = cloudSyncManager.syncGroup(groupId)
                if (result.isSuccess) {
                    // Show success message
                    _uiState.value = _uiState.value.copy(
                        syncResult = "All data synced to cloud successfully"
                    )
                    
                    // Clear the success message after 4 seconds (give more time to read)
                    kotlinx.coroutines.delay(4000)
                    _uiState.value = _uiState.value.copy(
                        syncProgress = null,
                        syncResult = null
                    )
                    
                    // Refresh data after successful sync
                    loadData()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Sync failed: ${result.exceptionOrNull()?.message}",
                        syncProgress = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Sync failed: ${e.message}",
                    syncProgress = null
                )
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
                // Clear the progress callback
                cloudSyncManager.setProgressCallback { }
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}