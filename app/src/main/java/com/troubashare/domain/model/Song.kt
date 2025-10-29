package com.troubashare.domain.model

data class Song(
    val id: String,
    val groupId: String,
    val title: String,
    val artist: String? = null,
    val key: String? = null,
    val tempo: Int? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val files: List<SongFile> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SongFile(
    val id: String,
    val songId: String,
    val memberId: String,
    val filePath: String,
    val fileType: FileType,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val displayOrder: Int = 0 // Order for display in lists and concert mode
)

enum class FileType(val extension: String) {
    PDF("pdf"),
    IMAGE("jpg"),
    ANNOTATION("json")
}