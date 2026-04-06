package com.troubashare.data.database.dao

import androidx.room.*
import com.troubashare.data.database.entities.FileSelectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileSelectionDao {

    /** All selections for files belonging to a song (joined via song_files). */
    @Query("""
        SELECT fs.* FROM file_selections fs
        INNER JOIN song_files sf ON fs.songFileId = sf.id
        WHERE sf.songId = :songId
        ORDER BY fs.displayOrder ASC
    """)
    fun getSelectionsBySongId(songId: String): Flow<List<FileSelectionEntity>>

    @Query("""
        SELECT fs.* FROM file_selections fs
        INNER JOIN song_files sf ON fs.songFileId = sf.id
        WHERE sf.songId = :songId
        ORDER BY fs.displayOrder ASC
    """)
    suspend fun getSelectionsBySongIdOnce(songId: String): List<FileSelectionEntity>

    /** Selections for a specific member for a song. */
    @Query("""
        SELECT fs.* FROM file_selections fs
        INNER JOIN song_files sf ON fs.songFileId = sf.id
        WHERE sf.songId = :songId
          AND fs.selectionType = 'MEMBER'
          AND fs.memberId = :memberId
        ORDER BY fs.displayOrder ASC
    """)
    suspend fun getMemberSelections(songId: String, memberId: String): List<FileSelectionEntity>

    /** Selections for a specific part for a song. */
    @Query("""
        SELECT fs.* FROM file_selections fs
        INNER JOIN song_files sf ON fs.songFileId = sf.id
        WHERE sf.songId = :songId
          AND fs.selectionType = 'PART'
          AND fs.partId = :partId
        ORDER BY fs.displayOrder ASC
    """)
    suspend fun getPartSelections(songId: String, partId: String): List<FileSelectionEntity>

    @Query("SELECT * FROM file_selections WHERE songFileId = :songFileId")
    suspend fun getSelectionsForFile(songFileId: String): List<FileSelectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSelection(selection: FileSelectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSelections(selections: List<FileSelectionEntity>)

    @Update
    suspend fun updateSelection(selection: FileSelectionEntity)

    @Delete
    suspend fun deleteSelection(selection: FileSelectionEntity)

    @Query("DELETE FROM file_selections WHERE songFileId = :songFileId")
    suspend fun deleteSelectionsForFile(songFileId: String)

    @Query("DELETE FROM file_selections WHERE songFileId = :songFileId AND memberId = :memberId AND selectionType = 'MEMBER'")
    suspend fun deleteMemberSelection(songFileId: String, memberId: String)

    @Query("UPDATE file_selections SET displayOrder = :newOrder WHERE id = :selectionId")
    suspend fun updateSelectionOrder(selectionId: String, newOrder: Int)
}
