package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.SongEntity
import com.troubashare.data.database.entities.SongFileEntity
import com.troubashare.data.database.entities.FileSelectionEntity
import com.troubashare.domain.model.Song
import com.troubashare.domain.model.SongFile
import com.troubashare.domain.model.FileType
import com.troubashare.domain.model.FileSelection
import com.troubashare.domain.model.SelectionType
import com.troubashare.data.file.FileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.io.InputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SongRepository(
    private val database: TroubaShareDatabase,
    private val fileManager: FileManager,
    private val annotationRepository: AnnotationRepository
) {

    private val songDao = database.songDao()
    private val fileSelectionDao = database.fileSelectionDao()
    private val gson = Gson()

    fun getSongsByGroupId(groupId: String): Flow<List<Song>> {
        return songDao.getSongsByGroupId(groupId).map { entities ->
            entities.map { it.toDomain(emptyList()) }
        }
    }

    suspend fun getSongsWithFilesByGroupId(groupId: String): List<Song> {
        val entities = songDao.getSongsByGroupId(groupId).first()
        return entities.map { entity ->
            val files = songDao.getFilesBySongId(entity.id)
            entity.toDomain(files.map { it.toDomain() })
        }
    }

    fun searchSongs(groupId: String, query: String): Flow<List<Song>> {
        return songDao.searchSongs(groupId, query).map { entities ->
            entities.map { it.toDomain(emptyList()) }
        }
    }

    suspend fun getSongById(id: String): Song? {
        val entity = songDao.getSongById(id) ?: return null
        val files = songDao.getFilesBySongId(id)
        return entity.toDomain(files.map { it.toDomain() })
    }

    fun getSongByIdFlow(id: String): Flow<Song?> {
        return combine(
            songDao.getSongByIdFlow(id),
            songDao.getFilesBySongIdFlow(id)
        ) { songEntity, fileEntities ->
            songEntity?.toDomain(fileEntities.map { it.toDomain() })
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
        return entity.toDomain(emptyList())
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
        return entity.toDomain(files.map { it.toDomain() })
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

    /**
     * Adds a file to the song's pool.
     * If [autoSelectForMember] is not null, also creates a MEMBER FileSelection.
     * If [autoSelectForPart] is not null, also creates a PART FileSelection.
     */
    suspend fun addFileToSong(
        songId: String,
        uploadedBy: String,
        fileName: String,
        inputStream: InputStream,
        autoSelectForMember: String? = uploadedBy,
        autoSelectForPart: String? = null
    ): Result<SongFile> {
        return try {
            val song = songDao.getSongById(songId)
                ?: return Result.failure(Exception("Song not found"))

            val result = fileManager.saveFile(
                groupId = song.groupId,
                songId = songId,
                memberId = uploadedBy,
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
            val existingFiles = songDao.getFilesBySongId(songId)
            val nextOrder = (existingFiles.maxOfOrNull { it.displayOrder } ?: -1) + 1

            val fileEntity = SongFileEntity(
                id = fileId,
                songId = songId,
                uploadedBy = uploadedBy,
                filePath = filePath,
                fileType = fileType.name,
                fileName = fileName,
                displayOrder = nextOrder,
                createdAt = now
            )
            songDao.insertSongFile(fileEntity)

            // Auto-create selection if requested
            when {
                autoSelectForPart != null -> {
                    val existingPartSelections = fileSelectionDao.getPartSelections(songId, autoSelectForPart)
                    val partOrder = (existingPartSelections.maxOfOrNull { it.displayOrder } ?: -1) + 1
                    fileSelectionDao.insertSelection(
                        FileSelectionEntity(
                            id = UUID.randomUUID().toString(),
                            songFileId = fileId,
                            selectionType = SelectionType.PART.name,
                            partId = autoSelectForPart,
                            displayOrder = partOrder
                        )
                    )
                }
                autoSelectForMember != null -> {
                    val existingMemberSelections = fileSelectionDao.getMemberSelections(songId, autoSelectForMember)
                    val memberOrder = (existingMemberSelections.maxOfOrNull { it.displayOrder } ?: -1) + 1
                    fileSelectionDao.insertSelection(
                        FileSelectionEntity(
                            id = UUID.randomUUID().toString(),
                            songFileId = fileId,
                            selectionType = SelectionType.MEMBER.name,
                            memberId = autoSelectForMember,
                            displayOrder = memberOrder
                        )
                    )
                }
            }

            Result.success(fileEntity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFileFromSong(songFile: SongFile, cleanupAnnotations: Boolean = true): Result<Unit> {
        return try {
            // Best-effort physical delete — ignore if file is already gone
            fileManager.deleteFile(songFile.filePath)

            // Remove all selections for this file first
            fileSelectionDao.deleteSelectionsForFile(songFile.id)

            val entity = SongFileEntity(
                id = songFile.id,
                songId = songFile.songId,
                uploadedBy = songFile.uploadedBy,
                filePath = songFile.filePath,
                fileType = songFile.fileType.name,
                fileName = songFile.fileName,
                displayOrder = songFile.displayOrder,
                createdAt = songFile.createdAt
            )
            songDao.deleteSongFile(entity)

            if (songFile.fileType == FileType.ANNOTATION && cleanupAnnotations) {
                val originalFileId = extractOriginalFileIdFromAnnotationFilename(songFile.fileName)
                if (originalFileId != null) {
                    annotationRepository.clearAnnotationsForFile(originalFileId)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Returns files visible to a member, filtered by their selections. */
    suspend fun getFilesForMember(songId: String, memberId: String, partIds: List<String>): List<SongFile> {
        val allFiles = songDao.getFilesBySongId(songId).associateBy { it.id }
        val allSelections = fileSelectionDao.getSelectionsBySongIdOnce(songId)

        val partSelections = allSelections
            .filter { it.selectionType == SelectionType.PART.name && it.partId in partIds }
            .sortedBy { it.displayOrder }

        val memberSelections = allSelections
            .filter { it.selectionType == SelectionType.MEMBER.name && it.memberId == memberId }
            .sortedBy { it.displayOrder }

        val seen = mutableSetOf<String>()
        val result = mutableListOf<SongFile>()
        (partSelections + memberSelections).forEach { sel ->
            if (seen.add(sel.songFileId)) {
                allFiles[sel.songFileId]?.let { result.add(it.toDomain()) }
            }
        }
        return result
    }

    suspend fun moveFile(songId: String, memberId: String, fileId: String, newPosition: Int): Result<Unit> {
        return try {
            // Reorder member selections for this member
            val selections = fileSelectionDao.getMemberSelections(songId, memberId)
                .sortedBy { it.displayOrder }
                .toMutableList()

            val selIndex = selections.indexOfFirst { it.songFileId == fileId }
            if (selIndex == -1) {
                // Fallback: try to reorder in the file pool directly
                val files = songDao.getFilesBySongId(songId).sortedBy { it.displayOrder }.toMutableList()
                val fileIndex = files.indexOfFirst { it.id == fileId }
                if (fileIndex == -1) return Result.failure(Exception("File not found"))
                val file = files.removeAt(fileIndex)
                files.add(newPosition, file)
                songDao.reorderFiles(files.mapIndexed { i, f -> f.copy(displayOrder = i) })
                return Result.success(Unit)
            }

            val sel = selections.removeAt(selIndex)
            selections.add(newPosition, sel)
            selections.forEachIndexed { i, s ->
                fileSelectionDao.updateSelectionOrder(s.id, i)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractOriginalFileIdFromAnnotationFilename(filename: String): String? {
        return try {
            val parts = filename.split("_")
            if (parts.size >= 3 && parts[0] == "annotations") parts[1] else null
        } catch (e: Exception) {
            null
        }
    }
}

private fun SongEntity.toDomain(files: List<SongFile>): Song {
    val gson = Gson()
    val tagsList: List<String> = try {
        if (tags.isNullOrEmpty()) emptyList()
        else {
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

private fun SongFileEntity.toDomain() = SongFile(
    id = id,
    songId = songId,
    uploadedBy = uploadedBy,
    filePath = filePath,
    fileType = FileType.valueOf(fileType),
    fileName = fileName,
    displayOrder = displayOrder,
    createdAt = createdAt
)
