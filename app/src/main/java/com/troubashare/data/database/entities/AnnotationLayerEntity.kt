package com.troubashare.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted annotation layer.
 *
 * [ownerId] holds either "_shared_" (group layer) or a member UUID (personal layer).
 * [colorIndex] is a 0-based index into the app's fixed layer-colour palette.
 */
@Entity(
    tableName = "annotation_layers",
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["ownerId"])
    ]
)
data class AnnotationLayerEntity(
    @PrimaryKey val id: String,
    val fileId: String,
    val name: String,
    val ownerId: String,
    val colorIndex: Int = 0,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isPromoted: Boolean = false
)
