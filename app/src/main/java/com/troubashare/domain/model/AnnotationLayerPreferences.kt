package com.troubashare.domain.model

data class AnnotationLayerPreferences(
    val fileId: String,
    val memberId: String,
    val showInConcert: Boolean = true,
    val layerName: String? = null, // For custom layer naming
    val useScrollMode: Boolean = false // false = swipe/page mode, true = continuous scroll mode
)