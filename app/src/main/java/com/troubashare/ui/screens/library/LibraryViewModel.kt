package com.troubashare.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.SongRepository
import com.troubashare.domain.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val songRepository: SongRepository,
    private val groupId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _createSongState = MutableStateFlow(CreateSongUiState())
    val createSongState: StateFlow<CreateSongUiState> = _createSongState.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val songs = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                songRepository.getSongsByGroupId(groupId)
            } else {
                songRepository.searchSongs(groupId, query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun showCreateSongDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
        _createSongState.value = CreateSongUiState()
    }

    fun hideCreateSongDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
        _createSongState.value = CreateSongUiState()
    }

    fun updateSongTitle(title: String) {
        _createSongState.value = _createSongState.value.copy(
            title = title,
            errorMessage = null
        )
    }

    fun updateSongArtist(artist: String) {
        _createSongState.value = _createSongState.value.copy(artist = artist)
    }

    fun updateSongKey(key: String) {
        _createSongState.value = _createSongState.value.copy(key = key)
    }

    fun updateSongTempo(tempoStr: String) {
        val tempo = tempoStr.toIntOrNull()
        _createSongState.value = _createSongState.value.copy(
            tempoInput = tempoStr,
            tempo = tempo
        )
    }

    fun updateSongNotes(notes: String) {
        _createSongState.value = _createSongState.value.copy(notes = notes)
    }

    fun addTag(tag: String) {
        val trimmedTag = tag.trim()
        if (trimmedTag.isNotEmpty() && !_createSongState.value.tags.contains(trimmedTag)) {
            val updatedTags = _createSongState.value.tags + trimmedTag
            _createSongState.value = _createSongState.value.copy(
                tags = updatedTags,
                tagInput = ""
            )
        }
    }

    fun removeTag(tag: String) {
        val updatedTags = _createSongState.value.tags - tag
        _createSongState.value = _createSongState.value.copy(tags = updatedTags)
    }

    fun updateTagInput(input: String) {
        _createSongState.value = _createSongState.value.copy(tagInput = input)
    }

    fun createSong() {
        val state = _createSongState.value
        if (!state.isValid) {
            _createSongState.value = state.copy(
                errorMessage = "Song title is required"
            )
            return
        }

        viewModelScope.launch {
            _createSongState.value = state.copy(isCreating = true)

            try {
                songRepository.createSong(
                    groupId = groupId,
                    title = state.title,
                    artist = state.artist.takeIf { it.isNotBlank() },
                    key = state.key.takeIf { it.isNotBlank() },
                    tempo = state.tempo,
                    tags = state.tags,
                    notes = state.notes.takeIf { it.isNotBlank() }
                )
                hideCreateSongDialog()
            } catch (error: Exception) {
                _createSongState.value = _createSongState.value.copy(
                    isCreating = false,
                    errorMessage = error.message ?: "Failed to create song"
                )
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            try {
                songRepository.deleteSong(song)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Failed to delete song"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        _createSongState.value = _createSongState.value.copy(errorMessage = null)
    }
}

data class LibraryUiState(
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val errorMessage: String? = null
)

data class CreateSongUiState(
    val title: String = "",
    val artist: String = "",
    val key: String = "",
    val tempoInput: String = "",
    val tempo: Int? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val isCreating: Boolean = false,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() = title.isNotBlank()
}