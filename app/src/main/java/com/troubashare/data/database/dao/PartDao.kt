package com.troubashare.data.database.dao

import androidx.room.*
import com.troubashare.data.database.entities.PartEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {
    @Query("SELECT * FROM parts WHERE groupId = :groupId ORDER BY name ASC")
    fun getPartsByGroupId(groupId: String): Flow<List<PartEntity>>

    @Query("SELECT * FROM parts WHERE groupId = :groupId ORDER BY name ASC")
    suspend fun getPartsByGroupIdOnce(groupId: String): List<PartEntity>

    @Query("SELECT * FROM parts WHERE id = :id")
    suspend fun getPartById(id: String): PartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: PartEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<PartEntity>)

    @Update
    suspend fun updatePart(part: PartEntity)

    @Delete
    suspend fun deletePart(part: PartEntity)

    @Query("DELETE FROM parts WHERE groupId = :groupId")
    suspend fun deletePartsByGroupId(groupId: String)
}
