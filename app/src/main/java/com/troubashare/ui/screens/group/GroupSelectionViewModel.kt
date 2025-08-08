package com.troubashare.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.troubashare.data.repository.GroupRepository
import com.troubashare.domain.model.Group
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GroupSelectionViewModel(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupSelectionUiState())
    val uiState: StateFlow<GroupSelectionUiState> = _uiState.asStateFlow()
    
    private val _createGroupState = MutableStateFlow(CreateGroupUiState())
    val createGroupState: StateFlow<CreateGroupUiState> = _createGroupState.asStateFlow()

    val groups = groupRepository.getAllGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun showCreateGroupDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
        _createGroupState.value = CreateGroupUiState()
    }

    fun hideCreateGroupDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
        _createGroupState.value = CreateGroupUiState()
    }

    fun updateGroupName(name: String) {
        _createGroupState.value = _createGroupState.value.copy(
            groupName = name,
            errorMessage = null
        )
    }

    fun updateMemberName(index: Int, name: String) {
        val updatedMembers = _createGroupState.value.members.toMutableList()
        if (index < updatedMembers.size) {
            updatedMembers[index] = name
        }
        _createGroupState.value = _createGroupState.value.copy(
            members = updatedMembers,
            errorMessage = null
        )
    }

    fun addMemberField() {
        val updatedMembers = _createGroupState.value.members + ""
        _createGroupState.value = _createGroupState.value.copy(
            members = updatedMembers
        )
    }

    fun removeMemberField(index: Int) {
        if (_createGroupState.value.members.size > 1) {
            val updatedMembers = _createGroupState.value.members.toMutableList()
            updatedMembers.removeAt(index)
            _createGroupState.value = _createGroupState.value.copy(
                members = updatedMembers
            )
        }
    }

    fun createGroup() {
        val state = _createGroupState.value
        if (!state.isValid) {
            _createGroupState.value = state.copy(
                errorMessage = "Group name is required"
            )
            return
        }

        viewModelScope.launch {
            _createGroupState.value = state.copy(isCreating = true)

            try {
                val group = groupRepository.createGroup(
                    name = state.groupName,
                    memberNames = state.members.filter { it.isNotBlank() }
                )
                hideCreateGroupDialog()
                _uiState.value = _uiState.value.copy(
                    selectedGroupId = group.id
                )
            } catch (error: Exception) {
                _createGroupState.value = _createGroupState.value.copy(
                    isCreating = false,
                    errorMessage = error.message ?: "Failed to create group"
                )
            }
        }
    }

    fun selectGroup(groupId: String) {
        _uiState.value = _uiState.value.copy(selectedGroupId = groupId)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        _createGroupState.value = _createGroupState.value.copy(errorMessage = null)
    }
}

data class GroupSelectionUiState(
    val isLoading: Boolean = false,
    val groups: List<Group> = emptyList(),
    val showCreateDialog: Boolean = false,
    val errorMessage: String? = null,
    val selectedGroupId: String? = null
)

data class CreateGroupUiState(
    val groupName: String = "",
    val members: List<String> = listOf(""),
    val isCreating: Boolean = false,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() = groupName.isNotBlank()
}