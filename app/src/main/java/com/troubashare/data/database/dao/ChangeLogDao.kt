package com.troubashare.data.database.dao

import androidx.room.*
import com.troubashare.data.database.entities.ChangeLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChangeLogDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChange(change: ChangeLogEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChanges(changes: List<ChangeLogEntity>)
    
    @Query("""
        SELECT * FROM change_log 
        WHERE groupId = :groupId AND isSynced = 0 
        ORDER BY timestamp ASC
    """)
    suspend fun getPendingChanges(groupId: String): List<ChangeLogEntity>
    
    @Query("""
        SELECT * FROM change_log 
        WHERE groupId = :groupId 
        ORDER BY timestamp DESC
    """)
    suspend fun getAllChanges(groupId: String): List<ChangeLogEntity>
    
    @Query("""
        SELECT * FROM change_log 
        WHERE groupId = :groupId AND timestamp > :timestamp 
        ORDER BY timestamp ASC
    """)
    suspend fun getChangesSince(groupId: String, timestamp: Long): List<ChangeLogEntity>
    
    @Query("""
        UPDATE change_log 
        SET isSynced = 1, lastSyncAttempt = :currentTime 
        WHERE changeId IN (:changeIds)
    """)
    suspend fun markChangesSynced(changeIds: List<String>, currentTime: Long = System.currentTimeMillis())
    
    @Query("""
        UPDATE change_log 
        SET syncAttempts = syncAttempts + 1, 
            lastSyncAttempt = :currentTime,
            syncError = :error 
        WHERE changeId IN (:changeIds)
    """)
    suspend fun markSyncFailed(changeIds: List<String>, error: String, currentTime: Long = System.currentTimeMillis())
    
    @Query("""
        SELECT MAX(timestamp) FROM change_log 
        WHERE groupId = :groupId
    """)
    suspend fun getLatestChangeTimestamp(groupId: String): Long?
    
    @Query("""
        SELECT COUNT(*) FROM change_log 
        WHERE groupId = :groupId AND isSynced = 0
    """)
    suspend fun getPendingChangeCount(groupId: String): Int
    
    @Query("""
        DELETE FROM change_log 
        WHERE groupId = :groupId AND timestamp < :timestamp AND isSynced = 1
    """)
    suspend fun clearOldChanges(groupId: String, timestamp: Long)
    
    @Query("""
        DELETE FROM change_log 
        WHERE groupId = :groupId
    """)
    suspend fun clearAllChanges(groupId: String)
    
    @Query("""
        SELECT * FROM change_log 
        WHERE entityType = :entityType AND entityId = :entityId 
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestChangeForEntity(entityType: String, entityId: String): ChangeLogEntity?
    
    @Query("""
        SELECT DISTINCT deviceId, deviceName, MAX(timestamp) as lastActivity
        FROM change_log 
        WHERE groupId = :groupId 
        GROUP BY deviceId 
        ORDER BY lastActivity DESC
    """)
    suspend fun getActiveDevices(groupId: String): List<DeviceActivity>
    
    @Query("""
        SELECT * FROM change_log 
        WHERE groupId = :groupId 
        AND entityType = :entityType 
        AND entityId = :entityId 
        ORDER BY timestamp ASC
    """)
    suspend fun getChangeHistoryForEntity(
        groupId: String, 
        entityType: String, 
        entityId: String
    ): List<ChangeLogEntity>
    
    @Query("""
        SELECT * FROM change_log 
        WHERE groupId = :groupId 
        AND deviceId != :excludeDeviceId 
        AND timestamp > :timestamp 
        ORDER BY timestamp ASC
    """)
    suspend fun getRemoteChangesSince(
        groupId: String, 
        excludeDeviceId: String, 
        timestamp: Long
    ): List<ChangeLogEntity>
    
    @Query("""
        SELECT * FROM change_log 
        WHERE groupId = :groupId 
        AND isSynced = 0 
        AND syncAttempts < :maxAttempts 
        ORDER BY timestamp ASC
    """)
    suspend fun getRetryableChanges(groupId: String, maxAttempts: Int = 3): List<ChangeLogEntity>
    
    @Query("""
        SELECT changeId FROM change_log 
        WHERE groupId = :groupId 
        AND entityType = :entityType 
        AND entityId = :entityId 
        AND changeType = :changeType 
        AND deviceId = :deviceId 
        AND timestamp > :timestamp
    """)
    suspend fun findDuplicateChange(
        groupId: String,
        entityType: String,
        entityId: String,
        changeType: String,
        deviceId: String,
        timestamp: Long
    ): String?
}

data class DeviceActivity(
    val deviceId: String,
    val deviceName: String,
    val lastActivity: Long
)