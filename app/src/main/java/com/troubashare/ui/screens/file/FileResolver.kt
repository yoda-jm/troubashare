package com.troubashare.ui.screens.file

import com.troubashare.data.repository.SongRepository

/**
 * Resolves the effective file, member, and song IDs to use for annotation storage.
 * Handles cases where the passed IDs are blank, placeholder values, or need to be
 * looked up from the database via the file path.
 */
class FileResolver(
    private val songRepository: SongRepository,
    private val fileId: String,
    private val memberId: String,
    private val songId: String,
    private val filePath: String
) {
    private var resolvedFileId: String? = null
    private var resolvedMemberId: String? = null
    private var resolvedSongId: String? = null

    fun getEffectiveFileId(): String {
        return resolvedFileId
            ?: fileId.takeIf { it.isNotBlank() }
            ?: "path-${filePath.hashCode().toString().replace("-", "n")}"
    }

    fun getEffectiveMemberId(): String {
        return resolvedMemberId
            ?: memberId.takeIf { it.isNotBlank() && it != "current-member-id" && it != "unknown-member" }
            ?: "fallback-member"
    }

    fun getEffectiveSongId(): String {
        return resolvedSongId
            ?: songId.takeIf { it.isNotBlank() }
            ?: ""
    }

    /**
     * Attempts to resolve file/member/song IDs from the database using [filePath].
     * Should be called when [fileId] is blank but [filePath] is available.
     */
    suspend fun resolveFromPath() {
        if (filePath.isBlank()) return
        try {
            val pathSegments = filePath.split("/")
            val songsIndex = pathSegments.indexOf("songs")
            if (songsIndex == -1 || songsIndex + 1 >= pathSegments.size) return

            val extractedSongId = pathSegments[songsIndex + 1]
            val song = songRepository.getSongById(extractedSongId)
            val matchingFile = song?.files?.find { it.filePath == filePath } ?: return

            resolvedFileId = matchingFile.id
            resolvedSongId = matchingFile.songId
            if (memberId.isBlank() || memberId == "current-member-id" || memberId == "unknown-member") {
                resolvedMemberId = matchingFile.memberId
            }
        } catch (_: Exception) {
            // Fall through — effective IDs will use fallback values
        }
    }
}
