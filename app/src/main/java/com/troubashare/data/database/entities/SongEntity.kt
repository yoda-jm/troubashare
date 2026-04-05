package com.troubashare.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val title: String,
    val artist: String? = null,
    val key: String? = null,
    val tempo: Int? = null,
    val tags: String? = null, // JSON string
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** File in the song's pool — belongs to the song, not to any member. */
@Entity(tableName = "song_files")
data class SongFileEntity(
    @PrimaryKey
    val id: String,
    val songId: String,
    val uploadedBy: String,  // Member.id — audit only
    val filePath: String,
    val fileType: String,    // FileType.name
    val fileName: String,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Links a Member (MEMBER type) or Part (PART type) to a file for a song. */
@Entity(tableName = "file_selections")
data class FileSelectionEntity(
    @PrimaryKey
    val id: String,
    val songFileId: String,
    val selectionType: String, // SelectionType.name: "MEMBER" | "PART"
    val memberId: String? = null,
    val partId: String? = null,
    val displayOrder: Int = 0
)
