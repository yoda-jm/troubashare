package com.troubashare.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.troubashare.domain.model.AnnotationLayerPreferences
import androidx.core.content.edit

class AnnotationPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "annotation_preferences",
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    // ── Layer visibility / active layer ──────────────────────────────────────

    fun getHiddenLayerIds(fileId: String, viewerMemberId: String): Set<String> {
        val preferences = getAnnotationLayerPreferences()
        return preferences["${fileId}_${viewerMemberId}"]?.hiddenLayerIds ?: emptySet()
    }

    fun setLayerHidden(fileId: String, viewerMemberId: String, layerId: String, hidden: Boolean) {
        val key = "${fileId}_${viewerMemberId}"
        val prefs = getAnnotationLayerPreferences()
        val pref = prefs[key] ?: AnnotationLayerPreferences(fileId, viewerMemberId)
        val newHidden = if (hidden) pref.hiddenLayerIds + layerId
                        else pref.hiddenLayerIds - layerId
        prefs[key] = pref.copy(hiddenLayerIds = newHidden)
        saveAnnotationLayerPreferences(prefs)
    }

    fun getActiveLayerId(fileId: String, viewerMemberId: String): String? {
        val preferences = getAnnotationLayerPreferences()
        return preferences["${fileId}_${viewerMemberId}"]?.activeLayerId
    }

    fun setActiveLayerId(fileId: String, viewerMemberId: String, layerId: String?) {
        val key = "${fileId}_${viewerMemberId}"
        val prefs = getAnnotationLayerPreferences()
        val pref = prefs[key] ?: AnnotationLayerPreferences(fileId, viewerMemberId)
        prefs[key] = pref.copy(activeLayerId = layerId)
        saveAnnotationLayerPreferences(prefs)
    }

    // ── Scroll / swipe mode ──────────────────────────────────────────────────

    fun setScrollMode(fileId: String, memberId: String, useScrollMode: Boolean) {
        val key = "${fileId}_${memberId}"
        val preferences = getAnnotationLayerPreferences()
        val pref = preferences[key] ?: AnnotationLayerPreferences(fileId, memberId)
        preferences[key] = pref.copy(useScrollMode = useScrollMode)
        saveAnnotationLayerPreferences(preferences)
    }

    fun getScrollMode(fileId: String, memberId: String): Boolean {
        val preferences = getAnnotationLayerPreferences()
        return preferences["${fileId}_${memberId}"]?.useScrollMode ?: false
    }

    // ── Legacy helpers (still used by MemberFileSection display name) ────────

    fun setAnnotationLayerName(fileId: String, memberId: String, name: String?) {
        val key = "${fileId}_${memberId}"
        val preferences = getAnnotationLayerPreferences()
        val pref = preferences[key] ?: AnnotationLayerPreferences(fileId, memberId)
        preferences[key] = pref.copy(layerName = name)
        saveAnnotationLayerPreferences(preferences)
    }

    fun getAnnotationLayerName(fileId: String, memberId: String): String? {
        val preferences = getAnnotationLayerPreferences()
        return preferences["${fileId}_${memberId}"]?.layerName
    }

    // ── Drawing style (global) ───────────────────────────────────────────────

    fun saveDrawingStyle(colorArgb: Int, strokeWidth: Float, opacity: Float) {
        prefs.edit {
            putInt("drawing_color", colorArgb)
            putFloat("drawing_stroke_width", strokeWidth)
            putFloat("drawing_opacity", opacity)
        }
    }

    fun getDrawingColor(): Int = prefs.getInt("drawing_color", android.graphics.Color.BLACK)
    fun getDrawingStrokeWidth(): Float = prefs.getFloat("drawing_stroke_width", 5f)
    fun getDrawingOpacity(): Float = prefs.getFloat("drawing_opacity", 1f)

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun getAnnotationLayerPreferences(): MutableMap<String, AnnotationLayerPreferences> {
        val json = prefs.getString("layer_preferences", null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, AnnotationLayerPreferences>>() {}.type
            val loaded: MutableMap<String, AnnotationLayerPreferences> =
                gson.fromJson(json, type) ?: return mutableMapOf()
            // Gson ignores Kotlin default values and can set non-null fields to null.
            // Sanitize after deserialization to avoid NPE in .copy() calls.
            loaded.mapValuesTo(mutableMapOf()) { (_, pref) -> pref.sanitized() }
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    /**
     * Fixes up fields that Gson may have deserialized as null despite non-null Kotlin types.
     * Creates a new instance manually to avoid .copy() NPE (copy passes nulls to non-null params).
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun AnnotationLayerPreferences.sanitized() = AnnotationLayerPreferences(
        fileId = fileId ?: "",
        memberId = memberId ?: "",
        useScrollMode = useScrollMode,
        hiddenLayerIds = if (hiddenLayerIds == null) emptySet() else hiddenLayerIds,
        activeLayerId = activeLayerId,
        showInConcert = showInConcert,
        showSharedLayer = showSharedLayer,
        activeLayerIsShared = activeLayerIsShared,
        layerName = layerName
    )

    private fun saveAnnotationLayerPreferences(preferences: Map<String, AnnotationLayerPreferences>) {
        val json = gson.toJson(preferences)
        prefs.edit { putString("layer_preferences", json) }
    }
}
