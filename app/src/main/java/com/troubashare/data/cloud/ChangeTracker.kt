package com.troubashare.data.cloud

import android.content.Context
import android.util.Log
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.ChangeLogEntity
import com.troubashare.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChangeTracker @Inject constructor(
    private val context: Context,
    private val database: TroubaShareDatabase
) {
    companion object {
        private const val TAG = "ChangeTracker"
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    private val deviceId = getDeviceId()
    private val deviceName = getDeviceName()
    
    /**
     * Record a change to be synchronized
     */
    suspend fun recordChange(
        groupId: String,
        changeType: ChangeType,
        entityType: EntityType,
        entityId: String,
        entityName: String,
        memberId: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        try {
            val changeId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            // Calculate checksum based on current entity state
            val checksum = calculateEntityChecksum(entityType, entityId)
            
            val change = ChangeLogEntry(
                changeId = changeId,
                deviceId = deviceId,
                deviceName = deviceName,
                timestamp = timestamp,
                changeType = changeType,
                entityType = entityType,
                entityId = entityId,
                entityName = entityName,
                memberId = memberId,
                checksum = checksum,
                description = generateChangeDescription(changeType, entityType, entityName),
                metadata = metadata
            )
            
            // Store in local database
            val entity = ChangeLogEntity(
                changeId = changeId,
                groupId = groupId,
                deviceId = deviceId,
                deviceName = deviceName,
                timestamp = timestamp,
                changeType = changeType.name,
                entityType = entityType.name,
                entityId = entityId,
                entityName = entityName,
                memberId = memberId,
                checksum = checksum,
                description = change.description,
                metadataJson = json.encodeToString(metadata),
                isSynced = false
            )
            
            database.changeLogDao().insertChange(entity)
            
            Log.d(TAG, "Recorded change: ${change.description}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record change", e)
        }
    }
    
    /**
     * Get pending changes that need to be synchronized
     */
    suspend fun getPendingChanges(groupId: String): List<ChangeLogEntry> {
        return try {
            val entities = database.changeLogDao().getPendingChanges(groupId)
            entities.map { entity ->
                ChangeLogEntry(
                    changeId = entity.changeId,
                    deviceId = entity.deviceId,
                    deviceName = entity.deviceName,
                    timestamp = entity.timestamp,
                    changeType = ChangeType.valueOf(entity.changeType),
                    entityType = EntityType.valueOf(entity.entityType),
                    entityId = entity.entityId,
                    entityName = entity.entityName,
                    memberId = entity.memberId,
                    checksum = entity.checksum,
                    description = entity.description,
                    metadata = if (entity.metadataJson.isNotBlank()) {
                        json.decodeFromString(entity.metadataJson)
                    } else {
                        emptyMap()
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get pending changes", e)
            emptyList()
        }
    }
    
    /**
     * Get all changes for a group (both synced and pending)
     */
    suspend fun getAllChanges(groupId: String): List<ChangeLogEntry> {
        return try {
            val entities = database.changeLogDao().getAllChanges(groupId)
            entities.map { entity ->
                ChangeLogEntry(
                    changeId = entity.changeId,
                    deviceId = entity.deviceId,
                    deviceName = entity.deviceName,
                    timestamp = entity.timestamp,
                    changeType = ChangeType.valueOf(entity.changeType),
                    entityType = EntityType.valueOf(entity.entityType),
                    entityId = entity.entityId,
                    entityName = entity.entityName,
                    memberId = entity.memberId,
                    checksum = entity.checksum,
                    description = entity.description,
                    metadata = if (entity.metadataJson.isNotBlank()) {
                        json.decodeFromString(entity.metadataJson)
                    } else {
                        emptyMap()
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all changes", e)
            emptyList()
        }
    }
    
    /**
     * Mark changes as synced
     */
    suspend fun markChangesSynced(changeIds: List<String>) {
        try {
            database.changeLogDao().markChangesSynced(changeIds)
            Log.d(TAG, "Marked ${changeIds.size} changes as synced")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark changes as synced", e)
        }
    }
    
    /**
     * Apply remote changes to local database
     */
    suspend fun applyRemoteChange(groupId: String, change: ChangeLogEntry) {
        try {
            // Store the remote change in local database
            val entity = ChangeLogEntity(
                changeId = change.changeId,
                groupId = groupId,
                deviceId = change.deviceId,
                deviceName = change.deviceName,
                timestamp = change.timestamp,
                changeType = change.changeType.name,
                entityType = change.entityType.name,
                entityId = change.entityId,
                entityName = change.entityName,
                memberId = change.memberId,
                checksum = change.checksum,
                description = change.description,
                metadataJson = json.encodeToString(change.metadata),
                isSynced = true // Remote changes are considered synced
            )
            
            database.changeLogDao().insertChange(entity)
            
            Log.d(TAG, "Applied remote change: ${change.description}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply remote change", e)
        }
    }
    
    /**
     * Clear old changes (cleanup)
     */
    suspend fun clearOldChanges(groupId: String, olderThanTimestamp: Long) {
        try {
            database.changeLogDao().clearOldChanges(groupId, olderThanTimestamp)
            Log.d(TAG, "Cleared old changes for group $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear old changes", e)
        }
    }
    
    /**
     * Get the latest change timestamp for a group
     */
    suspend fun getLatestChangeTimestamp(groupId: String): Long {
        return try {
            database.changeLogDao().getLatestChangeTimestamp(groupId) ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest change timestamp", e)
            0L
        }
    }
    
    /**
     * Calculate checksum for an entity
     */
    private suspend fun calculateEntityChecksum(entityType: EntityType, entityId: String): String {
        return try {
            when (entityType) {
                EntityType.SONG -> {
                    val song = database.songDao().getSongById(entityId)
                    song?.let { 
                        val content = "${it.title}${it.artist}${it.key}${it.tempo}${it.notes}"
                        calculateSHA256(content)
                    } ?: ""
                }
                
                EntityType.SETLIST -> {
                    val setlist = database.setlistDao().getSetlistById(entityId)
                    setlist?.let {
                        val content = "${it.name}${it.description}${it.createdAt}${it.updatedAt}"
                        calculateSHA256(content)
                    } ?: ""
                }
                
                EntityType.ANNOTATION -> {
                    val annotation = database.annotationDao().getAnnotationById(entityId)
                    annotation?.let {
                        val content = "${it.fileId}${it.pageNumber}${it.createdAt}${it.updatedAt}"
                        calculateSHA256(content)
                    } ?: ""
                }
                
                EntityType.GROUP -> {
                    val group = database.groupDao().getGroupById(entityId)
                    group?.let {
                        val members = database.groupDao().getMembersByGroupId(entityId)
                        val content = "${it.name}${members.size}"
                        calculateSHA256(content)
                    } ?: ""
                }
                
                else -> UUID.randomUUID().toString().take(8) // Fallback checksum
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate checksum for $entityType:$entityId", e)
            UUID.randomUUID().toString().take(8)
        }
    }
    
    /**
     * Generate human-readable description for a change
     */
    private fun generateChangeDescription(
        changeType: ChangeType,
        entityType: EntityType,
        entityName: String
    ): String {
        val action = when (changeType) {
            ChangeType.CREATE -> "added"
            ChangeType.UPDATE -> "updated"
            ChangeType.DELETE -> "deleted"
            ChangeType.MOVE -> "moved"
        }
        
        val entityDescription = when (entityType) {
            EntityType.SONG -> "song"
            EntityType.SETLIST -> "setlist"
            EntityType.ANNOTATION -> "annotation"
            EntityType.GROUP -> "group"
            EntityType.MEMBER -> "member"
            EntityType.SONG_FILE -> "file"
            EntityType.SETLIST_ITEM -> "setlist item"
        }
        
        return "$action $entityDescription '$entityName'"
    }
    
    /**
     * Calculate SHA-256 hash
     */
    private fun calculateSHA256(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(16) // First 16 chars
        } catch (e: Exception) {
            UUID.randomUUID().toString().take(16)
        }
    }
    
    /**
     * Get unique device ID
     */
    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("troubashare_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        
        return deviceId
    }
    
    /**
     * Get human-readable device name
     */
    private fun getDeviceName(): String {
        val prefs = context.getSharedPreferences("troubashare_prefs", Context.MODE_PRIVATE)
        var deviceName = prefs.getString("device_name", null)
        
        if (deviceName == null) {
            deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            prefs.edit().putString("device_name", deviceName).apply()
        }
        
        return deviceName
    }
    
    /**
     * Get changes since a specific timestamp
     */
    suspend fun getChangesSince(groupId: String, timestamp: Long): List<ChangeLogEntry> {
        return try {
            val entities = database.changeLogDao().getChangesSince(groupId, timestamp)
            entities.map { entity ->
                ChangeLogEntry(
                    changeId = entity.changeId,
                    deviceId = entity.deviceId,
                    deviceName = entity.deviceName,
                    timestamp = entity.timestamp,
                    changeType = ChangeType.valueOf(entity.changeType),
                    entityType = EntityType.valueOf(entity.entityType),
                    entityId = entity.entityId,
                    entityName = entity.entityName,
                    memberId = entity.memberId,
                    checksum = entity.checksum,
                    description = entity.description,
                    metadata = if (entity.metadataJson.isNotBlank()) {
                        json.decodeFromString(entity.metadataJson)
                    } else {
                        emptyMap()
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get changes since timestamp", e)
            emptyList()
        }
    }
}