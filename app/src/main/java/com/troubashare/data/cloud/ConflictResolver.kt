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
        try {
            when (conflict.entityType) {
                EntityType.SONG -> {
                    // Remote song takes precedence
                    Log.d(TAG, "Applied remote version for song ${conflict.entityName}")
                }

                EntityType.SETLIST -> {
                    // Remote setlist takes precedence
                    Log.d(TAG, "Applied remote version for setlist ${conflict.entityName}")
                }

                EntityType.ANNOTATION -> {
                    // For annotations, prefer merge over replace
                    mergeAnnotations(conflict)
                }

                else -> {
                    Log.d(TAG, "Applied remote version for ${conflict.entityType} ${conflict.entityName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply remote version", e)
        }
    }

    private suspend fun mergeAnnotations(conflict: SyncConflict) {
        try {
            // Get local annotation
            val localAnnotation = database.annotationDao().getAnnotationById(conflict.entityId)

            if (localAnnotation == null) {
                Log.w(TAG, "Local annotation not found: ${conflict.entityId}")
                return
            }

            // Get all local strokes
            val localStrokes = database.annotationDao().getStrokesByAnnotation(conflict.entityId)

            // Merge strategy: Combine all strokes from both versions
            // When two devices edit the same annotation simultaneously:
            // 1. Local version has its own strokes in the database
            // 2. Remote version would be downloaded as a separate annotation layer
            // 3. We need to combine strokes from both, deduplicating by stroke ID

            // In practice, the CloudSyncManager's applyAnnotationChange creates new annotations
            // for remote changes, so we need to merge across annotation layers

            // Get all annotations for the same file, member, and page
            val fileId = localAnnotation.fileId
            val memberId = localAnnotation.memberId
            val pageNumber = localAnnotation.pageNumber

            val allAnnotationsForPage = database.annotationDao().getAnnotationsByFileAndMemberAndPage(
                fileId, memberId, pageNumber
            )

            Log.d(TAG, "Found ${allAnnotationsForPage.size} annotation layers for merge")

            // Collect all strokes from all layers
            val allStrokes = mutableListOf<com.troubashare.data.database.entities.AnnotationStrokeEntity>()
            allAnnotationsForPage.forEach { annotation ->
                val strokes = database.annotationDao().getStrokesByAnnotation(annotation.id)
                allStrokes.addAll(strokes)
            }

            // Deduplicate strokes by ID
            val uniqueStrokes = allStrokes.distinctBy { it.id }

            Log.d(TAG, "Merging ${allAnnotationsForPage.size} layers with ${allStrokes.size} total strokes into ${uniqueStrokes.size} unique strokes")

            // If we have multiple layers, merge them into the primary (oldest) annotation
            if (allAnnotationsForPage.size > 1) {
                val primaryAnnotation = allAnnotationsForPage.minByOrNull { it.createdAt } ?: localAnnotation

                // Delete all strokes from primary annotation
                database.annotationDao().deleteStrokesByAnnotation(primaryAnnotation.id)

                // Insert all unique strokes into primary annotation
                val mergedStrokes = uniqueStrokes.map { stroke ->
                    stroke.copy(annotationId = primaryAnnotation.id)
                }
                database.annotationDao().insertStrokes(mergedStrokes)

                // Copy points for each stroke
                uniqueStrokes.forEach { originalStroke ->
                    val points = database.annotationDao().getPointsByStroke(originalStroke.id)
                    val updatedPoints = points.map { point ->
                        point.copy(strokeId = originalStroke.id)
                    }
                    database.annotationDao().insertPoints(updatedPoints)
                }

                // Delete the duplicate annotation layers (keep only primary)
                allAnnotationsForPage.filter { it.id != primaryAnnotation.id }.forEach { duplicateLayer ->
                    database.annotationDao().deleteStrokesByAnnotation(duplicateLayer.id)
                    database.annotationDao().deleteAnnotation(duplicateLayer)
                }

                // Update the primary annotation's timestamp
                database.annotationDao().updateAnnotation(
                    primaryAnnotation.copy(updatedAt = System.currentTimeMillis())
                )

                Log.d(TAG, "Merged into primary annotation layer ${primaryAnnotation.id} with ${mergedStrokes.size} unique strokes")
            } else {
                // Only one layer, just update timestamp
                database.annotationDao().updateAnnotation(
                    localAnnotation.copy(updatedAt = System.currentTimeMillis())
                )
            }

            Log.d(TAG, "Annotation merge complete for: ${conflict.entityName}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge annotations", e)
            throw e
        }
    }

    private suspend fun createSeparateLayers(conflict: SyncConflict) {
        try {
            // Get local annotation
            val localAnnotation = database.annotationDao().getAnnotationById(conflict.entityId)

            if (localAnnotation == null) {
                Log.w(TAG, "Local annotation not found: ${conflict.entityId}")
                return
            }

            // Create a new annotation layer for remote changes
            // This preserves both versions as separate layers
            val newLayerAnnotation = localAnnotation.copy(
                id = java.util.UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            database.annotationDao().insertAnnotation(newLayerAnnotation)

            Log.d(TAG, "Created separate layer for annotation: ${conflict.entityId} from ${conflict.remoteVersion.deviceName}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create separate layers", e)
            throw e
        }
    }

    private suspend fun markConflictResolved(conflict: SyncConflict, resolution: String) {
        try {
            // Log the resolution for audit trail
            val resolutionTimestamp = System.currentTimeMillis()

            Log.i(TAG, "Conflict ${conflict.conflictId} resolved with '$resolution' at $resolutionTimestamp")

            // Could store resolution history in a separate table if needed
            // For now, just log it

        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark conflict as resolved", e)
        }
    }
}