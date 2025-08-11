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
    
    fun setAnnotationLayerVisibility(fileId: String, memberId: String, showInConcert: Boolean) {
        val preferences = getAnnotationLayerPreferences()
        val key = "${fileId}_${memberId}"
        
        val existingPref = preferences[key]
        val updatedPref = existingPref?.copy(showInConcert = showInConcert)
            ?: AnnotationLayerPreferences(
                fileId = fileId,
                memberId = memberId,
                showInConcert = showInConcert
            )
        
        preferences[key] = updatedPref
        saveAnnotationLayerPreferences(preferences)
    }
    
    fun getAnnotationLayerVisibility(fileId: String, memberId: String): Boolean {
        val preferences = getAnnotationLayerPreferences()
        val key = "${fileId}_${memberId}"
        return preferences[key]?.showInConcert ?: true // Default to visible
    }
    
    fun setAnnotationLayerName(fileId: String, memberId: String, name: String?) {
        val preferences = getAnnotationLayerPreferences()
        val key = "${fileId}_${memberId}"
        
        val existingPref = preferences[key]
        val updatedPref = existingPref?.copy(layerName = name)
            ?: AnnotationLayerPreferences(
                fileId = fileId,
                memberId = memberId,
                layerName = name
            )
        
        preferences[key] = updatedPref
        saveAnnotationLayerPreferences(preferences)
    }
    
    fun getAnnotationLayerName(fileId: String, memberId: String): String? {
        val preferences = getAnnotationLayerPreferences()
        val key = "${fileId}_${memberId}"
        return preferences[key]?.layerName
    }
    
    private fun getAnnotationLayerPreferences(): MutableMap<String, AnnotationLayerPreferences> {
        val json = prefs.getString("layer_preferences", null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, AnnotationLayerPreferences>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }
    
    private fun saveAnnotationLayerPreferences(preferences: Map<String, AnnotationLayerPreferences>) {
        val json = gson.toJson(preferences)
        prefs.edit {
            putString("layer_preferences", json)
        }
    }
}