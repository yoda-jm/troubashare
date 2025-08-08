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
    val tags: String? = null, // JSON string for simplicity
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "song_files")
data class SongFileEntity(
    @PrimaryKey
    val id: String,
    val songId: String,
    val memberId: String,
    val filePath: String,
    val fileType: String, // "PDF", "IMAGE", "ANNOTATION"
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis()
)