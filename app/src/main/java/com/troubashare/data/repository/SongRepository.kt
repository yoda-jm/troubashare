package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.SongEntity
import com.troubashare.data.database.entities.SongFileEntity
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.SongFile
import com.troubashare.domain.model.FileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SongRepository(private val database: TroubaShareDatabase) {
    
    private val songDao = database.songDao()
    private val gson = Gson()
    
    fun getSongsByGroupId(groupId: String): Flow<List<Song>> {
        return songDao.getSongsByGroupId(groupId).map { entities ->
            entities.map { entity ->
                entity.toDomainModel(emptyList()) // For now, skip loading files in list view
            }
        }
    }
    
    fun searchSongs(groupId: String, query: String): Flow<List<Song>> {
        return songDao.searchSongs(groupId, query).map { entities ->
            entities.map { entity ->
                entity.toDomainModel(emptyList()) // For now, skip loading files in list view
            }
        }
    }
    
    suspend fun getSongById(id: String): Song? {
        val entity = songDao.getSongById(id) ?: return null
        val files = songDao.getFilesBySongId(id)
        return entity.toDomainModel(files.map { it.toDomainModel() })
    }
    
    suspend fun createSong(
        groupId: String,
        title: String,
        artist: String? = null,
        key: String? = null,
        tempo: Int? = null,
        tags: List<String> = emptyList(),
        notes: String? = null
    ): Song {
        val songId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val entity = SongEntity(
            id = songId,
            groupId = groupId,
            title = title,
            artist = artist,
            key = key,
            tempo = tempo,
            tags = gson.toJson(tags),
            notes = notes,
            createdAt = now,
            updatedAt = now
        )
        
        songDao.insertSong(entity)
        return entity.toDomainModel(emptyList())
    }
    
    suspend fun updateSong(song: Song): Song {
        val entity = SongEntity(
            id = song.id,
            groupId = song.groupId,
            title = song.title,
            artist = song.artist,
            key = song.key,
            tempo = song.tempo,
            tags = gson.toJson(song.tags),
            notes = song.notes,
            createdAt = song.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        
        songDao.updateSong(entity)
        return song.copy(updatedAt = entity.updatedAt)
    }
    
    suspend fun deleteSong(song: Song) {
        val entity = SongEntity(
            id = song.id,
            groupId = song.groupId,
            title = song.title,
            artist = song.artist,
            key = song.key,
            tempo = song.tempo,
            tags = gson.toJson(song.tags),
            notes = song.notes,
            createdAt = song.createdAt,
            updatedAt = song.updatedAt
        )
        songDao.deleteSong(entity)
    }
    
    suspend fun addFileToSong(
        songId: String,
        memberId: String,
        filePath: String,
        fileType: FileType,
        fileName: String
    ): SongFile {
        val fileId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val entity = SongFileEntity(
            id = fileId,
            songId = songId,
            memberId = memberId,
            filePath = filePath,
            fileType = fileType.name,
            fileName = fileName,
            createdAt = now
        )
        
        songDao.insertSongFile(entity)
        return entity.toDomainModel()
    }
}

// Extension functions
private fun SongEntity.toDomainModel(files: List<SongFile>): Song {
    val gson = Gson()
    val tagsList: List<String> = try {
        if (tags.isNullOrEmpty()) {
            emptyList()
        } else {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(tags, type) ?: emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
    
    return Song(
        id = id,
        groupId = groupId,
        title = title,
        artist = artist,
        key = key,
        tempo = tempo,
        tags = tagsList,
        notes = notes,
        files = files,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

private fun SongFileEntity.toDomainModel(): SongFile {
    return SongFile(
        id = id,
        songId = songId,
        memberId = memberId,
        filePath = filePath,
        fileType = FileType.valueOf(fileType),
        fileName = fileName,
        createdAt = createdAt
    )
}