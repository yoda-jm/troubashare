package com.troubashare.ui.screens.setlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.SetlistRepository
import com.troubashare.data.repository.SongRepository
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.Setlist
import com.troubashare.domain.model.SetlistItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class SetlistEditorViewModel(
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository,
    private val setlistId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetlistEditorUiState())
    val uiState: StateFlow<SetlistEditorUiState> = _uiState.asStateFlow()

    private val _setlist = MutableStateFlow<Setlist?>(null)
    val setlist: StateFlow<Setlist?> = _setlist.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Available songs (not already in setlist)
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val availableSongs = combine(
        searchQuery.debounce(300),
        _setlist
    ) { query, currentSetlist ->
        Pair(query, currentSetlist)
    }.flatMapLatest { (query, currentSetlist) ->
        if (currentSetlist == null) {
            flowOf(emptyList())
        } else {
            val songsInSetlist = currentSetlist.items.map { it.song.id }.toSet()
            if (query.isBlank()) {
                songRepository.getSongsByGroupId(currentSetlist.groupId)
            } else {
                songRepository.searchSongs(currentSetlist.groupId, query)
            }.map { songs ->
                songs.filter { it.id !in songsInSetlist }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadSetlist()
    }

    private fun loadSetlist() {
        viewModelScope.launch {
            val setlist = setlistRepository.getSetlistById(setlistId)
            _setlist.value = setlist
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addSongToSetlist(songId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAddingSong = true,
                addingSongId = songId,
                errorMessage = null
            )

            try {
                setlistRepository.addSongToSetlist(setlistId, songId)
                loadSetlist() // Refresh the setlist
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    isAddingSong = false,
                    addingSongId = null
                )
            }
        }
    }

    fun removeSongFromSetlist(songId: String) {
        val currentSetlist = _setlist.value ?: return
        val item = currentSetlist.items.find { it.song.id == songId } ?: return
        
        viewModelScope.launch {
            try {
                setlistRepository.removeSongFromSetlist(item.id)
                loadSetlist() // Refresh the setlist
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun moveSong(songId: String, newPosition: Int) {
        val currentSetlist = _setlist.value ?: return
        
        viewModelScope.launch {
            try {
                val items = currentSetlist.items.sortedBy { it.position }
                val currentIndex = items.indexOfFirst { it.song.id == songId }
                
                if (currentIndex != -1 && newPosition >= 0 && newPosition < items.size) {
                    // Create new order with the item moved
                    val reorderedItems = items.toMutableList()
                    val item = reorderedItems.removeAt(currentIndex)
                    reorderedItems.add(newPosition, item)
                    
                    // Create new order list with item IDs
                    val newOrder = reorderedItems.map { it.id }
                    setlistRepository.reorderSetlistItems(setlistId, newOrder)
                    loadSetlist() // Refresh the setlist
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun saveSetlist() {
        val currentSetlist = _setlist.value ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            
            try {
                setlistRepository.updateSetlist(currentSetlist)
                _uiState.value = _uiState.value.copy(isSaving = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message
                )
            }
        }
    }
}

data class SetlistEditorUiState(
    val isAddingSong: Boolean = false,
    val addingSongId: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)