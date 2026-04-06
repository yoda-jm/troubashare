package com.troubashare.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = SongFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
        // NOTE: memberId is intentionally NOT a foreign key — it may hold synthetic IDs
        // like "_shared_" for the group annotation layer, which have no corresponding member row.
    ],
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["memberId"]),
        Index(value = ["pageNumber"])
    ]
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val fileId: String,
    val memberId: String,
    val layerId: String = "",       // references annotation_layers.id; "" = legacy row
    val pageNumber: Int = 0,
    val scope: String = "PERSONAL", // AnnotationScope.name
    val partId: String? = null,     // only when scope = PART
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
