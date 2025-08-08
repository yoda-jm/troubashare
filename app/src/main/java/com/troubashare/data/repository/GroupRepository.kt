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