package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.SongEntity
import com.troubashare.data.database.entities.SongFileEntity
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.SongFile
import com.troubashare.domain.model.FileType
import com.troubashare.data.file.FileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*
import java.io.InputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SongRepository(
    private val database: TroubaShareDatabase,
    private val fileManager: FileManager
) {
    
    private val songDao = database.songDao()
    private val gson = Gson()
    
    fun getSongsByGroupId(groupId: String): Flow<List<Song>> {
        return songDao.getSongsByGroupId(groupId).map { entities ->
            entities.map { entity ->
                // For list view, load files count only for performance
                entity.toDomainModel(emptyList())
            }
        }
    }
    
    fun searchSongs(groupId: String, query: String): Flow<List<Song>> {
        return songDao.searchSongs(groupId, query).map { entities ->
            entities.map { entity ->
                // For search view, load files count only for performance  
                entity.toDomainModel(emptyList())
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
        fileName: String,
        inputStream: InputStream
    ): Result<SongFile> {
        return try {
            val song = songDao.getSongById(songId)
                ?: return Result.failure(Exception("Song not found"))
            
            // Save file to storage
            val result = fileManager.saveFile(
                groupId = song.groupId,
                songId = songId,
                memberId = memberId,
                fileName = fileName,
                inputStream = inputStream
            )
            
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Exception("Failed to save file"))
            }
            
            val filePath = result.getOrThrow()
            val fileType = when {
                fileManager.isPdfFile(fileName) -> FileType.PDF
                fileManager.isImageFile(fileName) -> FileType.IMAGE
                else -> return Result.failure(Exception("Unsupported file type"))
            }
            
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
            Result.success(entity.toDomainModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun removeFileFromSong(songFile: SongFile): Result<Unit> {
        return try {
            // Delete file from storage
            val deleteResult = fileManager.deleteFile(songFile.filePath)
            if (deleteResult.isFailure) {
                return Result.failure(deleteResult.exceptionOrNull() ?: Exception("Failed to delete file"))
            }
            
            // Remove from database
            val entity = SongFileEntity(
                id = songFile.id,
                songId = songFile.songId,
                memberId = songFile.memberId,
                filePath = songFile.filePath,
                fileType = songFile.fileType.name,
                fileName = songFile.fileName,
                createdAt = songFile.createdAt
            )
            
            songDao.deleteSongFile(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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