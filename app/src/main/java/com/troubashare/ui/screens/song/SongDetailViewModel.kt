package com.troubashare.ui.screens.song

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.AnnotationRepository
import com.troubashare.data.repository.FileSelectionRepository
import com.troubashare.data.repository.GroupRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.domain.model.Annotation
import com.troubashare.domain.model.FileSelection
import com.troubashare.domain.model.Group
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.SongFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongDetailViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val groupRepository: GroupRepository,
    private val annotationRepository: AnnotationRepository,
    private val fileSelectionRepository: FileSelectionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val songId: String = savedStateHandle["songId"] ?: ""
    private val groupId: String = savedStateHandle["groupId"] ?: ""

    private val _uiState = MutableStateFlow(SongDetailUiState())
    val uiState: StateFlow<SongDetailUiState> = _uiState.asStateFlow()

    val song: StateFlow<Song?> = songRepository.getSongByIdFlow(songId)
        .stateIn(
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

    /** All FileSelections for this song, updated reactively. */
    val fileSelections: StateFlow<List<FileSelection>> =
        fileSelectionRepository.getSelectionsBySongId(songId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun getAnnotationsForFile(fileId: String, memberId: String): Flow<List<Annotation>> =
        annotationRepository.getAnnotationsByFileAndMember(fileId, memberId)

    /** Returns the selected (ordered) files for a member from the pool. */
    fun getFilesForMember(songFiles: List<SongFile>, memberId: String, partIds: List<String>, selections: List<FileSelection> = fileSelections.value): List<SongFile> {
        val fileMap = songFiles.associateBy { it.id }

        val partSels = selections
            .filter { it.selectionType == com.troubashare.domain.model.SelectionType.PART && it.partId in partIds }
            .sortedBy { it.displayOrder }

        val memberSels = selections
            .filter { it.selectionType == com.troubashare.domain.model.SelectionType.MEMBER && it.memberId == memberId }
            .sortedBy { it.displayOrder }

        val seen = mutableSetOf<String>()
        return (partSels + memberSels).mapNotNull { sel ->
            if (seen.add(sel.songFileId)) fileMap[sel.songFileId] else null
        }
    }

    /**
     * Uploads a file to the song pool and auto-selects it for ALL members.
     * [uploadedBy] is used as the uploader (audit trail only).
     */
    fun uploadFile(uploadedBy: String, fileName: String, uri: Uri, context: Context) {
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

                // Add to pool without any auto-selection first
                val result = songRepository.addFileToSong(
                    songId = songId,
                    uploadedBy = uploadedBy,
                    fileName = fileName,
                    inputStream = inputStream,
                    autoSelectForMember = null  // we'll select for all members below
                )

                inputStream.close()

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(isUploading = false)
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
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete file"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun moveFile(memberId: String, fileId: String, newPosition: Int) {
        viewModelScope.launch {
            val result = songRepository.moveFile(songId, memberId, fileId, newPosition)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to reorder files"
                )
            }
        }
    }

    fun assignFileToMember(songFileId: String, memberId: String) {
        toggleFileSelection(songFileId, memberId, true)
    }

    fun removeFileAssignment(songFileId: String, memberId: String) {
        toggleFileSelection(songFileId, memberId, false)
    }

    fun toggleFileSelection(songFileId: String, memberId: String, selected: Boolean) {
        viewModelScope.launch {
            if (selected) {
                val currentSelections = fileSelections.value
                    .filter { it.memberId == memberId }
                val nextOrder = if (currentSelections.isEmpty()) 0 else currentSelections.maxOf { it.displayOrder } + 1
                fileSelectionRepository.addMemberSelection(songFileId, memberId, nextOrder)
            } else {
                fileSelectionRepository.removeMemberSelection(songFileId, memberId)
            }
        }
    }
}

data class SongDetailUiState(
    val isUploading: Boolean = false,
    val errorMessage: String? = null
)
