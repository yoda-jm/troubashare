package com.troubashare.domain.model

data class AnnotationLayerPreferences(
    val fileId: String,
    val memberId: String,
    val useScrollMode: Boolean = false,
    /** IDs of layers the viewer has hidden. Default empty = all layers visible. */
    val hiddenLayerIds: Set<String> = emptySet(),
    /** ID of the currently active (drawing) layer. Null = auto-select first writable. */
    val activeLayerId: String? = null,
    // Legacy fields kept for JSON backward-compat — no longer written by new code
    val showInConcert: Boolean = true,
    val showSharedLayer: Boolean = true,
    val activeLayerIsShared: Boolean = false,
    val layerName: String? = null
)
