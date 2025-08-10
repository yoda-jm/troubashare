package com.troubashare.ui.screens.concert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.SetlistRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.data.repository.GroupRepository
import com.troubashare.domain.model.SongFile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConcertModeViewModel(
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ConcertModeUiState())
    val uiState: StateFlow<ConcertModeUiState> = _uiState.asStateFlow()
    
    fun loadConcertData(setlistId: String, memberId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Load setlist details
                val setlist = setlistRepository.getSetlistById(setlistId)
                if (setlist == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Setlist not found"
                    )
                    return@launch
                }
                
                // Load member details
                val member = groupRepository.getMemberById(memberId)
                val memberName = member?.name ?: "Unknown Member"
                
                // Load songs with files for this member
                val songsWithFiles = setlist.items.map { item ->
                    val song = item.song
                    val memberFiles = song.files.filter { it.memberId == memberId }
                    ConcertSongItem(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        files = memberFiles,
                        notes = item.notes
                    )
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    setlistName = setlist.name,
                    venue = setlist.venue ?: "",
                    memberName = memberName,
                    songs = songsWithFiles,
                    totalSongs = songsWithFiles.size,
                    currentSongIndex = if (songsWithFiles.isNotEmpty()) 0 else -1
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load concert data: ${e.message}"
                )
            }
        }
    }
    
    fun selectSong(index: Int) {
        val currentState = _uiState.value
        if (index >= 0 && index < currentState.songs.size) {
            _uiState.value = currentState.copy(currentSongIndex = index)
        }
    }
    
    fun goToSong(index: Int) {
        selectSong(index)
    }
    
    fun nextSong() {
        val currentState = _uiState.value
        if (currentState.currentSongIndex < currentState.songs.size - 1) {
            _uiState.value = currentState.copy(
                currentSongIndex = currentState.currentSongIndex + 1
            )
        }
    }
    
    fun previousSong() {
        val currentState = _uiState.value
        if (currentState.currentSongIndex > 0) {
            _uiState.value = currentState.copy(
                currentSongIndex = currentState.currentSongIndex - 1
            )
        }
    }
    
    fun togglePerformanceMode() {
        _uiState.value = _uiState.value.copy(
            isInPerformanceMode = !_uiState.value.isInPerformanceMode
        )
    }
    
    fun reorderSongs(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (fromIndex < 0 || toIndex < 0 || 
            fromIndex >= currentState.songs.size || 
            toIndex >= currentState.songs.size ||
            fromIndex == toIndex) {
            return
        }
        
        val mutableSongs = currentState.songs.toMutableList()
        val songToMove = mutableSongs.removeAt(fromIndex)
        mutableSongs.add(toIndex, songToMove)
        
        // Adjust current song index if needed
        val newCurrentIndex = when {
            currentState.currentSongIndex == fromIndex -> toIndex
            currentState.currentSongIndex > fromIndex && currentState.currentSongIndex <= toIndex -> 
                currentState.currentSongIndex - 1
            currentState.currentSongIndex >= toIndex && currentState.currentSongIndex < fromIndex -> 
                currentState.currentSongIndex + 1
            else -> currentState.currentSongIndex
        }
        
        _uiState.value = currentState.copy(
            songs = mutableSongs,
            currentSongIndex = newCurrentIndex
        )
    }
}

data class ConcertModeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val setlistName: String = "",
    val venue: String = "",
    val memberName: String = "",
    val songs: List<ConcertSongItem> = emptyList(),
    val currentSongIndex: Int = -1,
    val totalSongs: Int = 0,
    val isInPerformanceMode: Boolean = false
)

data class ConcertSongItem(
    val songId: String,
    val title: String,
    val artist: String?,
    val files: List<SongFile>,
    val notes: String?
)