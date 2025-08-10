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

    private val _editGroupState = MutableStateFlow(EditGroupUiState())
    val editGroupState: StateFlow<EditGroupUiState> = _editGroupState.asStateFlow()

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

    fun showEditGroupDialog() {
        currentGroup.value?.let { group ->
            _editGroupState.value = EditGroupUiState(
                groupId = group.id,
                groupName = group.name,
                members = group.members.map { it.name }
            )
            _uiState.value = _uiState.value.copy(showEditGroupDialog = true)
        }
    }

    fun hideEditGroupDialog() {
        _uiState.value = _uiState.value.copy(showEditGroupDialog = false)
        _editGroupState.value = EditGroupUiState()
    }

    fun updateEditGroupName(name: String) {
        _editGroupState.value = _editGroupState.value.copy(
            groupName = name,
            errorMessage = null
        )
    }

    fun updateEditMemberName(index: Int, name: String) {
        val updatedMembers = _editGroupState.value.members.toMutableList()
        if (index < updatedMembers.size) {
            updatedMembers[index] = name
        }
        _editGroupState.value = _editGroupState.value.copy(
            members = updatedMembers,
            errorMessage = null
        )
    }

    fun addEditMemberField() {
        val updatedMembers = _editGroupState.value.members + ""
        _editGroupState.value = _editGroupState.value.copy(
            members = updatedMembers
        )
    }

    fun removeEditMemberField(index: Int) {
        if (_editGroupState.value.members.size > 1) {
            val updatedMembers = _editGroupState.value.members.toMutableList()
            updatedMembers.removeAt(index)
            _editGroupState.value = _editGroupState.value.copy(
                members = updatedMembers
            )
        }
    }

    fun updateGroup() {
        val state = _editGroupState.value
        if (!state.isValid) {
            _editGroupState.value = state.copy(
                errorMessage = "Group name is required"
            )
            return
        }

        viewModelScope.launch {
            _editGroupState.value = state.copy(isUpdating = true)

            try {
                groupRepository.updateGroup(
                    groupId = state.groupId!!,
                    name = state.groupName,
                    memberNames = state.members.filter { it.isNotBlank() }
                )
                hideEditGroupDialog()
            } catch (error: Exception) {
                _editGroupState.value = _editGroupState.value.copy(
                    isUpdating = false,
                    errorMessage = error.message ?: "Failed to update group"
                )
            }
        }
    }
    
    fun showConcertModeDialog() {
        _uiState.value = _uiState.value.copy(showConcertModeDialog = true)
    }

    fun hideConcertModeDialog() {
        _uiState.value = _uiState.value.copy(showConcertModeDialog = false)
    }
}

data class HomeUiState(
    val showGroupSwitcher: Boolean = false,
    val showEditGroupDialog: Boolean = false,
    val showConcertModeDialog: Boolean = false
)

data class EditGroupUiState(
    val groupId: String? = null,
    val groupName: String = "",
    val members: List<String> = listOf(""),
    val isUpdating: Boolean = false,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() = groupName.isNotBlank() && groupId != null
}
