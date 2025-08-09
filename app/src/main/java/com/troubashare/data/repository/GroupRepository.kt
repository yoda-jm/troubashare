package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.GroupEntity
import com.troubashare.data.database.entities.MemberEntity
import com.troubashare.domain.model.Group
import com.troubashare.domain.model.Member
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

class GroupRepository(private val database: TroubaShareDatabase) {
    
    private val groupDao = database.groupDao()
    
    fun getAllGroups(): Flow<List<Group>> {
        return groupDao.getAllGroups().map { entities ->
            entities.map { entity ->
                val members = groupDao.getMembersByGroupId(entity.id)
                entity.toDomainModel(members.map { it.toDomainModel() })
            }
        }
    }
    
    suspend fun getGroupById(id: String): Group? {
        val entity = groupDao.getGroupById(id) ?: return null
        val members = groupDao.getMembersByGroupId(id)
        return entity.toDomainModel(members.map { it.toDomainModel() })
    }
    
    suspend fun createGroup(name: String, memberNames: List<String>): Group {
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val group = GroupEntity(
            id = groupId,
            name = name,
            createdAt = now,
            updatedAt = now
        )
        
        val members = memberNames.filter { it.isNotBlank() }.map { memberName ->
            MemberEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                name = memberName.trim(),
                role = null
            )
        }
        
        groupDao.insertGroup(group)
        members.forEach { groupDao.insertMember(it) }
        
        return group.toDomainModel(members.map { it.toDomainModel() })
    }
    
    suspend fun updateGroup(groupId: String, name: String, memberNames: List<String>): Group {
        val existingGroup = groupDao.getGroupById(groupId) 
            ?: throw IllegalArgumentException("Group not found")
        
        val updatedGroup = existingGroup.copy(
            name = name,
            updatedAt = System.currentTimeMillis()
        )
        
        // Update group
        groupDao.updateGroup(updatedGroup)
        
        // Get existing members to preserve their IDs and relationships
        val existingMembers = groupDao.getMembersByGroupId(groupId)
        val cleanMemberNames = memberNames.filter { it.isNotBlank() }.map { it.trim() }
        
        // Update existing members that still exist
        val updatedMembers = mutableListOf<MemberEntity>()
        
        // Match existing members with new names by order/position
        existingMembers.zip(cleanMemberNames).forEach { (existingMember, newName) ->
            val updated = existingMember.copy(name = newName)
            groupDao.updateMember(updated)
            updatedMembers.add(updated)
        }
        
        // Remove excess existing members if we have fewer names now
        if (existingMembers.size > cleanMemberNames.size) {
            existingMembers.drop(cleanMemberNames.size).forEach { memberToDelete ->
                groupDao.deleteMember(memberToDelete)
            }
        }
        
        // Add new members if we have more names than existing members
        if (cleanMemberNames.size > existingMembers.size) {
            val newMemberNames = cleanMemberNames.drop(existingMembers.size)
            newMemberNames.forEach { memberName ->
                val newMember = MemberEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    name = memberName,
                    role = null
                )
                groupDao.insertMember(newMember)
                updatedMembers.add(newMember)
            }
        }
        
        return updatedGroup.toDomainModel(updatedMembers.map { it.toDomainModel() })
    }
    
    suspend fun deleteGroup(group: Group) {
        val entity = GroupEntity(
            id = group.id,
            name = group.name,
            createdAt = group.createdAt,
            updatedAt = group.updatedAt
        )
        groupDao.deleteMembersByGroupId(group.id)
        groupDao.deleteGroup(entity)
    }
}

// Extension functions for conversion
private fun GroupEntity.toDomainModel(members: List<Member>): Group {
    return Group(
        id = id,
        name = name,
        members = members,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

private fun MemberEntity.toDomainModel(): Member {
    return Member(
        id = id,
        name = name,
        role = role
    )
}