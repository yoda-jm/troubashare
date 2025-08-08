package com.troubashare.data.database.dao

import androidx.room.*
import com.troubashare.data.database.entities.SetlistEntity
import com.troubashare.data.database.entities.SetlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SetlistDao {
    // Setlist operations
    @Query("SELECT * FROM setlists WHERE groupId = :groupId ORDER BY updatedAt DESC")
    fun getSetlistsByGroupId(groupId: String): Flow<List<SetlistEntity>>
    
    @Query("SELECT * FROM setlists WHERE id = :id")
    suspend fun getSetlistById(id: String): SetlistEntity?
    
    @Query("SELECT * FROM setlists WHERE groupId = :groupId AND name LIKE '%' || :query || '%'")
    fun searchSetlists(groupId: String, query: String): Flow<List<SetlistEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetlist(setlist: SetlistEntity)
    
    @Update
    suspend fun updateSetlist(setlist: SetlistEntity)
    
    @Delete
    suspend fun deleteSetlist(setlist: SetlistEntity)
    
    // Setlist item operations
    @Query("SELECT * FROM setlist_items WHERE setlistId = :setlistId ORDER BY position ASC")
    suspend fun getSetlistItemsBySetlistId(setlistId: String): List<SetlistItemEntity>
    
    @Query("SELECT * FROM setlist_items WHERE id = :id")
    suspend fun getSetlistItemById(id: String): SetlistItemEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetlistItem(item: SetlistItemEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetlistItems(items: List<SetlistItemEntity>)
    
    @Update
    suspend fun updateSetlistItem(item: SetlistItemEntity)
    
    @Update
    suspend fun updateSetlistItems(items: List<SetlistItemEntity>)
    
    @Delete
    suspend fun deleteSetlistItem(item: SetlistItemEntity)
    
    @Query("DELETE FROM setlist_items WHERE setlistId = :setlistId")
    suspend fun deleteSetlistItemsBySetlistId(setlistId: String)
    
    // Reordering operations
    @Query("UPDATE setlist_items SET position = :newPosition WHERE id = :itemId")
    suspend fun updateSetlistItemPosition(itemId: String, newPosition: Int)
    
    @Transaction
    suspend fun reorderSetlistItems(items: List<SetlistItemEntity>) {
        items.forEach { item ->
            updateSetlistItemPosition(item.id, item.position)
        }
    }
    
    // Combined queries for complex operations
    @Query("""
        SELECT COUNT(*) FROM setlist_items si 
        INNER JOIN songs s ON si.songId = s.id 
        WHERE si.setlistId = :setlistId
    """)
    suspend fun getSetlistItemCount(setlistId: String): Int
    
    @Query("""
        SELECT COALESCE(SUM(si.duration), COUNT(*) * 180) as totalDuration 
        FROM setlist_items si 
        WHERE si.setlistId = :setlistId
    """)
    suspend fun getSetlistTotalDuration(setlistId: String): Int
}