package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.PartEntity
import com.troubashare.domain.model.Part
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class PartRepository(private val database: TroubaShareDatabase) {

    private val partDao = database.partDao()

    fun getPartsByGroupId(groupId: String): Flow<List<Part>> =
        partDao.getPartsByGroupId(groupId).map { it.map { e -> e.toDomain() } }

    suspend fun getPartsByGroupIdOnce(groupId: String): List<Part> =
        partDao.getPartsByGroupIdOnce(groupId).map { it.toDomain() }

    suspend fun getPartById(id: String): Part? =
        partDao.getPartById(id)?.toDomain()

    suspend fun createPart(groupId: String, name: String, color: String? = null): Part {
        val entity = PartEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            name = name,
            color = color
        )
        partDao.insertPart(entity)
        return entity.toDomain()
    }

    suspend fun updatePart(part: Part) {
        partDao.updatePart(part.toEntity())
    }

    suspend fun deletePart(part: Part) {
        partDao.deletePart(part.toEntity())
    }

    suspend fun deletePartsByGroupId(groupId: String) {
        partDao.deletePartsByGroupId(groupId)
    }
}

private fun PartEntity.toDomain() = Part(id = id, groupId = groupId, name = name, color = color)
private fun Part.toEntity() = PartEntity(id = id, groupId = groupId, name = name, color = color)
