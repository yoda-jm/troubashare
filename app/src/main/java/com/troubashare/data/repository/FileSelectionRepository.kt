package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.FileSelectionEntity
import com.troubashare.domain.model.FileSelection
import com.troubashare.domain.model.SelectionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FileSelectionRepository(private val database: TroubaShareDatabase) {

    private val dao = database.fileSelectionDao()

    fun getSelectionsBySongId(songId: String): Flow<List<FileSelection>> =
        dao.getSelectionsBySongId(songId).map { it.map { e -> e.toDomain() } }

    suspend fun getSelectionsBySongIdOnce(songId: String): List<FileSelection> =
        dao.getSelectionsBySongIdOnce(songId).map { it.toDomain() }

    /**
     * Returns the ordered file IDs a member should see for a song.
     * In Band mode (no parts): returns MEMBER selections for that member.
     * In Ensemble mode: returns PART selections for their parts + personal MEMBER selections.
     * Deduplication: if a member-level selection overlaps with a part-level one, member wins.
     */
    suspend fun getFileIdsForMember(songId: String, memberId: String, partIds: List<String>): List<String> {
        val allSelections = dao.getSelectionsBySongIdOnce(songId)

        val partSelections = allSelections
            .filter { it.selectionType == SelectionType.PART.name && it.partId in partIds }
            .sortedBy { it.displayOrder }

        val memberSelections = allSelections
            .filter { it.selectionType == SelectionType.MEMBER.name && it.memberId == memberId }
            .sortedBy { it.displayOrder }

        // Member selections override/supplement part selections
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        (partSelections + memberSelections).forEach {
            if (seen.add(it.songFileId)) result.add(it.songFileId)
        }
        return result
    }

    suspend fun addMemberSelection(songFileId: String, memberId: String, displayOrder: Int = 0): FileSelection {
        val entity = FileSelectionEntity(
            id = UUID.randomUUID().toString(),
            songFileId = songFileId,
            selectionType = SelectionType.MEMBER.name,
            memberId = memberId,
            partId = null,
            displayOrder = displayOrder
        )
        dao.insertSelection(entity)
        return entity.toDomain()
    }

    suspend fun addPartSelection(songFileId: String, partId: String, displayOrder: Int = 0): FileSelection {
        val entity = FileSelectionEntity(
            id = UUID.randomUUID().toString(),
            songFileId = songFileId,
            selectionType = SelectionType.PART.name,
            memberId = null,
            partId = partId,
            displayOrder = displayOrder
        )
        dao.insertSelection(entity)
        return entity.toDomain()
    }

    suspend fun removeSelection(selection: FileSelection) {
        dao.deleteSelection(selection.toEntity())
    }

    suspend fun removeMemberSelection(songFileId: String, memberId: String) {
        dao.deleteMemberSelection(songFileId, memberId)
    }

    suspend fun removeSelectionsForFile(songFileId: String) {
        dao.deleteSelectionsForFile(songFileId)
    }
}

private fun FileSelectionEntity.toDomain() = FileSelection(
    id = id,
    songFileId = songFileId,
    selectionType = SelectionType.valueOf(selectionType),
    memberId = memberId,
    partId = partId,
    displayOrder = displayOrder
)

private fun FileSelection.toEntity() = FileSelectionEntity(
    id = id,
    songFileId = songFileId,
    selectionType = selectionType.name,
    memberId = memberId,
    partId = partId,
    displayOrder = displayOrder
)
