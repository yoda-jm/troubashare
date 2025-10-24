package com.troubashare.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class CloudFile(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val modifiedTime: String,
    val checksum: String? = null
)

@Serializable
data class CloudFileInfo(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedTime: String,
    val checksum: String? = null,
    val downloadUrl: String? = null
)

@Serializable
data class ShareCode(
    val code: String,
    val deepLink: String,
    val groupId: String,
    val folderId: String,
    val expiresAt: Long? = null
)

@Serializable
data class GroupManifest(
    val groupId: String,
    val name: String,
    val createdBy: String,
    val createdAt: Long,
    val lastModified: Long,
    val version: Int,
    val appVersion: String,
    val members: List<CloudMember>,
    val permissions: CloudPermissions,
    val syncSettings: CloudSyncSettings
)

@Serializable
data class CloudMember(
    val memberId: String,
    val name: String,
    val role: String? = null,
    val addedBy: String,
    val addedAt: Long
)

@Serializable
data class CloudPermissions(
    val adminDevices: List<String>,
    val editorDevices: List<String>,
    val viewerDevices: List<String>,
    val readOnlyDevices: List<String>
)

@Serializable
data class CloudSyncSettings(
    val autoSync: Boolean = true,
    val syncInterval: Long = 30000, // 30 seconds
    val encryptionEnabled: Boolean = true,
    val compressionEnabled: Boolean = true
)

@Serializable
data class ChangeLogEntry(
    val changeId: String,
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long,
    val changeType: ChangeType,
    val entityType: EntityType,
    val entityId: String,
    val entityName: String,
    val memberId: String? = null,
    val checksum: String,
    val description: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ChangeLog(
    val changes: List<ChangeLogEntry>,
    val lastChangeId: String?,
    val version: Int
)

enum class ChangeType {
    CREATE,
    UPDATE,
    DELETE,
    MOVE
}

enum class EntityType {
    GROUP,
    MEMBER,
    SONG,
    SONG_FILE,
    SETLIST,
    SETLIST_ITEM,
    ANNOTATION
}

@Serializable
data class SyncConflict(
    val conflictId: String,
    val entityType: EntityType,
    val entityId: String,
    val entityName: String,
    val localVersion: ConflictVersion,
    val remoteVersion: ConflictVersion,
    val conflictType: ConflictType,
    val canAutoResolve: Boolean
)

@Serializable
data class ConflictVersion(
    val timestamp: Long,
    val deviceId: String,
    val deviceName: String,
    val authorName: String,
    val checksum: String,
    val description: String
)

enum class ConflictType {
    SIMULTANEOUS_EDIT,
    DELETE_MODIFY,
    STRUCTURE_CHANGE,
    ANNOTATION_OVERLAP,
    VERSION_MISMATCH
}

enum class ResolutionAction {
    KEEP_LOCAL,
    ACCEPT_REMOTE,
    MERGE_ANNOTATIONS,
    LAYER_SEPARATE,
    MANUAL_MERGE
}

enum class ShareMethod {
    EMAIL,
    FILE_SHARE,
    QR_CODE,
    GOOGLE_DRIVE,
    MESSAGING
}

// ExportBundle will be implemented later when import/export is needed
data class ExportBundle(
    val metadata: ExportMetadata,
    val group: Group,
    val songs: List<Song>,
    val setlists: List<Setlist>,
    val annotations: List<Annotation>,
    val zipFilePath: String? = null
)

@Serializable
data class ExportMetadata(
    val groupName: String,
    val exportedBy: String,
    val exportedAt: Long,
    val version: String,
    val appVersion: String,
    val checksum: String,
    val fileCount: Int,
    val totalSize: Long
)

enum class CloudProvider {
    GOOGLE_DRIVE,
    DROPBOX,
    LOCAL_EXPORT,
    CUSTOM
}

@Serializable
data class CloudSettings(
    val provider: CloudProvider,
    val isEnabled: Boolean,
    val autoSync: Boolean,
    val syncInterval: Long,
    val encryptionEnabled: Boolean,
    val lastSync: Long? = null,
    val accountInfo: CloudAccountInfo? = null
)

@Serializable
data class CloudAccountInfo(
    val accountId: String,
    val accountName: String,
    val email: String? = null,
    val storageUsed: Long? = null,
    val storageLimit: Long? = null
)

// Version compatibility
@Serializable
data class AppVersionInfo(
    val version: String,
    val versionCode: Int,
    val minCompatibleVersion: String,
    val breakingChanges: List<String> = emptyList()
)

// Sync status
enum class SyncStatus {
    OFFLINE,
    SYNCING,
    UP_TO_DATE,
    CONFLICTS_DETECTED,
    ERROR,
    AUTHENTICATION_REQUIRED
}

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val lastSeen: Long,
    val isOnline: Boolean
)

// Song cloud data models
@Serializable
data class CloudSongMetadata(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val bpm: Int,
    val key: String,
    val timeSignature: String,
    val genre: String,
    val tags: List<String>,
    val createdAt: Long,
    val lastModified: Long,
    val checksum: String
)

// Setlist cloud data models
@Serializable
data class CloudSetlistData(
    val setlistId: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val lastModified: Long,
    val songs: List<CloudSetlistSong>
)

@Serializable
data class CloudSetlistSong(
    val songId: String,
    val order: Int,
    val notes: String
)

// Annotation cloud data models
@Serializable
data class CloudAnnotationLayer(
    val layerId: String,
    val name: String,
    val isVisible: Boolean,
    val createdAt: Long,
    val lastModified: Long,
    val annotations: List<CloudAnnotation>
)

@Serializable
data class CloudAnnotation(
    val annotationId: String,
    val type: String,
    val pageNumber: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val strokePath: String,
    val strokeWidth: Float,
    val strokeColor: Int,
    val fillColor: Int?,
    val text: String,
    val textSize: Float,
    val createdAt: Long
)