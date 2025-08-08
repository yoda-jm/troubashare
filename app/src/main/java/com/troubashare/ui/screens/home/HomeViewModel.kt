package com.troubashare.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.GroupRepository
import com.troubashare.domain.model.Group
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val groupRepository: GroupRepository,
    private val groupId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val currentGroup: StateFlow<Group?> = flow {
        emit(groupRepository.getGroupById(groupId))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val allGroups = groupRepository.getAllGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun showGroupSwitcher() {
        _uiState.value = _uiState.value.copy(showGroupSwitcher = true)
    }

    fun hideGroupSwitcher() {
        _uiState.value = _uiState.value.copy(showGroupSwitcher = false)
    }
}

data class HomeUiState(
    val showGroupSwitcher: Boolean = false
)