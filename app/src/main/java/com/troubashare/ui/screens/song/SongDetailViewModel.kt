package com.troubashare.ui.screens.song

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.GroupRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.domain.model.Group
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.SongFile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SongDetailViewModel(
    private val songRepository: SongRepository,
    private val groupRepository: GroupRepository,
    private val songId: String,
    private val groupId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongDetailUiState())
    val uiState: StateFlow<SongDetailUiState> = _uiState.asStateFlow()

    val song: StateFlow<Song?> = flow {
        emit(songRepository.getSongById(songId))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val currentGroup: StateFlow<Group?> = flow {
        emit(groupRepository.getGroupById(groupId))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun uploadFile(memberId: String, fileName: String, uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
            
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        errorMessage = "Could not open file"
                    )
                    return@launch
                }
                
                val result = songRepository.addFileToSong(
                    songId = songId,
                    memberId = memberId,
                    fileName = fileName,
                    inputStream = inputStream
                )
                
                inputStream.close()
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(isUploading = false)
                    // Refresh song data to show new file
                    refreshSong()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to upload file"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    errorMessage = e.message ?: "Failed to upload file"
                )
            }
        }
    }

    fun deleteFile(file: SongFile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(errorMessage = null)
            
            val result = songRepository.removeFileFromSong(file)
            if (result.isSuccess) {
                // Refresh song data to remove deleted file
                refreshSong()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete file"
                )
            }
        }
    }

    private fun refreshSong() {
        viewModelScope.launch {
            // Trigger refresh by getting song data again
            val refreshedSong = songRepository.getSongById(songId)
            // The StateFlow will automatically update through the flow collection
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class SongDetailUiState(
    val isUploading: Boolean = false,
    val errorMessage: String? = null
)