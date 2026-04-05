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

/** A file in the song's pool — belongs to the song, not to any member. */
data class SongFile(
    val id: String,
    val songId: String,
    val uploadedBy: String,  // Member.id — audit only, not access control
    val filePath: String,
    val fileType: FileType,
    val fileName: String,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class FileType(val extension: String) {
    PDF("pdf"),
    IMAGE("jpg"),
    ANNOTATION("json")
}

enum class SelectionType {
    MEMBER,  // File selected by a specific member (Band mode, or personal in Ensemble)
    PART     // File selected for an entire Part (Ensemble mode)
}

/** Links a Member or Part to the files they use for a song. */
data class FileSelection(
    val id: String,
    val songFileId: String,
    val selectionType: SelectionType,
    val memberId: String? = null,  // set when selectionType = MEMBER
    val partId: String? = null,    // set when selectionType = PART
    val displayOrder: Int = 0
)
