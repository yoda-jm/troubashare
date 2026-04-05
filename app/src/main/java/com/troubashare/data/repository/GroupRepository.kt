package com.troubashare.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.GroupEntity
import com.troubashare.data.database.entities.MemberEntity
import com.troubashare.domain.model.Group
import com.troubashare.domain.model.GroupType
import com.troubashare.domain.model.Member
import com.troubashare.domain.model.Part
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class GroupRepository(private val database: TroubaShareDatabase) {

    private val groupDao = database.groupDao()
    private val partDao = database.partDao()
    private val gson = Gson()

    fun getAllGroups(): Flow<List<Group>> {
        return groupDao.getAllGroups().map { entities ->
            entities.map { entity ->
                val members = groupDao.getMembersByGroupId(entity.id)
                val parts = partDao.getPartsByGroupIdOnce(entity.id)
                entity.toDomain(
                    members = members.map { it.toDomain(gson) },
                    parts = parts.map { Part(it.id, it.groupId, it.name, it.color) }
                )
            }
        }
    }

    suspend fun getGroupById(id: String): Group? {
        val entity = groupDao.getGroupById(id) ?: return null
        val members = groupDao.getMembersByGroupId(id)
        val parts = partDao.getPartsByGroupIdOnce(id)
        return entity.toDomain(
            members = members.map { it.toDomain(gson) },
            parts = parts.map { Part(it.id, it.groupId, it.name, it.color) }
        )
    }

    suspend fun createGroup(
        name: String,
        type: GroupType = GroupType.BAND,
        memberNames: List<String>,
        partNames: List<String> = emptyList()
    ): Group {
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val groupEntity = GroupEntity(
            id = groupId,
            name = name,
            type = type.name,
            createdAt = now,
            updatedAt = now
        )
        groupDao.insertGroup(groupEntity)

        val memberEntities = memberNames.filter { it.isNotBlank() }.map { memberName ->
            MemberEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                name = memberName.trim(),
                partIds = "[]"
            )
        }
        memberEntities.forEach { groupDao.insertMember(it) }

        val partEntities = partNames.filter { it.isNotBlank() }.map { partName ->
            com.troubashare.data.database.entities.PartEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                name = partName.trim()
            )
        }
        partEntities.forEach { partDao.insertPart(it) }

        return groupEntity.toDomain(
            members = memberEntities.map { it.toDomain(gson) },
            parts = partEntities.map { Part(it.id, it.groupId, it.name, it.color) }
        )
    }

    suspend fun updateGroup(
        groupId: String,
        name: String,
        memberNames: List<String>
    ): Group {
        val existingGroup = groupDao.getGroupById(groupId)
            ?: throw IllegalArgumentException("Group not found")

        val updatedGroup = existingGroup.copy(name = name, updatedAt = System.currentTimeMillis())
        groupDao.updateGroup(updatedGroup)

        val existingMembers = groupDao.getMembersByGroupId(groupId)
        val cleanNames = memberNames.filter { it.isNotBlank() }.map { it.trim() }
        val updatedMembers = mutableListOf<MemberEntity>()

        existingMembers.zip(cleanNames).forEach { (existing, newName) ->
            val updated = existing.copy(name = newName)
            groupDao.updateMember(updated)
            updatedMembers.add(updated)
        }

        if (existingMembers.size > cleanNames.size) {
            existingMembers.drop(cleanNames.size).forEach { groupDao.deleteMember(it) }
        }

        if (cleanNames.size > existingMembers.size) {
            cleanNames.drop(existingMembers.size).forEach { memberName ->
                val newMember = MemberEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    name = memberName,
                    partIds = "[]"
                )
                groupDao.insertMember(newMember)
                updatedMembers.add(newMember)
            }
        }

        val parts = partDao.getPartsByGroupIdOnce(groupId)
        return updatedGroup.toDomain(
            members = updatedMembers.map { it.toDomain(gson) },
            parts = parts.map { Part(it.id, it.groupId, it.name, it.color) }
        )
    }

    suspend fun getMemberById(memberId: String): Member? {
        val entity = groupDao.getMemberById(memberId) ?: return null
        return entity.toDomain(gson)
    }

    fun getMembersByGroupId(groupId: String): Flow<List<Member>> {
        return groupDao.getMembersByGroupIdFlow(groupId).map { entities ->
            entities.map { it.toDomain(gson) }
        }
    }

    suspend fun deleteGroup(group: Group) {
        groupDao.deleteMembersByGroupId(group.id)
        partDao.deletePartsByGroupId(group.id)
        val entity = GroupEntity(
            id = group.id,
            name = group.name,
            type = group.type.name,
            createdAt = group.createdAt,
            updatedAt = group.updatedAt
        )
        groupDao.deleteGroup(entity)
    }
}

private fun GroupEntity.toDomain(members: List<Member>, parts: List<Part>): Group = Group(
    id = id,
    name = name,
    type = try { GroupType.valueOf(type) } catch (e: Exception) { GroupType.BAND },
    members = members,
    parts = parts,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun MemberEntity.toDomain(gson: Gson): Member {
    val partIdsList: List<String> = try {
        val type = object : TypeToken<List<String>>() {}.type
        gson.fromJson(partIds, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
    return Member(id = id, groupId = groupId, name = name, partIds = partIdsList)
}
