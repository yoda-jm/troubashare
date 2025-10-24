package com.troubashare.data.cloud

import android.util.Log
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.domain.model.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class ConflictResolver @Inject constructor(
    private val database: TroubaShareDatabase
) {
    companion object {
        private const val TAG = "ConflictResolver"
        private val CONFLICT_WINDOW = 5.minutes.inWholeMilliseconds // 5 minutes
    }
    
    private val _conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val conflicts: StateFlow<List<SyncConflict>> = _conflicts.asStateFlow()
    
    /**
     * Detect conflicts between local and remote changes
     */
    suspend fun detectConflicts(
        groupId: String,
        localChanges: List<ChangeLogEntry>,
        remoteChanges: List<ChangeLogEntry>
    ): List<SyncConflict> {
        val detectedConflicts = mutableListOf<SyncConflict>()
        
        try {
            // Group changes by entity to find overlapping modifications
            val changesByEntity = (localChanges + remoteChanges)
                .groupBy { "${it.entityType}:${it.entityId}" }
            
            changesByEntity.forEach { (entityKey, changes) ->
                if (changes.size > 1) {
                    // Multiple changes to same entity - check for conflicts
                    val localChange = changes.find { change ->
                        localChanges.any { it.changeId == change.changeId }
                    }
                    val remoteChangesList = changes.filter { change ->
                        remoteChanges.any { it.changeId == change.changeId }
                    }
                    
                    remoteChangesList.forEach { remoteChange ->
                        localChange?.let { local ->
                            val conflict = analyzeConflict(local, remoteChange)
                            conflict?.let { detectedConflicts.add(it) }
                        }
                    }
                }
            }
            
            // Update conflicts state
            _conflicts.value = detectedConflicts
            
            Log.d(TAG, "Detected ${detectedConflicts.size} conflicts for group $groupId")
            return detectedConflicts
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect conflicts", e)
            return emptyList()
        }
    }
    
    /**
     * Analyze two changes to determine if they conflict
     */
    private suspend fun analyzeConflict(
        localChange: ChangeLogEntry,
        remoteChange: ChangeLogEntry
    ): SyncConflict? {
        return try {
            when {
                // Delete vs Modify conflict
                localChange.changeType == ChangeType.DELETE && remoteChange.changeType == ChangeType.UPDATE -> {
                    createConflict(localChange, remoteChange, ConflictType.DELETE_MODIFY, false)
                }
                
                localChange.changeType == ChangeType.UPDATE && remoteChange.changeType == ChangeType.DELETE -> {
                    createConflict(localChange, remoteChange, ConflictType.DELETE_MODIFY, false)
                }
                
                // Simultaneous edits (within time window)
                localChange.changeType == ChangeType.UPDATE && 
                remoteChange.changeType == ChangeType.UPDATE &&
                areTimestampsClose(localChange.timestamp, remoteChange.timestamp) -> {
                    val canAutoResolve = canAutoResolveSimultaneousEdit(localChange, remoteChange)
                    createConflict(localChange, remoteChange, ConflictType.SIMULTANEOUS_EDIT, canAutoResolve)
                }
                
                // Annotation overlaps (special case for annotations)
                localChange.entityType == EntityType.ANNOTATION && 
                remoteChange.entityType == EntityType.ANNOTATION &&
                doAnnotationsOverlap(localChange, remoteChange) -> {
                    createConflict(localChange, remoteChange, ConflictType.ANNOTATION_OVERLAP, true)
                }
                
                // Structure changes (group/member modifications)
                (localChange.entityType == EntityType.GROUP || localChange.entityType == EntityType.MEMBER) &&
                (remoteChange.entityType == EntityType.GROUP || remoteChange.entityType == EntityType.MEMBER) -> {
                    createConflict(localChange, remoteChange, ConflictType.STRUCTURE_CHANGE, false)
                }
                
                // Different checksums for same entity
                localChange.entityType == remoteChange.entityType &&
                localChange.entityId == remoteChange.entityId &&
                localChange.checksum != remoteChange.checksum -> {
                    createConflict(localChange, remoteChange, ConflictType.SIMULTANEOUS_EDIT, false)
                }
                
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze conflict", e)
            null
        }
    }
    
    /**
     * Create a sync conflict object
     */
    private fun createConflict(
        localChange: ChangeLogEntry,
        remoteChange: ChangeLogEntry,
        conflictType: ConflictType,
        canAutoResolve: Boolean
    ): SyncConflict {
        return SyncConflict(
            conflictId = "${localChange.changeId}_${remoteChange.changeId}",
            entityType = localChange.entityType,
            entityId = localChange.entityId,
            entityName = localChange.entityName,
            localVersion = ConflictVersion(
                timestamp = localChange.timestamp,
                deviceId = localChange.deviceId,
                deviceName = localChange.deviceName,
                authorName = localChange.deviceName,
                checksum = localChange.checksum,
                description = localChange.description
            ),
            remoteVersion = ConflictVersion(
                timestamp = remoteChange.timestamp,
                deviceId = remoteChange.deviceId,
                deviceName = remoteChange.deviceName,
                authorName = remoteChange.deviceName,
                checksum = remoteChange.checksum,
                description = remoteChange.description
            ),
            conflictType = conflictType,
            canAutoResolve = canAutoResolve
        )
    }
    
    /**
     * Resolve a conflict with the specified action
     */
    suspend fun resolveConflict(
        conflict: SyncConflict, 
        resolution: ResolutionAction
    ): Result<Unit> {
        return try {
            when (resolution) {
                ResolutionAction.KEEP_LOCAL -> {
                    // Keep local version, mark remote as resolved
                    Log.d(TAG, "Keeping local version for ${conflict.entityName}")
                    markConflictResolved(conflict, "local_kept")
                }
                
                ResolutionAction.ACCEPT_REMOTE -> {
                    // Accept remote version, update local
                    Log.d(TAG, "Accepting remote version for ${conflict.entityName}")
                    applyRemoteVersion(conflict)
                    markConflictResolved(conflict, "remote_accepted")
                }
                
                ResolutionAction.MERGE_ANNOTATIONS -> {
                    // Merge annotations (special case)
                    if (conflict.entityType == EntityType.ANNOTATION) {
                        Log.d(TAG, "Merging annotations for ${conflict.entityName}")
                        mergeAnnotations(conflict)
                        markConflictResolved(conflict, "merged")
                    } else {
                        return Result.failure(Exception("Cannot merge non-annotation entities"))
                    }
                }
                
                ResolutionAction.LAYER_SEPARATE -> {
                    // Create separate layers (annotations only)
                    if (conflict.entityType == EntityType.ANNOTATION) {
                        Log.d(TAG, "Creating separate layers for ${conflict.entityName}")
                        createSeparateLayers(conflict)
                        markConflictResolved(conflict, "layered")
                    } else {
                        return Result.failure(Exception("Cannot layer non-annotation entities"))
                    }
                }
                
                ResolutionAction.MANUAL_MERGE -> {
                    // Manual merge - requires user intervention
                    Log.d(TAG, "Manual merge requested for ${conflict.entityName}")
                    return Result.failure(Exception("Manual merge not yet implemented"))
                }
            }
            
            // Remove from conflicts list
            val updatedConflicts = _conflicts.value.filter { it.conflictId != conflict.conflictId }
            _conflicts.value = updatedConflicts
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve conflict", e)
            Result.failure(e)
        }
    }
    
    /**
     * Auto-resolve conflicts where possible
     */
    suspend fun autoResolveConflicts(conflicts: List<SyncConflict>): List<SyncConflict> {
        val remainingConflicts = mutableListOf<SyncConflict>()
        
        conflicts.forEach { conflict ->
            if (conflict.canAutoResolve) {
                when (conflict.conflictType) {
                    ConflictType.ANNOTATION_OVERLAP -> {
                        // Auto-merge overlapping annotations
                        val result = resolveConflict(conflict, ResolutionAction.MERGE_ANNOTATIONS)
                        if (result.isFailure) {
                            remainingConflicts.add(conflict)
                        }
                    }
                    
                    ConflictType.SIMULTANEOUS_EDIT -> {
                        // Use last-writer-wins for simple edits
                        val resolution = if (conflict.remoteVersion.timestamp > conflict.localVersion.timestamp) {
                            ResolutionAction.ACCEPT_REMOTE
                        } else {
                            ResolutionAction.KEEP_LOCAL
                        }
                        val result = resolveConflict(conflict, resolution)
                        if (result.isFailure) {
                            remainingConflicts.add(conflict)
                        }
                    }
                    
                    else -> {
                        remainingConflicts.add(conflict)
                    }
                }
            } else {
                remainingConflicts.add(conflict)
            }
        }
        
        return remainingConflicts
    }
    
    /**
     * Clear all conflicts for a group
     */
    fun clearConflicts() {
        _conflicts.value = emptyList()
    }
    
    // Helper methods
    private fun areTimestampsClose(timestamp1: Long, timestamp2: Long): Boolean {
        return kotlin.math.abs(timestamp1 - timestamp2) <= CONFLICT_WINDOW
    }
    
    private suspend fun canAutoResolveSimultaneousEdit(
        localChange: ChangeLogEntry,
        remoteChange: ChangeLogEntry
    ): Boolean {
        // Simple metadata changes can often be auto-resolved
        return localChange.entityType in listOf(
            EntityType.SONG, 
            EntityType.SETLIST
        ) && localChange.changeType == ChangeType.UPDATE
    }
    
    private suspend fun doAnnotationsOverlap(
        localChange: ChangeLogEntry,
        remoteChange: ChangeLogEntry
    ): Boolean {
        // TODO: Implement actual annotation overlap detection
        // For now, assume overlap if they're on the same song file
        return localChange.metadata["songFileId"] == remoteChange.metadata["songFileId"]
    }
    
    private suspend fun applyRemoteVersion(conflict: SyncConflict) {
        // TODO: Implement applying remote version to local entity
        Log.d(TAG, "Would apply remote version for ${conflict.entityName}")
    }
    
    private suspend fun mergeAnnotations(conflict: SyncConflict) {
        // TODO: Implement annotation merging
        Log.d(TAG, "Would merge annotations for ${conflict.entityName}")
    }
    
    private suspend fun createSeparateLayers(conflict: SyncConflict) {
        // TODO: Implement creating separate annotation layers
        Log.d(TAG, "Would create separate layers for ${conflict.entityName}")
    }
    
    private suspend fun markConflictResolved(conflict: SyncConflict, resolution: String) {
        // TODO: Mark conflict as resolved in database
        Log.d(TAG, "Marked conflict ${conflict.conflictId} as resolved with: $resolution")
    }
}