package com.troubashare.data.database.dao

data class CompleteAnnotationData(
    val id: String,
    val fileId: String,
    val memberId: String,
    val pageNumber: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val stroke_id: String?,
    val color: Long?,
    val strokeWidth: Float?,
    val tool: String?,
    val stroke_created: Long?,
    val x: Float?,
    val y: Float?,
    val pressure: Float?,
    val point_timestamp: Long?
)