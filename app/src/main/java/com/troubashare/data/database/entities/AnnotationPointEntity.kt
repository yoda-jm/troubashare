package com.troubashare.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "annotation_points",
    foreignKeys = [
        ForeignKey(
            entity = AnnotationStrokeEntity::class,
            parentColumns = ["id"],
            childColumns = ["strokeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["strokeId"])]
)
data class AnnotationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val strokeId: String,
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timestamp: Long = System.currentTimeMillis()
)