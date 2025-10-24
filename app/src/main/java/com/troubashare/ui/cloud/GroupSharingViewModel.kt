package com.troubashare.ui.cloud

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.cloud.CloudSyncManager
import com.troubashare.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class GroupSharingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudSyncManager: CloudSyncManager,
    private val groupRepository: com.troubashare.data.repository.GroupRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(GroupSharingUiState())
    val uiState: StateFlow<GroupSharingUiState> = _uiState.asStateFlow()
    
    private var currentGroupId: String = ""
    private var lastOperation: (() -> Unit)? = null
    
    init {
        // Observe sync status from CloudSyncManager
        viewModelScope.launch {
            cloudSyncManager.syncStatus.collect { status ->
                _uiState.value = _uiState.value.copy(
                    syncStatus = status,
                    isLoading = status == SyncStatus.SYNCING
                )
            }
        }
    }
    
    fun initialize(groupId: String) {
        loadGroupSharingInfo(groupId)
    }
    
    fun loadGroupSharingInfo(groupId: String) {
        currentGroupId = groupId
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Load the group
                val group = groupRepository.getGroupById(groupId)
                
                // Load existing sharing info if available
                val existingShareCode = getExistingShareCode(groupId)
                val lastSyncTime = getLastSyncTime(groupId)
                
                _uiState.value = _uiState.value.copy(
                    group = group,
                    shareCode = existingShareCode,
                    lastSyncTime = lastSyncTime,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    fun enableCloudSharing(groupId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                lastOperation = { enableCloudSharing(groupId) }
                
                val result = cloudSyncManager.enableCloudSync(groupId)
                
                result.fold(
                    onSuccess = { shareCode ->
                        _uiState.value = _uiState.value.copy(
                            shareCode = shareCode,
                            isLoading = false,
                            message = "Cloud sharing enabled successfully!"
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = exception.message
                        )
                    }
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    fun authenticate() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Launch CloudAuthActivity
                val intent = Intent(context, CloudAuthActivity::class.java).apply {
                    putExtra(CloudAuthActivity.EXTRA_PROVIDER, "google_drive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                
                // Note: The actual authentication result will be handled by the activity
                _uiState.value = _uiState.value.copy(isLoading = false)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    fun performManualSync(groupId: String) {
        viewModelScope.launch {
            try {
                // Trigger manual sync
                cloudSyncManager.stopContinuousSync()
                cloudSyncManager.startContinuousSync(groupId)
                
                _uiState.value = _uiState.value.copy(
                    message = "Manual sync started"
                )
                
                // Update last sync time
                val currentTime = getCurrentTimeString()
                _uiState.value = _uiState.value.copy(
                    lastSyncTime = currentTime
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message
                )
            }
        }
    }
    
    fun shareViaEmail(shareCode: ShareCode, group: Group) {
        viewModelScope.launch {
            try {
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Join TroubaShare Group: ${group.name}")
                    putExtra(Intent.EXTRA_TEXT, buildEmailBody(shareCode, group))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                val chooserIntent = Intent.createChooser(emailIntent, "Share group via...")
                chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(chooserIntent)
                
                _uiState.value = _uiState.value.copy(
                    message = "Sharing via email..."
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to share via email: ${e.message}"
                )
            }
        }
    }
    
    fun generateQRCode(shareCode: ShareCode) {
        viewModelScope.launch {
            try {
                // TODO: Implement QR code generation
                _uiState.value = _uiState.value.copy(
                    message = "QR code generation coming soon!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to generate QR code: ${e.message}"
                )
            }
        }
    }
    
    fun showConflictResolution(groupId: String) {
        // TODO: Navigate to conflict resolution screen
        _uiState.value = _uiState.value.copy(
            message = "Conflict resolution coming soon!"
        )
    }
    
    fun retryLastOperation() {
        lastOperation?.invoke()
    }
    
    fun showMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
    
    private suspend fun getExistingShareCode(groupId: String): ShareCode? {
        // TODO: Implement loading existing share code from preferences/database
        return null
    }
    
    private suspend fun getLastSyncTime(groupId: String): String? {
        // TODO: Implement loading last sync time from preferences/database
        return null
    }
    
    private fun getCurrentTimeString(): String {
        val formatter = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return formatter.format(Date())
    }
    
    private fun buildEmailBody(shareCode: ShareCode, group: Group): String {
        return """
            🎵 You're invited to join our TroubaShare group!
            
            Group: ${group.name}
            Members: ${group.members.size}
            
            To join:
            1. Install TroubaShare from the Play Store
            2. Tap "Join Group" on the main screen
            3. Enter this code: ${shareCode.code}
            
            Or use this link: ${shareCode.deepLink}
            
            See you at the next rehearsal! 🎶
        """.trimIndent()
    }
}

data class GroupSharingUiState(
    val group: Group? = null,
    val syncStatus: SyncStatus = SyncStatus.OFFLINE,
    val shareCode: ShareCode? = null,
    val lastSyncTime: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null
)