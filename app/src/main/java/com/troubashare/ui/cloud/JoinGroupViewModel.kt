package com.troubashare.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.cloud.CloudSyncManager
import com.troubashare.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinGroupUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val joinedGroup: Group? = null
)

@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(JoinGroupUiState())
    val uiState: StateFlow<JoinGroupUiState> = _uiState.asStateFlow()
    
    fun joinGroup(shareCode: String) {
        if (shareCode.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please enter a share code"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            
            try {
                val result = cloudSyncManager.joinGroup(shareCode.trim())
                if (result.isSuccess) {
                    val group = result.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        joinedGroup = group
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to join group"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }
    
    fun scanQRCode() {
        // TODO: Implement QR code scanning
        _uiState.value = _uiState.value.copy(
            errorMessage = "QR code scanning will be available in a future update"
        )
    }
    
    fun joinFromLink() {
        // TODO: Implement deep link joining
        _uiState.value = _uiState.value.copy(
            errorMessage = "Link joining will be available in a future update"
        )
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}