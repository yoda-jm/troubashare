package com.troubashare.domain.model

/**
 * A named annotation layer for a specific file.
 *
 * Ownership:
 * - [ownerId] == [SHARED_ANNOTATION_LAYER] → group/shared layer, editable only from pool view
 * - [ownerId] == member UUID               → personal layer, editable only by that member
 *
 * [colorIndex] indexes into a fixed UI palette; 0 = first color.
 */
data class AnnotationLayer(
    val id: String,
    val fileId: String,
    val name: String,
    val ownerId: String,
    val colorIndex: Int = 0,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isShared: Boolean get() = ownerId == SHARED_ANNOTATION_LAYER
}
