package com.troubashare.ui.screens.setlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.GroupRepository
import com.troubashare.data.repository.SetlistRepository
import com.troubashare.domain.model.Setlist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SetlistsViewModel(
    private val setlistRepository: SetlistRepository,
    private val groupRepository: GroupRepository,
    private val groupId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetlistsUiState())
    val uiState: StateFlow<SetlistsUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _createSetlistState = MutableStateFlow(CreateSetlistUiState())
    val createSetlistState: StateFlow<CreateSetlistUiState> = _createSetlistState.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val setlists = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                setlistRepository.getSetlistsByGroupId(groupId)
            } else {
                setlistRepository.searchSetlists(groupId, query)
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

    fun showCreateSetlistDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
        _createSetlistState.value = CreateSetlistUiState()
    }

    fun hideCreateSetlistDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
        _createSetlistState.value = CreateSetlistUiState()
    }

    fun updateSetlistName(name: String) {
        _createSetlistState.value = _createSetlistState.value.copy(
            name = name,
            errorMessage = null
        )
    }

    fun updateSetlistDescription(description: String) {
        _createSetlistState.value = _createSetlistState.value.copy(description = description)
    }

    fun updateSetlistVenue(venue: String) {
        _createSetlistState.value = _createSetlistState.value.copy(venue = venue)
    }

    fun createSetlist() {
        val state = _createSetlistState.value
        if (!state.isValid) {
            _createSetlistState.value = state.copy(
                errorMessage = "Setlist name is required"
            )
            return
        }

        viewModelScope.launch {
            _createSetlistState.value = state.copy(isCreating = true)

            try {
                setlistRepository.createSetlist(
                    groupId = groupId,
                    name = state.name,
                    description = state.description.takeIf { it.isNotBlank() },
                    venue = state.venue.takeIf { it.isNotBlank() }
                )
                hideCreateSetlistDialog()
            } catch (error: Exception) {
                _createSetlistState.value = _createSetlistState.value.copy(
                    isCreating = false,
                    errorMessage = error.message ?: "Failed to create setlist"
                )
            }
        }
    }

    fun deleteSetlist(setlist: Setlist) {
        viewModelScope.launch {
            try {
                setlistRepository.deleteSetlist(setlist)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Failed to delete setlist"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        _createSetlistState.value = _createSetlistState.value.copy(errorMessage = null)
    }
}

data class SetlistsUiState(
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val errorMessage: String? = null
)

data class CreateSetlistUiState(
    val name: String = "",
    val description: String = "",
    val venue: String = "",
    val isCreating: Boolean = false,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank()
}