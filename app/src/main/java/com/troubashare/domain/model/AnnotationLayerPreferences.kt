package com.troubashare.domain.model

data class AnnotationLayerPreferences(
    val fileId: String,
    val memberId: String,
    val showInConcert: Boolean = true,
    val layerName: String? = null // For future layer naming functionality
)