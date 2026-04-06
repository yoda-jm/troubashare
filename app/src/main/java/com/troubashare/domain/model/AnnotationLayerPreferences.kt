package com.troubashare.domain.model

data class AnnotationLayerPreferences(
    val fileId: String,
    val memberId: String,
    val showInConcert: Boolean = true,
    val layerName: String? = null,
    val useScrollMode: Boolean = false,
    val showSharedLayer: Boolean = true,
    val activeLayerIsShared: Boolean = false
)