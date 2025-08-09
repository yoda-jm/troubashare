package com.troubashare.data.database.dao

import androidx.room.*
import com.troubashare.data.database.entities.SongEntity
import com.troubashare.data.database.entities.SongFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE groupId = :groupId ORDER BY updatedAt DESC")
    fun getSongsByGroupId(groupId: String): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): SongEntity?
    
    @Query("SELECT * FROM songs WHERE id = :id")
    fun getSongByIdFlow(id: String): Flow<SongEntity?>
    
    @Query("SELECT * FROM songs WHERE groupId = :groupId AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%')")
    fun searchSongs(groupId: String, query: String): Flow<List<SongEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)
    
    @Update
    suspend fun updateSong(song: SongEntity)
    
    @Delete
    suspend fun deleteSong(song: SongEntity)
    
    // Song Files
    @Query("SELECT * FROM song_files WHERE songId = :songId")
    suspend fun getFilesBySongId(songId: String): List<SongFileEntity>
    
    @Query("SELECT * FROM song_files WHERE songId = :songId")
    fun getFilesBySongIdFlow(songId: String): Flow<List<SongFileEntity>>
    
    @Query("SELECT COUNT(*) FROM song_files WHERE songId = :songId")
    suspend fun getFileCountBySongId(songId: String): Int
    
    @Query("SELECT * FROM song_files WHERE songId = :songId AND memberId = :memberId")
    suspend fun getFilesBySongAndMember(songId: String, memberId: String): List<SongFileEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongFile(file: SongFileEntity)
    
    @Delete
    suspend fun deleteSongFile(file: SongFileEntity)
}