package com.troubashare.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "annotation_strokes",
    foreignKeys = [
        ForeignKey(
            entity = AnnotationEntity::class,
            parentColumns = ["id"],
            childColumns = ["annotationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["annotationId"])]
)
data class AnnotationStrokeEntity(
    @PrimaryKey val id: String,
    val annotationId: String,
    val color: Long,
    val strokeWidth: Float,
    val tool: String, // DrawingTool.name
    val text: String? = null, // For TEXT tool annotations
    val createdAt: Long = System.currentTimeMillis()
)