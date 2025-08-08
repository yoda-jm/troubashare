package com.troubashare.data.database.entities

import androidx.room.*

@Entity(
    tableName = "setlists",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class SetlistEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val name: String,
    val description: String? = null,
    val venue: String? = null,
    val eventDate: Long? = null, // Performance date
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "setlist_items",
    foreignKeys = [
        ForeignKey(
            entity = SetlistEntity::class,
            parentColumns = ["id"],
            childColumns = ["setlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["setlistId"]),
        Index(value = ["songId"])
    ]
)
data class SetlistItemEntity(
    @PrimaryKey
    val id: String,
    val setlistId: String,
    val songId: String,
    val position: Int, // Order in the setlist
    val key: String? = null, // Optional key override for this performance
    val tempo: Int? = null, // Optional tempo override for this performance
    val notes: String? = null, // Performance-specific notes
    val duration: Int? = null // Estimated duration in seconds
)