package com.troubashare.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "change_log",
    indices = [
        Index(value = ["groupId", "timestamp"]),
        Index(value = ["isSynced"]),
        Index(value = ["deviceId"]),
        Index(value = ["entityType", "entityId"])
    ]
)
data class ChangeLogEntity(
    @PrimaryKey
    val changeId: String,
    val groupId: String,
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long,
    val changeType: String, // CREATE, UPDATE, DELETE, MOVE
    val entityType: String, // GROUP, MEMBER, SONG, SONG_FILE, SETLIST, SETLIST_ITEM, ANNOTATION
    val entityId: String,
    val entityName: String,
    val memberId: String? = null,
    val checksum: String,
    val description: String,
    val metadataJson: String = "{}",
    val isSynced: Boolean = false,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long? = null,
    val syncError: String? = null
)