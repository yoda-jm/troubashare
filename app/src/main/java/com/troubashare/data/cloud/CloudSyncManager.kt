package com.troubashare.data.cloud

import android.content.Context
import android.util.Log
import com.troubashare.data.repository.*
import com.troubashare.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    private val context: Context,
    private val googleDriveProvider: GoogleDriveProvider,
    private val groupRepository: GroupRepository,
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val annotationRepository: AnnotationRepository,
    private val changeTracker: ChangeTracker,
    private val conflictResolver: ConflictResolver
) {
    companion object {
        private const val TAG = "CloudSyncManager"
        private const val SYNC_INTERVAL_MS = 30_000L // 30 seconds
        private const val MANIFEST_FILE = "group-manifest.json"
        private const val CHANGE_LOG_FILE = "change-log.json"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L // 1 second
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.OFFLINE)
    private var syncJob: Job? = null
    
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Progress callback for detailed sync progress
    private var progressCallback: ((String) -> Unit)? = null
    
    fun setProgressCallback(callback: (String) -> Unit) {
        progressCallback = callback
    }
    
    private fun updateProgress(message: String) {
        progressCallback?.invoke(message)
        Log.d(TAG, "Sync Progress: $message")
    }
    
    /**
     * Initialize cloud sync for a group
     */
    suspend fun enableCloudSync(groupId: String): Result<ShareCode> {
        try {
            _syncStatus.value = SyncStatus.SYNCING
            
            // Authenticate with Google Drive
            val authResult = googleDriveProvider.authenticate()
            if (authResult.isFailure) {
                _syncStatus.value = SyncStatus.AUTHENTICATION_REQUIRED
                return Result.failure(authResult.exceptionOrNull()!!)
            }
            
            val group = groupRepository.getGroupById(groupId)
                ?: return Result.failure(Exception("Group not found"))
            
            // Create folder structure in Google Drive
            val folderStructure = createGroupFolderStructure(group)
            if (folderStructure.isFailure) {
                _syncStatus.value = SyncStatus.ERROR
                return Result.failure(folderStructure.exceptionOrNull()!!)
            }
            
            val groupFolderId = folderStructure.getOrThrow()
            
            // Upload initial group manifest
            val manifestResult = uploadGroupManifest(group, groupFolderId)
            if (manifestResult.isFailure) {
                _syncStatus.value = SyncStatus.ERROR
                return Result.failure(manifestResult.exceptionOrNull()!!)
            }
            
            // Perform initial full sync
            val syncResult = performInitialSync(groupId, groupFolderId)
            if (syncResult.isFailure) {
                _syncStatus.value = SyncStatus.ERROR
                return Result.failure(syncResult.exceptionOrNull()!!)
            }
            
            // Generate share code
            val shareCode = generateShareCode(groupId, groupFolderId)
            
            // Save cloud settings
            saveGroupCloudSettings(groupId, groupFolderId, shareCode)
            
            // Start continuous sync
            startContinuousSync(groupId)
            
            _syncStatus.value = SyncStatus.UP_TO_DATE
            return Result.success(shareCode)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable cloud sync", e)
            _syncStatus.value = SyncStatus.ERROR
            return Result.failure(e)
        }
    }
    
    /**
     * Join a shared group using share code
     */
    suspend fun joinSharedGroup(shareCode: String): Result<Group> {
        try {
            _syncStatus.value = SyncStatus.SYNCING
            
            // Parse share code
            val shareInfo = parseShareCode(shareCode)
            
            // Authenticate with Google Drive
            val authResult = googleDriveProvider.authenticate()
            if (authResult.isFailure) {
                _syncStatus.value = SyncStatus.AUTHENTICATION_REQUIRED
                return Result.failure(authResult.exceptionOrNull()!!)
            }
            
            // Verify access to shared folder
            val accessResult = verifyFolderAccess(shareInfo.folderId)
            if (accessResult.isFailure) {
                _syncStatus.value = SyncStatus.ERROR
                return Result.failure(accessResult.exceptionOrNull()!!)
            }
            
            // Download and parse group manifest
            val manifestResult = downloadGroupManifest(shareInfo.folderId)
            if (manifestResult.isFailure) {
                _syncStatus.value = SyncStatus.ERROR
                return Result.failure(manifestResult.exceptionOrNull()!!)
            }
            
            val groupManifest = manifestResult.getOrThrow()
            
            // Create local group from manifest
            val group = createGroupFromManifest(groupManifest)
            
            // Perform initial download sync
            val syncResult = performInitialDownload(group.id, shareInfo.folderId)
            if (syncResult.isFailure) {
                _syncStatus.value = SyncStatus.ERROR
                return Result.failure(syncResult.exceptionOrNull()!!)
            }
            
            // Save cloud settings
            saveGroupCloudSettings(group.id, shareInfo.folderId, shareInfo)
            
            // Start continuous sync
            startContinuousSync(group.id)
            
            _syncStatus.value = SyncStatus.UP_TO_DATE
            return Result.success(group)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join shared group", e)
            _syncStatus.value = SyncStatus.ERROR
            return Result.failure(e)
        }
    }
    
    /**
     * Start continuous synchronization for a group
     */
    fun startContinuousSync(groupId: String) {
        syncJob?.cancel()
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                try {
                    performDeltaSync(groupId)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync error", e)
                    _syncStatus.value = SyncStatus.ERROR
                }
            }
        }
    }
    
    /**
     * Stop continuous synchronization
     */
    fun stopContinuousSync() {
        syncJob?.cancel()
        syncJob = null
        _syncStatus.value = SyncStatus.OFFLINE
    }
    
    /**
     * Perform full initial synchronization (for first-time sync - upload content)
     */
    private suspend fun performInitialSync(groupId: String, cloudFolderId: String): Result<Unit> {
        try {
            Log.d(TAG, "Performing initial sync (upload) for group: $groupId")
            
            // Upload all songs
            val songsResult = uploadAllSongs(groupId, cloudFolderId)
            if (songsResult.isFailure) {
                Log.w(TAG, "Songs upload had issues: ${songsResult.exceptionOrNull()?.message}")
            }
            
            // Upload all setlists
            val setlistsResult = uploadAllSetlists(groupId, cloudFolderId)
            if (setlistsResult.isFailure) {
                Log.w(TAG, "Setlists upload had issues: ${setlistsResult.exceptionOrNull()?.message}")
            }
            
            // Update last sync timestamp
            updateLastSyncTimestamp(groupId)
            
            Log.d(TAG, "Initial sync completed for group: $groupId")
            return Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Initial sync failed", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Perform full download synchronization (when joining existing group)
     */
    private suspend fun performInitialDownload(groupId: String, cloudFolderId: String): Result<Unit> {
        try {
            Log.d(TAG, "Performing initial download for group: $groupId")
            
            // Download all songs
            val songsResult = downloadAllSongs(groupId, cloudFolderId)
            if (songsResult.isFailure) {
                Log.w(TAG, "Songs download had issues: ${songsResult.exceptionOrNull()?.message}")
            }
            
            // Download all setlists
            val setlistsResult = downloadAllSetlists(groupId, cloudFolderId)
            if (setlistsResult.isFailure) {
                Log.w(TAG, "Setlists download had issues: ${setlistsResult.exceptionOrNull()?.message}")
            }
            
            // Download all annotations
            val annotationsResult = downloadAllAnnotations(groupId, cloudFolderId)
            if (annotationsResult.isFailure) {
                Log.w(TAG, "Annotations download had issues: ${annotationsResult.exceptionOrNull()?.message}")
            }
            
            // Update last sync timestamp
            updateLastSyncTimestamp(groupId)
            
            Log.d(TAG, "Initial download completed for group: $groupId")
            return Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Initial download failed", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Perform delta synchronization (only changed items)
     */
    private suspend fun performDeltaSync(groupId: String) {
        try {
            val cloudSettings = getGroupCloudSettings(groupId) ?: return
            
            // Check for remote changes
            val remoteChanges = downloadChangeLog(cloudSettings.folderId)
            val lastLocalSync = getLastSyncTimestamp(groupId)
            val newChanges = remoteChanges.filter { it.timestamp > lastLocalSync }
            
            // Check for local changes
            val localChanges = changeTracker.getPendingChanges(groupId)
            
            // Handle conflicts if both exist
            if (newChanges.isNotEmpty() && localChanges.isNotEmpty()) {
                val conflicts = conflictResolver.detectConflicts(groupId, localChanges, newChanges)
                
                if (conflicts.isNotEmpty()) {
                    // Try auto-resolution first
                    val remainingConflicts = conflictResolver.autoResolveConflicts(conflicts)
                    
                    if (remainingConflicts.isNotEmpty()) {
                        _syncStatus.value = SyncStatus.CONFLICTS_DETECTED
                        Log.w(TAG, "Detected ${remainingConflicts.size} unresolved conflicts")
                        return
                    } else {
                        Log.d(TAG, "All conflicts auto-resolved")
                    }
                }
            }
            
            // Apply remote changes
            if (newChanges.isNotEmpty()) {
                applyRemoteChanges(groupId, newChanges)
            }
            
            // Upload local changes
            if (localChanges.isNotEmpty()) {
                uploadLocalChanges(groupId, localChanges, cloudSettings.folderId)
            }
            
            // Update sync timestamp
            updateLastSyncTimestamp(groupId)
            
            _syncStatus.value = SyncStatus.UP_TO_DATE
            
        } catch (e: Exception) {
            Log.e(TAG, "Delta sync failed", e)
            _syncStatus.value = SyncStatus.ERROR
        }
    }
    
    /**
     * Create folder structure in Google Drive
     */
    private suspend fun createGroupFolderStructure(group: Group): Result<String> {
        try {
            // Create main group folder
            val groupFolder = googleDriveProvider.createFolder("TroubaShare-${group.name}")
            if (groupFolder.isFailure) return Result.failure(groupFolder.exceptionOrNull()!!)
            
            val groupFolderId = groupFolder.getOrThrow().id
            
            // Create subfolders
            val folders = listOf("songs", "setlists", "sync")
            folders.forEach { folderName ->
                val folderResult = googleDriveProvider.createFolder(folderName, groupFolderId)
                if (folderResult.isFailure) return Result.failure(folderResult.exceptionOrNull()!!)
            }
            
            return Result.success(groupFolderId)
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * Upload group manifest to cloud
     */
    private suspend fun uploadGroupManifest(group: Group, groupFolderId: String): Result<Unit> {
        try {
            val manifest = createGroupManifest(group)
            val manifestJson = json.encodeToString(manifest)
            
            // Write to temporary file
            val tempFile = File(context.cacheDir, MANIFEST_FILE)
            tempFile.writeText(manifestJson)
            
            // Upload to cloud
            val uploadResult = googleDriveProvider.uploadFile(
                tempFile.absolutePath,
                MANIFEST_FILE,
                groupFolderId
            )
            
            // Clean up temp file
            tempFile.delete()
            
            return if (uploadResult.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(uploadResult.exceptionOrNull()!!)
            }
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * Download group manifest from cloud
     */
    private suspend fun downloadGroupManifest(groupFolderId: String): Result<GroupManifest> {
        try {
            // Find manifest file
            val filesResult = googleDriveProvider.listFiles(groupFolderId, MANIFEST_FILE)
            if (filesResult.isFailure) return Result.failure(filesResult.exceptionOrNull()!!)
            
            val manifestFile = filesResult.getOrThrow()
                .find { it.name == MANIFEST_FILE }
                ?: return Result.failure(Exception("Group manifest not found"))
            
            // Download to temporary file
            val tempFile = File(context.cacheDir, "temp-$MANIFEST_FILE")
            val downloadResult = googleDriveProvider.downloadFile(
                CloudFile(
                    id = manifestFile.id,
                    name = manifestFile.name,
                    path = "/$MANIFEST_FILE",
                    size = manifestFile.size,
                    mimeType = "application/json",
                    modifiedTime = manifestFile.modifiedTime,
                    checksum = manifestFile.checksum
                ),
                tempFile.absolutePath
            )
            
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull()!!)
            }
            
            // Parse manifest
            val manifestJson = tempFile.readText()
            val manifest = json.decodeFromString<GroupManifest>(manifestJson)
            
            // Clean up temp file
            tempFile.delete()
            
            return Result.success(manifest)
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * Generate share code for group
     */
    private fun generateShareCode(groupId: String, folderId: String): ShareCode {
        val code = "TB-${groupId.take(8).uppercase()}"
        val deepLink = "troubashare://join?group=$groupId&folder=$folderId"
        
        return ShareCode(
            code = code,
            deepLink = deepLink,
            groupId = groupId,
            folderId = folderId,
            expiresAt = null
        )
    }
    
    /**
     * Parse share code to extract group and folder info
     */
    private fun parseShareCode(shareCode: String): ShareCode {
        // For now, assume the share code contains the necessary info
        // In a real implementation, this would query a sharing service
        return ShareCode(
            code = shareCode,
            deepLink = "",
            groupId = "",
            folderId = "",
            expiresAt = null
        )
    }
    
    // Helper methods (simplified implementations)
    private fun createGroupManifest(group: Group): GroupManifest {
        return GroupManifest(
            groupId = group.id,
            name = group.name,
            createdBy = "current-device",
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            version = 1,
            appVersion = "1.0.0",
            members = group.members.map { member ->
                CloudMember(
                    memberId = member.id,
                    name = member.name,
                    role = member.role,
                    addedBy = "current-device",
                    addedAt = System.currentTimeMillis()
                )
            },
            permissions = CloudPermissions(
                adminDevices = listOf("current-device"),
                editorDevices = emptyList(),
                viewerDevices = emptyList(),
                readOnlyDevices = emptyList()
            ),
            syncSettings = CloudSyncSettings()
        )
    }
    
    private suspend fun createGroupFromManifest(manifest: GroupManifest): Group {
        // Create group from manifest data
        val group = Group(
            id = manifest.groupId,
            name = manifest.name,
            members = emptyList() // Will be populated separately
        )
        
        // Save to local database
        val memberNames = manifest.members.map { it.name }
        val createdGroup = groupRepository.createGroup(manifest.name, memberNames)
        
        return createdGroup
    }
    
    /**
     * Verify access to a cloud folder
     */
    private suspend fun verifyFolderAccess(folderId: String): Result<Unit> {
        return try {
            val result = googleDriveProvider.getFileInfo(folderId)
            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(CloudException.PermissionDenied("Cannot access folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Download change log from cloud
     */
    private suspend fun downloadChangeLog(folderId: String): List<ChangeLogEntry> {
        return try {
            // Find change log file in sync folder
            val syncFolderId = findSyncFolder(folderId)
            val filesResult = googleDriveProvider.listFiles(syncFolderId, CHANGE_LOG_FILE)
            
            if (filesResult.isFailure) return emptyList()
            
            val changeLogFile = filesResult.getOrThrow()
                .find { it.name == CHANGE_LOG_FILE }
                ?: return emptyList()
            
            // Download and parse change log
            val tempFile = File(context.cacheDir, "temp-$CHANGE_LOG_FILE")
            val downloadResult = googleDriveProvider.downloadFile(
                CloudFile(
                    id = changeLogFile.id,
                    name = changeLogFile.name,
                    path = "/$CHANGE_LOG_FILE",
                    size = changeLogFile.size,
                    mimeType = "application/json",
                    modifiedTime = changeLogFile.modifiedTime,
                    checksum = changeLogFile.checksum
                ),
                tempFile.absolutePath
            )
            
            if (downloadResult.isFailure) return emptyList()
            
            val changeLogJson = tempFile.readText()
            val changeLog = json.decodeFromString<ChangeLog>(changeLogJson)
            
            tempFile.delete()
            
            changeLog.changes
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download change log", e)
            emptyList()
        }
    }
    
    /**
     * Apply remote changes to local database
     */
    private suspend fun applyRemoteChanges(groupId: String, changes: List<ChangeLogEntry>) {
        try {
            changes.forEach { change ->
                when (change.entityType) {
                    EntityType.SONG -> applySongChange(change)
                    EntityType.SETLIST -> applySetlistChange(change)
                    EntityType.ANNOTATION -> applyAnnotationChange(change)
                    EntityType.MEMBER -> applyMemberChange(change)
                    EntityType.GROUP -> applyGroupChange(change)
                    else -> Log.w(TAG, "Unknown entity type: ${change.entityType}")
                }
                
                // Record the remote change locally
                changeTracker.applyRemoteChange(groupId, change)
            }
            
            Log.d(TAG, "Applied ${changes.size} remote changes")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply remote changes", e)
        }
    }
    
    /**
     * Upload local changes to cloud
     */
    private suspend fun uploadLocalChanges(groupId: String, changes: List<ChangeLogEntry>, folderId: String) {
        try {
            if (changes.isEmpty()) return
            
            // Update change log in cloud
            val existingChangeLog = downloadChangeLog(folderId)
            val updatedChangeLog = ChangeLog(
                changes = existingChangeLog + changes,
                lastChangeId = changes.lastOrNull()?.changeId,
                version = ((existingChangeLog.maxOfOrNull { it.timestamp } ?: 0) + 1).toInt()
            )
            
            // Upload updated change log
            val changeLogJson = json.encodeToString(updatedChangeLog)
            val tempFile = File(context.cacheDir, CHANGE_LOG_FILE)
            tempFile.writeText(changeLogJson)
            
            val syncFolderId = findSyncFolder(folderId)
            val uploadResult = googleDriveProvider.uploadFile(
                tempFile.absolutePath,
                CHANGE_LOG_FILE,
                syncFolderId
            )
            
            tempFile.delete()
            
            if (uploadResult.isSuccess) {
                // Mark changes as synced
                changeTracker.markChangesSynced(changes.map { it.changeId })
                Log.d(TAG, "Uploaded ${changes.size} local changes")
            } else {
                Log.e(TAG, "Failed to upload changes: ${uploadResult.exceptionOrNull()?.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload local changes", e)
        }
    }
    
    // Entity-specific change application methods
    private suspend fun applySongChange(change: ChangeLogEntry) {
        try {
            Log.d(TAG, "Applying song change: ${change.description}")

            when (change.changeType) {
                ChangeType.CREATE, ChangeType.UPDATE -> {
                    // Download song from cloud
                    val cloudSettings = getGroupCloudSettings(change.metadata["groupId"] ?: "") ?: return
                    val songsFolderId = findSubFolder(cloudSettings.folderId, "songs") ?: return

                    val downloadResult = downloadSongMetadata(change.entityId, songsFolderId, change.metadata["groupId"] ?: "")
                    if (downloadResult.isSuccess) {
                        val song = downloadResult.getOrThrow()
                        // Create or update the song in local database
                        songRepository.createSong(
                            groupId = song.groupId,
                            title = song.title,
                            artist = song.artist,
                            key = song.key,
                            tempo = song.tempo
                        )
                        Log.d(TAG, "Successfully applied song change: ${song.title}")
                    } else {
                        Log.w(TAG, "Failed to download song metadata for: ${change.entityId}")
                    }
                }

                ChangeType.DELETE -> {
                    // Delete song from local database
                    val song = songRepository.getSongById(change.entityId)
                    if (song != null) {
                        songRepository.deleteSong(song)
                        Log.d(TAG, "Successfully deleted song: ${change.entityName}")
                    } else {
                        Log.w(TAG, "Song not found for deletion: ${change.entityId}")
                    }
                }

                else -> Log.w(TAG, "Unsupported song change type: ${change.changeType}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply song change", e)
        }
    }

    private suspend fun applySetlistChange(change: ChangeLogEntry) {
        try {
            Log.d(TAG, "Applying setlist change: ${change.description}")

            when (change.changeType) {
                ChangeType.CREATE, ChangeType.UPDATE -> {
                    // Download setlist from cloud
                    val cloudSettings = getGroupCloudSettings(change.metadata["groupId"] ?: "") ?: return
                    val setlistsFolderId = findSubFolder(cloudSettings.folderId, "setlists") ?: return

                    // Download setlist JSON file
                    val filesResult = googleDriveProvider.listFiles(setlistsFolderId, "${change.entityId}.json")
                    if (filesResult.isSuccess) {
                        val setlistFile = filesResult.getOrThrow().find { it.name == "${change.entityId}.json" }
                        if (setlistFile != null) {
                            val tempFile = File(context.cacheDir, "temp-${setlistFile.name}")
                            val downloadResult = googleDriveProvider.downloadFile(
                                CloudFile(
                                    id = setlistFile.id,
                                    name = setlistFile.name,
                                    path = "/${setlistFile.name}",
                                    size = setlistFile.size,
                                    mimeType = "application/json",
                                    modifiedTime = setlistFile.modifiedTime,
                                    checksum = setlistFile.checksum
                                ),
                                tempFile.absolutePath
                            )

                            if (downloadResult.isSuccess) {
                                val setlistJson = tempFile.readText()
                                val setlistData = json.decodeFromString<CloudSetlistData>(setlistJson)

                                // Create or update setlist in local database
                                setlistRepository.createSetlist(
                                    name = setlistData.name,
                                    groupId = change.metadata["groupId"] ?: "",
                                    description = setlistData.description,
                                    venue = null,
                                    eventDate = null
                                )
                                tempFile.delete()
                                Log.d(TAG, "Successfully applied setlist change: ${setlistData.name}")
                            }
                        }
                    }
                }

                ChangeType.DELETE -> {
                    // Delete setlist from local database
                    val setlist = setlistRepository.getSetlistById(change.entityId)
                    if (setlist != null) {
                        setlistRepository.deleteSetlist(setlist)
                        Log.d(TAG, "Successfully deleted setlist: ${change.entityName}")
                    } else {
                        Log.w(TAG, "Setlist not found for deletion: ${change.entityId}")
                    }
                }

                else -> Log.w(TAG, "Unsupported setlist change type: ${change.changeType}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply setlist change", e)
        }
    }

    private suspend fun applyAnnotationChange(change: ChangeLogEntry) {
        try {
            Log.d(TAG, "Applying annotation change: ${change.description}")

            when (change.changeType) {
                ChangeType.CREATE, ChangeType.UPDATE -> {
                    // Download annotation from cloud
                    val cloudSettings = getGroupCloudSettings(change.metadata["groupId"] ?: "") ?: return
                    val songsFolderId = findSubFolder(cloudSettings.folderId, "songs") ?: return

                    val fileId = change.metadata["fileId"] ?: return
                    val memberId = change.memberId ?: return
                    val songId = change.metadata["songId"] ?: return

                    // Download annotation file
                    val annotationFileName = "${songId}_${memberId}_${fileId}_annotations.json"
                    val contentResult = googleDriveProvider.listFiles(songsFolderId, annotationFileName)

                    if (contentResult.isSuccess) {
                        val annotationFile = contentResult.getOrThrow().find { it.name == annotationFileName }
                        if (annotationFile != null) {
                            val downloadResult = googleDriveProvider.downloadFileContent(annotationFile.id)
                            if (downloadResult.isSuccess) {
                                val annotationContent = downloadResult.getOrThrow()
                                val annotationLayer = json.decodeFromString<CloudAnnotationLayer>(annotationContent)

                                // Create annotation in local database
                                val localAnnotation = annotationRepository.createAnnotation(
                                    fileId = fileId,
                                    memberId = memberId,
                                    pageNumber = annotationLayer.annotations.firstOrNull()?.pageNumber ?: 0
                                )

                                // Add all strokes from cloud
                                annotationLayer.annotations.forEach { cloudAnnotation ->
                                    val stroke = AnnotationStroke(
                                        id = cloudAnnotation.annotationId,
                                        points = cloudAnnotation.strokePath.split(",").mapNotNull { pointStr ->
                                            val coords = pointStr.split(":")
                                            if (coords.size == 2) {
                                                AnnotationPoint(
                                                    x = coords[0].toFloatOrNull() ?: 0f,
                                                    y = coords[1].toFloatOrNull() ?: 0f
                                                )
                                            } else null
                                        },
                                        color = cloudAnnotation.strokeColor.toLong(),
                                        strokeWidth = cloudAnnotation.strokeWidth,
                                        tool = DrawingTool.valueOf(cloudAnnotation.type.takeIf {
                                            DrawingTool.values().any { tool -> tool.name == it }
                                        } ?: "PEN"),
                                        text = cloudAnnotation.text.takeIf { it.isNotEmpty() },
                                        createdAt = cloudAnnotation.createdAt
                                    )
                                    annotationRepository.addStrokeToAnnotation(localAnnotation.id, stroke)
                                }

                                Log.d(TAG, "Successfully applied annotation change for file: $fileId")
                            }
                        }
                    }
                }

                ChangeType.DELETE -> {
                    // Delete annotation from local database
                    // Clear all annotations for this entity (file)
                    annotationRepository.clearAnnotationsForFile(change.entityId)
                    Log.d(TAG, "Successfully deleted annotations for: ${change.entityName}")
                }

                else -> Log.w(TAG, "Unsupported annotation change type: ${change.changeType}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply annotation change", e)
        }
    }

    private suspend fun applyMemberChange(change: ChangeLogEntry) {
        try {
            Log.d(TAG, "Applying member change: ${change.description}")

            when (change.changeType) {
                ChangeType.CREATE, ChangeType.UPDATE -> {
                    // Download group manifest to get member info
                    val cloudSettings = getGroupCloudSettings(change.metadata["groupId"] ?: "") ?: return
                    val manifestResult = downloadGroupManifest(cloudSettings.folderId)

                    if (manifestResult.isSuccess) {
                        val manifest = manifestResult.getOrThrow()
                        val groupId = change.metadata["groupId"] ?: ""
                        val group = groupRepository.getGroupById(groupId)

                        if (group != null) {
                            // Update group with new member list from manifest
                            val memberNames = manifest.members.map { it.name }
                            groupRepository.updateGroup(groupId, group.name, memberNames)
                            Log.d(TAG, "Successfully updated group members from manifest")
                        }
                    }
                }

                ChangeType.DELETE -> {
                    // Member deletion is handled by updating the group with the new manifest
                    Log.d(TAG, "Member deletion handled via group update: ${change.entityName}")
                }

                else -> Log.w(TAG, "Unsupported member change type: ${change.changeType}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply member change", e)
        }
    }

    private suspend fun applyGroupChange(change: ChangeLogEntry) {
        try {
            Log.d(TAG, "Applying group change: ${change.description}")

            when (change.changeType) {
                ChangeType.UPDATE -> {
                    // Download updated group manifest
                    val cloudSettings = getGroupCloudSettings(change.entityId) ?: return
                    val manifestResult = downloadGroupManifest(cloudSettings.folderId)

                    if (manifestResult.isSuccess) {
                        val manifest = manifestResult.getOrThrow()
                        // Update group name and members
                        val memberNames = manifest.members.map { it.name }
                        groupRepository.updateGroup(change.entityId, manifest.name, memberNames)
                        Log.d(TAG, "Successfully updated group: ${manifest.name}")
                    }
                }

                ChangeType.DELETE -> {
                    // Delete entire group from local database
                    val group = groupRepository.getGroupById(change.entityId)
                    if (group != null) {
                        groupRepository.deleteGroup(group)
                        Log.d(TAG, "Successfully deleted group: ${change.entityName}")
                    } else {
                        Log.w(TAG, "Group not found for deletion: ${change.entityId}")
                    }
                }

                else -> Log.w(TAG, "Unsupported group change type: ${change.changeType}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply group change", e)
        }
    }
    
    /**
     * Upload all local songs to cloud
     */
    private suspend fun uploadAllSongs(groupId: String, folderId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Uploading songs for group: $groupId")
            updateProgress("Loading songs...")
            
            // Get songs with their files included for cloud sync
            val songs = songRepository.getSongsWithFilesByGroupId(groupId)
            Log.d(TAG, "Collected ${songs.size} songs from repository")
            
            if (songs.isEmpty()) {
                updateProgress("No songs to sync")
            } else {
                updateProgress("Found ${songs.size} songs to sync")
            }
            
            Log.d(TAG, "Found ${songs.size} songs to upload")
            
            if (songs.isEmpty()) {
                Log.d(TAG, "No songs found in group - skipping song upload")
                return Result.success(Unit)
            }
            
            // Find songs folder
            val songsFolderId = findSubFolder(folderId, "songs")
            if (songsFolderId == null) {
                Log.e(TAG, "Songs folder not found")
                return Result.failure(Exception("Songs folder not found"))
            }
            
            var uploadedCount = 0
            for ((index, song) in songs.withIndex()) {
                updateProgress("Syncing song ${index + 1}/${songs.size}: '${song.title}'")
                Log.d(TAG, "Uploading song: ${song.title}")
                Log.d(TAG, "Song details - ID: ${song.id}, GroupID: ${song.groupId}")
                Log.d(TAG, "Total files for song: ${song.files.size}")
                song.files.forEach { file ->
                    Log.d(TAG, "  File: ${file.fileName}, Type: ${file.fileType}, Member: ${file.memberId}, Path: ${file.filePath}")
                }
                
                // Find all PDF files for this song (multiple members may have PDFs)
                val pdfFiles = song.files.filter { it.fileType == FileType.PDF }
                Log.d(TAG, "Found ${pdfFiles.size} PDF files for song: ${song.title}")
                
                if (pdfFiles.isNotEmpty()) {
                    var songUploadedSuccessfully = false
                    
                    // Upload each member's PDF file
                    pdfFiles.forEachIndexed { index, pdfFile ->
                        Log.d(TAG, "Uploading PDF ${index + 1}/${pdfFiles.size} for ${song.title} (member: ${pdfFile.memberId})")
                        
                        val file = java.io.File(pdfFile.filePath)
                        if (file.exists()) {
                            // Create unique filename for each member's PDF
                            val remoteName = "${song.id}_${pdfFile.memberId}.pdf"

                            // Check if PDF needs upload (using checksum comparison)
                            if (needsUpload(file, remoteName, songsFolderId)) {
                                Log.d(TAG, "Uploading PDF ${remoteName} (changed or new)")

                                // Upload with retry logic
                                val uploadResult = retryWithBackoff(
                                    operation = "Upload PDF: $remoteName"
                                ) {
                                    googleDriveProvider.uploadFile(
                                        localPath = pdfFile.filePath,
                                        remotePath = remoteName,
                                        parentFolderId = songsFolderId
                                    )
                                }

                                if (uploadResult.isSuccess) {
                                    Log.d(TAG, "Successfully uploaded PDF for ${song.title} (member: ${pdfFile.memberId})")
                                    songUploadedSuccessfully = true
                                } else {
                                    Log.e(TAG, "Failed to upload PDF for ${song.title} (member: ${pdfFile.memberId}) - ${uploadResult.exceptionOrNull()?.message}")
                                }
                            } else {
                                Log.d(TAG, "PDF ${remoteName} unchanged, skipping upload")
                                songUploadedSuccessfully = true
                            }
                        } else {
                            Log.w(TAG, "PDF file not found: ${pdfFile.filePath} for member: ${pdfFile.memberId}")
                        }
                    }
                    
                    if (songUploadedSuccessfully) {
                        uploadedCount++
                        
                        // Upload song metadata once per song
                        updateProgress("Uploading metadata for '${song.title}'")
                        val metadataResult = uploadSongMetadata(song, songsFolderId)
                        if (metadataResult.isFailure) {
                            Log.w(TAG, "Failed to upload metadata for: ${song.title}")
                        }
                        
                        // Upload annotations if any exist
                        updateProgress("Checking annotations for '${song.title}'")
                        val annotationsResult = uploadSongAnnotations(song, songsFolderId)
                        if (annotationsResult.isFailure) {
                            Log.w(TAG, "Failed to upload annotations for: ${song.title}")
                        }
                    }
                } else {
                    Log.w(TAG, "No PDF file found for song: ${song.title}")
                }
            }
            
            Log.d(TAG, "Upload completed: $uploadedCount/${songs.size} songs uploaded")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload songs", e)
            Result.failure(e)
        }
    }
    
    /**
     * Download all songs from cloud
     */
    private suspend fun downloadAllSongs(groupId: String, folderId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Downloading songs for group: $groupId")
            
            // Find songs folder
            val songsFolderId = findSubFolder(folderId, "songs")
            if (songsFolderId == null) {
                Log.e(TAG, "Songs folder not found")
                return Result.failure(Exception("Songs folder not found"))
            }
            
            // List all files in songs folder
            val filesResult = googleDriveProvider.listFiles(songsFolderId)
            if (filesResult.isFailure) {
                Log.e(TAG, "Failed to list files in songs folder")
                return Result.failure(filesResult.exceptionOrNull()!!)
            }
            
            val files = filesResult.getOrThrow()
            val pdfFiles = files.filter { it.name.endsWith(".pdf") }
            Log.d(TAG, "Found ${pdfFiles.size} PDF files to download")
            
            var downloadedCount = 0
            pdfFiles.forEach { file ->
                try {
                    val songId = file.name.substringBeforeLast(".pdf")
                    Log.d(TAG, "Downloading song: ${file.name}")
                    
                    // Check if song already exists locally
                    val existingSong = songRepository.getSongById(songId)
                    if (existingSong != null) {
                        Log.d(TAG, "Song already exists locally: ${file.name}")
                        return@forEach
                    }
                    
                    // Create local file path
                    val songsDir = java.io.File(context.getExternalFilesDir(null), "songs")
                    if (!songsDir.exists()) songsDir.mkdirs()
                    val localFilePath = java.io.File(songsDir, file.name).absolutePath
                    
                    // Download PDF file
                    val downloadResult = googleDriveProvider.downloadFile(
                        CloudFile(
                            id = file.id,
                            name = file.name,
                            path = "/${file.name}",
                            size = file.size,
                            mimeType = "application/pdf",
                            modifiedTime = file.modifiedTime,
                            checksum = file.checksum
                        ),
                        localFilePath
                    )
                    
                    if (downloadResult.isSuccess) {
                        Log.d(TAG, "Successfully downloaded: ${file.name}")
                        
                        // Download metadata and create song entry
                        val metadataResult = downloadSongMetadata(songId, songsFolderId, groupId)
                        if (metadataResult.isSuccess) {
                            val song = metadataResult.getOrThrow()
                            // Create song with PDF file
                            val songFile = SongFile(
                                id = "${songId}_pdf",
                                songId = songId,
                                memberId = "synced_user",
                                filePath = localFilePath,
                                fileType = FileType.PDF,
                                fileName = file.name
                            )
                            val updatedSong = song.copy(files = listOf(songFile))
                            songRepository.createSong(
                                groupId = song.groupId,
                                title = song.title,
                                artist = song.artist,
                                key = song.key,
                                tempo = song.tempo
                            )
                            downloadedCount++
                            
                            // Download annotations
                            downloadSongAnnotations(songId, songsFolderId)
                        } else {
                            Log.w(TAG, "Failed to download metadata for: ${file.name}")
                        }
                    } else {
                        Log.e(TAG, "Failed to download: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading song: ${file.name}", e)
                }
            }
            
            Log.d(TAG, "Download completed: $downloadedCount songs downloaded")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download songs", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload all local setlists to cloud
     */
    private suspend fun uploadAllSetlists(groupId: String, folderId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Uploading setlists for group: $groupId")
            updateProgress("Loading setlists...")
            val setlistsFlow = setlistRepository.getSetlistsByGroupId(groupId)
            val setlists = mutableListOf<Setlist>()
            
            // Get current setlists from Flow
            val setlistList = setlistsFlow.first()
            setlists.addAll(setlistList)
            Log.d(TAG, "Collected ${setlistList.size} setlists from repository")
            
            if (setlists.isEmpty()) {
                updateProgress("No setlists to sync")
            } else {
                updateProgress("Found ${setlists.size} setlists to sync")
            }
            Log.d(TAG, "Found ${setlists.size} setlists to upload")
            
            // Find setlists folder
            val setlistsFolderId = findSubFolder(folderId, "setlists")
            if (setlistsFolderId == null) {
                Log.e(TAG, "Setlists folder not found")
                return Result.failure(Exception("Setlists folder not found"))
            }
            
            var uploadedCount = 0
            for ((index, setlist) in setlists.withIndex()) {
                updateProgress("Syncing setlist ${index + 1}/${setlists.size}: '${setlist.name}'")
                Log.d(TAG, "Uploading setlist: ${setlist.name}")
                
                try {
                    val setlistFileName = "${setlist.id}.json"
                    
                    // Check if setlist already exists in cloud
                    val existingFiles = googleDriveProvider.listFiles(setlistsFolderId).getOrNull()
                    val existingSetlist = existingFiles?.find { it.name == setlistFileName }
                    
                    if (existingSetlist != null) {
                        Log.d(TAG, "Setlist ${setlist.name} already exists in cloud, skipping upload")
                        uploadedCount++
                        continue
                    }
                    
                    // Create setlist data structure
                    val setlistData = createSetlistCloudData(setlist)
                    val setlistJson = json.encodeToString(setlistData)
                    
                    // Write to temporary file
                    val tempFile = java.io.File(context.cacheDir, setlistFileName)
                    tempFile.writeText(setlistJson)
                    
                    // Upload to cloud
                    val uploadResult = googleDriveProvider.uploadFile(
                        tempFile.absolutePath,
                        setlistFileName,
                        setlistsFolderId
                    )
                    
                    // Clean up temp file
                    tempFile.delete()
                    
                    if (uploadResult.isSuccess) {
                        Log.d(TAG, "Successfully uploaded setlist: ${setlist.name}")
                        uploadedCount++
                    } else {
                        Log.e(TAG, "Failed to upload setlist: ${setlist.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading setlist: ${setlist.name}", e)
                }
            }
            
            Log.d(TAG, "Setlist upload completed: $uploadedCount/${setlists.size} uploaded")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload setlists", e)
            Result.failure(e)
        }
    }
    
    /**
     * Download all setlists from cloud
     */
    private suspend fun downloadAllSetlists(groupId: String, folderId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Downloading setlists for group: $groupId")
            
            // Find setlists folder
            val setlistsFolderId = findSubFolder(folderId, "setlists")
            if (setlistsFolderId == null) {
                Log.e(TAG, "Setlists folder not found")
                return Result.failure(Exception("Setlists folder not found"))
            }
            
            // List all files in setlists folder
            val filesResult = googleDriveProvider.listFiles(setlistsFolderId)
            if (filesResult.isFailure) {
                Log.e(TAG, "Failed to list setlist files")
                return Result.failure(filesResult.exceptionOrNull()!!)
            }
            
            val files = filesResult.getOrThrow()
            val jsonFiles = files.filter { it.name.endsWith(".json") }
            Log.d(TAG, "Found ${jsonFiles.size} setlist files to download")
            
            var downloadedCount = 0
            jsonFiles.forEach { file ->
                try {
                    val setlistId = file.name.substringBeforeLast(".json")
                    Log.d(TAG, "Downloading setlist: ${file.name}")
                    
                    // Check if setlist already exists locally
                    val existingSetlist = setlistRepository.getSetlistById(setlistId)
                    if (existingSetlist != null) {
                        Log.d(TAG, "Setlist already exists locally: ${file.name}")
                        return@forEach
                    }
                    
                    // Download to temporary file
                    val tempFile = java.io.File(context.cacheDir, "temp-${file.name}")
                    val downloadResult = googleDriveProvider.downloadFile(
                        CloudFile(
                            id = file.id,
                            name = file.name,
                            path = "/${file.name}",
                            size = file.size,
                            mimeType = "application/json",
                            modifiedTime = file.modifiedTime,
                            checksum = file.checksum
                        ),
                        tempFile.absolutePath
                    )
                    
                    if (downloadResult.isSuccess) {
                        // Parse setlist data and create local entry
                        val setlistJson = tempFile.readText()
                        val setlistData = json.decodeFromString<CloudSetlistData>(setlistJson)
                        
                        // Create setlist from cloud data
                        val setlist = createSetlistFromCloudData(setlistData, groupId)
                        setlistRepository.createSetlist(
                            name = setlist.name,
                            groupId = groupId,
                            description = setlist.description,
                            venue = setlist.venue,
                            eventDate = setlist.eventDate
                        )
                        
                        Log.d(TAG, "Successfully downloaded setlist: ${setlistData.name}")
                        downloadedCount++
                    } else {
                        Log.e(TAG, "Failed to download setlist: ${file.name}")
                    }
                    
                    // Clean up temp file
                    tempFile.delete()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading setlist: ${file.name}", e)
                }
            }
            
            Log.d(TAG, "Setlist download completed: $downloadedCount setlists downloaded")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download setlists", e)
            Result.failure(e)
        }
    }
    
    
    // Helper methods for folder management
    private suspend fun findSubFolder(parentFolderId: String, folderName: String): String? {
        return try {
            val filesResult = googleDriveProvider.listFiles(parentFolderId)
            if (filesResult.isFailure) return null
            
            val folder = filesResult.getOrThrow()
                .find { it.name == folderName }
            
            folder?.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find subfolder: $folderName", e)
            null
        }
    }
    
    private suspend fun findSyncFolder(groupFolderId: String): String {
        return findSubFolder(groupFolderId, "sync") ?: groupFolderId
    }
    
    // Song metadata handling
    private suspend fun uploadSongMetadata(song: Song, songsFolderId: String): Result<Unit> {
        return try {
            val metadata = CloudSongMetadata(
                songId = song.id,
                title = song.title,
                artist = song.artist ?: "",
                album = "",
                duration = 0,
                bpm = song.tempo ?: 0,
                key = song.key ?: "",
                timeSignature = "",
                genre = "",
                tags = song.tags,
                createdAt = song.createdAt,
                lastModified = song.updatedAt,
                checksum = ""
            )
            
            val metadataJson = json.encodeToString(metadata)
            val tempFile = java.io.File(context.cacheDir, "${song.id}-metadata.json")
            tempFile.writeText(metadataJson)
            
            val uploadResult = googleDriveProvider.uploadFile(
                tempFile.absolutePath,
                "${song.id}-metadata.json",
                songsFolderId
            )
            
            tempFile.delete()
            
            if (uploadResult.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(uploadResult.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun downloadSongMetadata(songId: String, songsFolderId: String, groupId: String): Result<Song> {
        return try {
            // Find metadata file
            val filesResult = googleDriveProvider.listFiles(songsFolderId, "${songId}-metadata.json")
            if (filesResult.isFailure) {
                return Result.failure(Exception("Metadata file not found"))
            }
            
            val metadataFile = filesResult.getOrThrow()
                .find { it.name == "${songId}-metadata.json" }
                ?: return Result.failure(Exception("Metadata file not found"))
            
            // Download metadata
            val tempFile = java.io.File(context.cacheDir, "temp-${metadataFile.name}")
            val downloadResult = googleDriveProvider.downloadFile(
                CloudFile(
                    id = metadataFile.id,
                    name = metadataFile.name,
                    path = "/${metadataFile.name}",
                    size = metadataFile.size,
                    mimeType = "application/json",
                    modifiedTime = metadataFile.modifiedTime,
                    checksum = metadataFile.checksum
                ),
                tempFile.absolutePath
            )
            
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull()!!)
            }
            
            val metadataJson = tempFile.readText()
            val metadata = json.decodeFromString<CloudSongMetadata>(metadataJson)
            tempFile.delete()
            
            // Create song from metadata
            val song = Song(
                id = metadata.songId,
                groupId = groupId,
                title = metadata.title,
                artist = metadata.artist,
                key = metadata.key,
                tempo = metadata.bpm,
                tags = metadata.tags,
                notes = null,
                files = emptyList(),
                createdAt = metadata.createdAt,
                updatedAt = metadata.lastModified
            )
            
            Result.success(song)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Annotation handling - simplified for now
    private suspend fun uploadSongAnnotations(song: Song, songsFolderId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Uploading annotations for song: ${song.title}")
            
            // Get all annotations for all PDF files of this song
            val annotations = mutableListOf<com.troubashare.domain.model.Annotation>()
            song.files.filter { it.fileType == FileType.PDF }.forEach { pdfFile ->
                // Get annotations for this specific member and file
                val fileAnnotations = annotationRepository.getAnnotationsByFileAndMember(pdfFile.id, pdfFile.memberId).first()
                annotations.addAll(fileAnnotations)
                Log.d(TAG, "Found ${fileAnnotations.size} annotations for PDF: ${pdfFile.fileName} (member: ${pdfFile.memberId})")
            }
            
            if (annotations.isEmpty()) {
                Log.d(TAG, "No annotations found for song: ${song.title}")
                return Result.success(Unit)
            }
            
            // Group annotations by member and file
            val annotationsByMemberAndFile = annotations.groupBy { "${it.memberId}_${it.fileId}" }
            
            var uploadedCount = 0
            annotationsByMemberAndFile.forEach { (memberFileKey, memberAnnotations) ->
                val memberId = memberFileKey.split("_")[0]
                val fileId = memberFileKey.split("_").drop(1).joinToString("_")
                
                Log.d(TAG, "Uploading ${memberAnnotations.size} annotations for member: $memberId, file: $fileId")
                
                // Convert annotations to cloud format
                val cloudAnnotations = memberAnnotations.map { annotation ->
                    // Convert strokes to cloud format
                    annotation.strokes.map { stroke ->
                        CloudAnnotation(
                            annotationId = stroke.id,
                            type = stroke.tool.name,
                            pageNumber = annotation.pageNumber,
                            x = stroke.points.minOfOrNull { it.x } ?: 0f,
                            y = stroke.points.minOfOrNull { it.y } ?: 0f,
                            width = (stroke.points.maxOfOrNull { it.x } ?: 0f) - (stroke.points.minOfOrNull { it.x } ?: 0f),
                            height = (stroke.points.maxOfOrNull { it.y } ?: 0f) - (stroke.points.minOfOrNull { it.y } ?: 0f),
                            strokePath = stroke.points.joinToString(",") { "${it.x}:${it.y}" },
                            strokeWidth = stroke.strokeWidth,
                            strokeColor = stroke.color.toInt(),
                            fillColor = null,
                            text = stroke.text ?: "",
                            textSize = 16f,
                            createdAt = stroke.createdAt
                        )
                    }
                }.flatten()
                
                if (cloudAnnotations.isNotEmpty()) {
                    val annotationFileName = "${song.id}_${memberId}_${fileId}_annotations.json"
                    
                    // Check if annotation file already exists in cloud
                    val existingFiles = googleDriveProvider.listFiles(songsFolderId).getOrNull()
                    val existingAnnotation = existingFiles?.find { it.name == annotationFileName }
                    
                    if (existingAnnotation != null) {
                        Log.d(TAG, "Annotation file ${annotationFileName} already exists in cloud, skipping upload")
                        uploadedCount++
                    } else {
                        // Create annotation layer for this member and file
                        val annotationLayer = CloudAnnotationLayer(
                            layerId = "${song.id}_${memberId}_${fileId}",
                            name = "Annotations for ${song.title} (Member: $memberId)",
                            isVisible = true,
                            createdAt = memberAnnotations.minOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                            lastModified = memberAnnotations.maxOfOrNull { it.updatedAt } ?: System.currentTimeMillis(),
                            annotations = cloudAnnotations
                        )
                        
                        // Serialize and upload
                        val annotationJson = Json.encodeToString(annotationLayer)
                        
                        val uploadResult = googleDriveProvider.uploadFileContent(
                            content = annotationJson,
                            fileName = annotationFileName,
                            parentFolderId = songsFolderId,
                            mimeType = "application/json"
                        )
                        
                        if (uploadResult.isSuccess) {
                            Log.d(TAG, "Successfully uploaded annotations for member: $memberId, file: $fileId")
                            uploadedCount++
                        } else {
                            Log.e(TAG, "Failed to upload annotations for member: $memberId, file: $fileId - ${uploadResult.exceptionOrNull()?.message}")
                        }
                    }
                }
            }
            
            Log.d(TAG, "Annotation upload completed: $uploadedCount annotation layers uploaded for song: ${song.title}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload annotations for song: ${song.title}", e)
            Result.failure(e)
        }
    }
    
    private suspend fun downloadSongAnnotations(songId: String, songsFolderId: String) {
        // This is called from downloadAllAnnotations, so just log here
        Log.d(TAG, "Annotation download for song $songId handled in downloadAllAnnotations")
    }
    
    // Setlist data conversion
    private suspend fun createSetlistCloudData(setlist: Setlist): CloudSetlistData {
        return CloudSetlistData(
            setlistId = setlist.id,
            name = setlist.name,
            description = setlist.description ?: "",
            createdAt = setlist.createdAt,
            lastModified = setlist.updatedAt,
            songs = setlist.items.map { setlistItem ->
                CloudSetlistSong(
                    songId = setlistItem.song.id,
                    order = setlistItem.position,
                    notes = setlistItem.notes ?: ""
                )
            }
        )
    }
    
    private suspend fun createSetlistFromCloudData(cloudData: CloudSetlistData, groupId: String): Setlist {
        return Setlist(
            id = cloudData.setlistId,
            groupId = groupId,
            name = cloudData.name,
            description = cloudData.description,
            venue = null,
            eventDate = null,
            items = emptyList(), // Will be populated separately
            createdAt = cloudData.createdAt,
            updatedAt = cloudData.lastModified
        )
    }
    
    // Annotation data conversion - simplified for now
    private suspend fun downloadAllAnnotations(groupId: String, folderId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Downloading annotations for group: $groupId")
            
            // Find songs folder
            val songsFolderId = findSubFolder(folderId, "songs")
            if (songsFolderId == null) {
                Log.e(TAG, "Songs folder not found for annotation download")
                return Result.failure(Exception("Songs folder not found"))
            }
            
            // List all annotation files in songs folder (files ending with _annotations.json)
            val annotationFiles = googleDriveProvider.listFiles(songsFolderId).getOrNull()
                ?.filter { it.name.endsWith("_annotations.json") }
            
            if (annotationFiles.isNullOrEmpty()) {
                Log.d(TAG, "No annotation files found in group: $groupId")
                return Result.success(Unit)
            }
            
            Log.d(TAG, "Found ${annotationFiles.size} annotation files to download")
            
            var downloadedCount = 0
            annotationFiles.forEach { annotationFile ->
                try {
                    Log.d(TAG, "Downloading annotation file: ${annotationFile.name}")
                    
                    // Download annotation content
                    val contentResult = googleDriveProvider.downloadFileContent(annotationFile.id)
                    if (contentResult.isFailure) {
                        Log.e(TAG, "Failed to download annotation content: ${annotationFile.name}")
                        return@forEach
                    }
                    
                    val annotationContent = contentResult.getOrThrow()
                    val annotationLayer = Json.decodeFromString<CloudAnnotationLayer>(annotationContent)
                    
                    // Parse filename to extract song ID, member ID, and file ID
                    // Format: songId_memberId_fileId_annotations.json
                    val fileNameParts = annotationFile.name.removeSuffix("_annotations.json").split("_")
                    if (fileNameParts.size < 3) {
                        Log.w(TAG, "Invalid annotation filename format: ${annotationFile.name}")
                        return@forEach
                    }
                    
                    val songId = fileNameParts[0]
                    val memberId = fileNameParts[1]
                    val fileId = fileNameParts.drop(2).joinToString("_")
                    
                    Log.d(TAG, "Processing annotations for song: $songId, member: $memberId, file: $fileId")
                    
                    // Convert cloud annotations back to local format
                    val localAnnotations = annotationLayer.annotations.map { cloudAnnotation ->
                        com.troubashare.domain.model.Annotation(
                            id = "${cloudAnnotation.annotationId}_local_${System.currentTimeMillis()}",
                            fileId = fileId,
                            memberId = memberId,
                            pageNumber = cloudAnnotation.pageNumber,
                            strokes = listOf(
                                com.troubashare.domain.model.AnnotationStroke(
                                    id = cloudAnnotation.annotationId,
                                    points = cloudAnnotation.strokePath.split(",").mapNotNull { pointStr ->
                                        val coords = pointStr.split(":")
                                        if (coords.size == 2) {
                                            com.troubashare.domain.model.AnnotationPoint(
                                                x = coords[0].toFloatOrNull() ?: 0f,
                                                y = coords[1].toFloatOrNull() ?: 0f
                                            )
                                        } else null
                                    },
                                    color = cloudAnnotation.strokeColor.toLong(),
                                    strokeWidth = cloudAnnotation.strokeWidth,
                                    tool = com.troubashare.domain.model.DrawingTool.valueOf(
                                        cloudAnnotation.type.takeIf { 
                                            com.troubashare.domain.model.DrawingTool.values().any { tool -> tool.name == it } 
                                        } ?: "PEN"
                                    ),
                                    text = cloudAnnotation.text.takeIf { it.isNotEmpty() },
                                    createdAt = cloudAnnotation.createdAt
                                )
                            ),
                            createdAt = annotationLayer.createdAt,
                            updatedAt = annotationLayer.lastModified
                        )
                    }
                    
                    // Save annotations to local database
                    localAnnotations.forEach { annotation ->
                        try {
                            // Create the annotation first
                            val createdAnnotation = annotationRepository.createAnnotation(
                                fileId = annotation.fileId,
                                memberId = annotation.memberId,
                                pageNumber = annotation.pageNumber
                            )
                            
                            // Add all strokes to the annotation
                            annotation.strokes.forEach { stroke ->
                                annotationRepository.addStrokeToAnnotation(createdAnnotation.id, stroke)
                            }
                            
                            Log.d(TAG, "Saved annotation for file: $fileId")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to save annotation: ${e.message}")
                        }
                    }
                    
                    downloadedCount++
                    Log.d(TAG, "Successfully processed annotation file: ${annotationFile.name}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process annotation file: ${annotationFile.name}", e)
                }
            }
            
            Log.d(TAG, "Annotation download completed: $downloadedCount/${annotationFiles.size} files processed")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download annotations", e)
            Result.failure(e)
        }
    }
    
    // Settings management
    private fun saveGroupCloudSettings(groupId: String, folderId: String, shareCode: ShareCode) {
        try {
            val prefs = context.getSharedPreferences("cloud_sync_settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("${groupId}_folder_id", folderId)
                putString("${groupId}_share_code", shareCode.code)
                putString("${groupId}_share_deep_link", shareCode.deepLink)
                putBoolean("${groupId}_is_admin", true) // First sync means we're the admin
                apply()
            }
            Log.d(TAG, "Saved cloud settings for group: $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cloud settings", e)
        }
    }
    
    private fun getGroupCloudSettings(groupId: String): GroupCloudSettings? {
        return try {
            val prefs = context.getSharedPreferences("cloud_sync_settings", Context.MODE_PRIVATE)
            val folderId = prefs.getString("${groupId}_folder_id", null)
            val shareCodeStr = prefs.getString("${groupId}_share_code", null)
            val deepLink = prefs.getString("${groupId}_share_deep_link", null)
            val isAdmin = prefs.getBoolean("${groupId}_is_admin", false)
            
            if (folderId != null && shareCodeStr != null) {
                val shareCode = ShareCode(
                    code = shareCodeStr,
                    deepLink = deepLink ?: "",
                    groupId = groupId,
                    folderId = folderId,
                    expiresAt = null
                )
                GroupCloudSettings(groupId, folderId, shareCode, isAdmin)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cloud settings", e)
            null
        }
    }
    
    private fun updateLastSyncTimestamp(groupId: String) {
        try {
            val prefs = context.getSharedPreferences("cloud_sync_settings", Context.MODE_PRIVATE)
            prefs.edit().putLong("${groupId}_last_sync", System.currentTimeMillis()).apply()
            Log.d(TAG, "Updated last sync timestamp for group: $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sync timestamp", e)
        }
    }
    
    private fun getLastSyncTimestamp(groupId: String): Long {
        return try {
            val prefs = context.getSharedPreferences("cloud_sync_settings", Context.MODE_PRIVATE)
            prefs.getLong("${groupId}_last_sync", 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sync timestamp", e)
            0L
        }
    }
    
    /**
     * Clear cloud settings for a group (force fresh sync)
     */
    fun clearGroupCloudSettings(groupId: String) {
        try {
            val prefs = context.getSharedPreferences("cloud_sync_settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                remove("${groupId}_folder_id")
                remove("${groupId}_share_code")
                remove("${groupId}_share_deep_link")
                remove("${groupId}_is_admin")
                remove("${groupId}_last_sync")
                apply()
            }
            Log.d(TAG, "Cleared cloud settings for group: $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cloud settings", e)
        }
    }
    
    /**
     * Check if connected to cloud storage
     */
    suspend fun checkConnection(): Boolean {
        return try {
            val authResult = googleDriveProvider.authenticate()
            val connected = authResult.isSuccess
            _isConnected.value = connected
            connected
        } catch (e: Exception) {
            _isConnected.value = false
            false
        }
    }
    
    /**
     * Disconnect from cloud storage
     */
    suspend fun disconnect() {
        try {
            syncJob?.cancel()
            _isConnected.value = false
            _syncStatus.value = SyncStatus.OFFLINE
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }
    
    /**
     * Sync a specific group
     */
    suspend fun syncGroup(groupId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Starting sync for group: $groupId")
            updateProgress("Starting sync...")
            _syncStatus.value = SyncStatus.SYNCING
            
            // Check connection first
            updateProgress("Verifying Google Drive connection...")
            if (!checkConnection()) {
                Log.e(TAG, "Sync failed: Not authenticated")
                updateProgress("Authentication failed")
                _syncStatus.value = SyncStatus.AUTHENTICATION_REQUIRED
                return Result.failure(Exception("Not authenticated"))
            }
            
            // Get the group from local database
            updateProgress("Loading group data...")
            val group = groupRepository.getGroupById(groupId)
            if (group == null) {
                Log.e(TAG, "Sync failed: Group not found locally")
                updateProgress("Group not found")
                _syncStatus.value = SyncStatus.ERROR
                return Result.failure(Exception("Group not found"))
            }
            
            Log.d(TAG, "Syncing group: ${group.name}")
            updateProgress("Syncing '${group.name}'...")
            
            // For now, always perform full sync to ensure content is uploaded
            // TODO: Implement proper delta sync detection
            Log.d(TAG, "Performing full content sync")
            
            // Check if we have cloud settings but force upload anyway for testing
            val existingSettings = getGroupCloudSettings(groupId)
            
            if (existingSettings == null) {
                // First time sync - create cloud folder structure
                Log.d(TAG, "First time sync - creating cloud folder structure")
                val folderResult = createGroupFolderStructure(group)
                
                if (folderResult.isFailure) {
                    Log.e(TAG, "Failed to create folder structure: ${folderResult.exceptionOrNull()?.message}")
                    _syncStatus.value = SyncStatus.ERROR
                    return Result.failure(folderResult.exceptionOrNull()!!)
                }
                
                val folderId = folderResult.getOrThrow()
                Log.d(TAG, "Created cloud folder with ID: $folderId")
                
                // Upload group manifest
                val manifestResult = uploadGroupManifest(group, folderId)
                if (manifestResult.isFailure) {
                    Log.e(TAG, "Failed to upload manifest: ${manifestResult.exceptionOrNull()?.message}")
                    _syncStatus.value = SyncStatus.ERROR
                    return Result.failure(manifestResult.exceptionOrNull()!!)
                }
                
                Log.d(TAG, "Successfully uploaded group manifest")
                
                // Generate share code and save settings
                val shareCode = generateShareCode(groupId, folderId)
                saveGroupCloudSettings(groupId, folderId, shareCode)
                
                Log.d(TAG, "Generated share code: ${shareCode.code}")
            } else {
                // Use existing settings but verify folder still exists
                Log.d(TAG, "Using existing cloud settings, verifying folder exists")
                val folderId = existingSettings.folderId
                
                // Test if the folder still exists by trying to get its metadata
                Log.d(TAG, "Testing folder validity for ID: $folderId")
                val folderTestResult = googleDriveProvider.getFileInfo(folderId)
                if (folderTestResult.isFailure) {
                    Log.w(TAG, "Folder validation failed: ${folderTestResult.exceptionOrNull()?.message}")
                    Log.w(TAG, "Stored folder no longer exists, clearing settings and starting fresh")
                    clearGroupCloudSettings(groupId)
                    
                    // Restart sync with fresh folder creation
                    Log.d(TAG, "Creating fresh cloud folder structure")
                    val folderResult = createGroupFolderStructure(group)
                    
                    if (folderResult.isFailure) {
                        Log.e(TAG, "Failed to create folder structure: ${folderResult.exceptionOrNull()?.message}")
                        _syncStatus.value = SyncStatus.ERROR
                        return Result.failure(folderResult.exceptionOrNull()!!)
                    }
                    
                    val newFolderId = folderResult.getOrThrow()
                    Log.d(TAG, "Created fresh cloud folder with ID: $newFolderId")
                    
                    // Upload group manifest
                    val manifestResult = uploadGroupManifest(group, newFolderId)
                    if (manifestResult.isFailure) {
                        Log.e(TAG, "Failed to upload manifest: ${manifestResult.exceptionOrNull()?.message}")
                        _syncStatus.value = SyncStatus.ERROR
                        return Result.failure(manifestResult.exceptionOrNull()!!)
                    }
                    
                    Log.d(TAG, "Successfully uploaded group manifest")
                    
                    // Upload content to fresh folder
                    val songsResult = uploadAllSongs(groupId, newFolderId)
                    if (songsResult.isFailure) {
                        Log.w(TAG, "Songs upload had issues: ${songsResult.exceptionOrNull()?.message}")
                    }
                    
                    val setlistsResult = uploadAllSetlists(groupId, newFolderId)
                    if (setlistsResult.isFailure) {
                        Log.w(TAG, "Setlists upload had issues: ${setlistsResult.exceptionOrNull()?.message}")
                    }
                    
                    // Generate new share code and save settings
                    val shareCode = generateShareCode(groupId, newFolderId)
                    saveGroupCloudSettings(groupId, newFolderId, shareCode)
                    
                    Log.d(TAG, "Generated fresh share code: ${shareCode.code}")
                } else {
                    // Folder exists, ensure subfolders exist
                    Log.d(TAG, "Folder validation passed, ensuring subfolder structure")
                    val folders = listOf("songs", "setlists", "sync")
                    folders.forEach { folderName ->
                        val existingFolder = findSubFolder(folderId, folderName)
                        if (existingFolder == null) {
                            Log.d(TAG, "Creating missing subfolder: $folderName")
                            val folderResult = googleDriveProvider.createFolder(folderName, folderId)
                            if (folderResult.isFailure) {
                                Log.e(TAG, "Failed to create subfolder: $folderName")
                            }
                        } else {
                            Log.d(TAG, "Subfolder already exists: $folderName")
                        }
                    }
                    
                    // Upload content using existing folder
                    val songsResult = uploadAllSongs(groupId, folderId)
                    if (songsResult.isFailure) {
                        Log.w(TAG, "Songs upload had issues: ${songsResult.exceptionOrNull()?.message}")
                    }
                    
                    val setlistsResult = uploadAllSetlists(groupId, folderId)
                    if (setlistsResult.isFailure) {
                        Log.w(TAG, "Setlists upload had issues: ${setlistsResult.exceptionOrNull()?.message}")
                    }
                    
                    Log.d(TAG, "Content upload completed using existing valid settings")
                }
            }
            
            // Update sync timestamp
            updateLastSyncTimestamp(groupId)
            
            _syncStatus.value = SyncStatus.UP_TO_DATE
            updateProgress("Sync completed successfully! ✅")
            Log.d(TAG, "Sync completed successfully for group: ${group.name}")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            _syncStatus.value = SyncStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Join a group using a share code
     */
    suspend fun joinGroup(shareCode: String): Result<Group> {
        return try {
            // Check connection first
            if (!checkConnection()) {
                return Result.failure(Exception("Not authenticated with Google Drive"))
            }
            
            // TODO: Implement actual join logic
            // For now, return a mock result
            val group = Group(
                id = UUID.randomUUID().toString(),
                name = "Joined Group"
            )
            
            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    data class GroupCloudSettings(
        val groupId: String,
        val folderId: String,
        val shareCode: ShareCode,
        val isAdmin: Boolean
    )

    /**
     * Retry a suspend operation with exponential backoff
     */
    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
        initialDelay: Long = INITIAL_RETRY_DELAY_MS,
        operation: String,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay
        repeat(maxAttempts) { attempt ->
            val result = try {
                block()
            } catch (e: Exception) {
                Result.failure<T>(e)
            }

            if (result.isSuccess) {
                if (attempt > 0) {
                    Log.d(TAG, "Operation '$operation' succeeded after ${attempt + 1} attempts")
                }
                return result
            }

            if (attempt < maxAttempts - 1) {
                Log.w(TAG, "Attempt ${attempt + 1}/$maxAttempts failed for '$operation': ${result.exceptionOrNull()?.message}. Retrying in ${currentDelay}ms...")
                delay(currentDelay)
                currentDelay *= 2 // Exponential backoff
            } else {
                Log.e(TAG, "Operation '$operation' failed after $maxAttempts attempts: ${result.exceptionOrNull()?.message}")
                return result
            }
        }

        return Result.failure(Exception("Operation '$operation' failed after $maxAttempts attempts"))
    }

    /**
     * Calculate MD5 checksum for a file
     */
    private fun calculateMD5(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val fis = FileInputStream(file)
            val buffer = ByteArray(8192)
            var read: Int

            while (fis.read(buffer).also { read = it } > 0) {
                md.update(buffer, 0, read)
            }

            fis.close()

            // Convert byte array to hex string
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate MD5", e)
            ""
        }
    }

    /**
     * Check if a file needs to be uploaded by comparing checksums
     */
    private suspend fun needsUpload(
        localFile: File,
        remoteName: String,
        parentFolderId: String
    ): Boolean {
        return try {
            // Calculate local file checksum
            val localChecksum = calculateMD5(localFile)
            if (localChecksum.isEmpty()) {
                Log.w(TAG, "Could not calculate local checksum, assuming upload needed")
                return true
            }

            // Get remote file info
            val existingFiles = googleDriveProvider.listFiles(parentFolderId).getOrNull()
            val remoteFile = existingFiles?.find { it.name == remoteName }

            if (remoteFile == null) {
                // File doesn't exist remotely, needs upload
                return true
            }

            // Compare checksums
            val remoteChecksum = remoteFile.checksum
            if (remoteChecksum == null) {
                Log.w(TAG, "Remote file has no checksum, assuming upload needed")
                return true
            }

            val checksumsDifferent = localChecksum != remoteChecksum

            if (checksumsDifferent) {
                Log.d(TAG, "File $remoteName has changed (local: ${localChecksum.take(8)}..., remote: ${remoteChecksum.take(8)}...)")
            } else {
                Log.d(TAG, "File $remoteName unchanged (checksum: ${localChecksum.take(8)}...)")
            }

            checksumsDifferent

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check if upload needed", e)
            true // Assume upload needed on error
        }
    }
}

