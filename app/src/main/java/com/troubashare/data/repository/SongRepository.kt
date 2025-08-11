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
import kotlinx.coroutines.flow.combine
import java.util.*
import java.io.InputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SongRepository(
    private val database: TroubaShareDatabase,
    private val fileManager: FileManager,
    private val annotationRepository: AnnotationRepository
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
    
    fun getSongByIdFlow(id: String): Flow<Song?> {
        return combine(
            songDao.getSongByIdFlow(id),
            songDao.getFilesBySongIdFlow(id)
        ) { songEntity, fileEntities ->
            println("DEBUG SongRepository: Loading song '$id' - found ${fileEntities.size} file entities")
            fileEntities.forEachIndexed { index, fileEntity ->
                println("DEBUG SongRepository: File $index - id='${fileEntity.id}', fileName='${fileEntity.fileName}', songId='${fileEntity.songId}'")
            }
            songEntity?.let { entity ->
                entity.toDomainModel(fileEntities.map { it.toDomainModel() })
            }
        }
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
    
    suspend fun updateSong(
        songId: String,
        title: String,
        artist: String?,
        key: String?,
        tempo: Int?,
        tags: List<String>,
        notes: String?
    ): Song {
        val existingSong = songDao.getSongById(songId) 
            ?: throw IllegalArgumentException("Song not found")
        
        val entity = existingSong.copy(
            title = title,
            artist = artist,
            key = key,
            tempo = tempo,
            tags = gson.toJson(tags),
            notes = notes,
            updatedAt = System.currentTimeMillis()
        )
        
        songDao.updateSong(entity)
        val files = songDao.getFilesBySongId(songId)
        return entity.toDomainModel(files.map { it.toDomainModel() })
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
            println("DEBUG SongRepository: addFileToSong called - songId='$songId', memberId='$memberId', fileName='$fileName'")
            val song = songDao.getSongById(songId)
            if (song == null) {
                println("DEBUG SongRepository: Song not found for songId='$songId'")
                return Result.failure(Exception("Song not found"))
            }
            println("DEBUG SongRepository: Found song - id='${song.id}', title='${song.title}', groupId='${song.groupId}'")
            
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
                fileName.endsWith(".json", ignoreCase = true) -> FileType.ANNOTATION
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
            
            println("DEBUG SongRepository: Inserting SongFileEntity - id='${entity.id}', songId='${entity.songId}', fileName='${entity.fileName}'")
            songDao.insertSongFile(entity)
            val domainModel = entity.toDomainModel()
            println("DEBUG SongRepository: Created domain model - id='${domainModel.id}', songId='${domainModel.songId}', fileName='${domainModel.fileName}'")
            Result.success(domainModel)
        } catch (e: Exception) {
            println("DEBUG SongRepository: Error in addFileToSong - ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun removeFileFromSong(songFile: SongFile, cleanupAnnotations: Boolean = true): Result<Unit> {
        return try {
            println("DEBUG SongRepository: Deleting file - id='${songFile.id}', fileName='${songFile.fileName}', filePath='${songFile.filePath}'")
            
            // Delete file from storage
            val deleteResult = fileManager.deleteFile(songFile.filePath)
            if (deleteResult.isFailure) {
                println("DEBUG SongRepository: Failed to delete file from storage: ${deleteResult.exceptionOrNull()?.message}")
                return Result.failure(deleteResult.exceptionOrNull() ?: Exception("Failed to delete file"))
            }
            println("DEBUG SongRepository: Successfully deleted file from storage")
            
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
            println("DEBUG SongRepository: Successfully deleted file from database")
            
            // If this was an annotation file, also clean up associated annotations (only if requested)
            if (songFile.fileType == FileType.ANNOTATION && cleanupAnnotations) {
                println("DEBUG SongRepository: Cleaning up annotations for deleted annotation layer")
                
                // Extract original PDF fileId from annotation filename
                // Format: annotations_{originalFileId}_{memberId}_{timestamp}.json
                val originalFileId = extractOriginalFileIdFromAnnotationFilename(songFile.fileName)
                if (originalFileId != null) {
                    println("DEBUG SongRepository: Extracted original fileId '$originalFileId' from annotation filename '${songFile.fileName}'")
                    annotationRepository.clearAnnotationsForFile(originalFileId)
                    println("DEBUG SongRepository: Successfully cleaned up annotations for original file")
                } else {
                    println("DEBUG SongRepository: WARNING - Could not extract original fileId from annotation filename '${songFile.fileName}'")
                }
            } else if (songFile.fileType == FileType.ANNOTATION && !cleanupAnnotations) {
                println("DEBUG SongRepository: Skipping annotation cleanup for annotation file replacement")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG SongRepository: Error deleting file: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    private fun extractOriginalFileIdFromAnnotationFilename(filename: String): String? {
        // Format: annotations_{originalFileId}_{memberId}_{timestamp}.json
        return try {
            val parts = filename.split("_")
            if (parts.size >= 3 && parts[0] == "annotations") {
                parts[1] // The originalFileId is the second part
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
    val domainModel = SongFile(
        id = id,
        songId = songId,
        memberId = memberId,
        filePath = filePath,
        fileType = FileType.valueOf(fileType),
        fileName = fileName,
        createdAt = createdAt
    )
    println("DEBUG SongRepository: Converting entity to domain - entityId='$id', domainId='${domainModel.id}', fileName='$fileName'")
    return domainModel
}