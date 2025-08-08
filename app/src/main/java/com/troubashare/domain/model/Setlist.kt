package com.troubashare.domain.model

import java.text.SimpleDateFormat
import java.util.*

data class Setlist(
    val id: String,
    val groupId: String,
    val name: String,
    val description: String? = null,
    val venue: String? = null,
    val eventDate: Long? = null,
    val items: List<SetlistItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val formattedEventDate: String?
        get() = eventDate?.let { 
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it))
        }
    
    val totalDuration: Int
        get() = items.sumOf { it.duration ?: 180 } // Default 3 minutes per song
    
    val formattedDuration: String
        get() {
            val minutes = totalDuration / 60
            val seconds = totalDuration % 60
            return if (minutes > 60) {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                "${hours}h ${remainingMinutes}m"
            } else {
                "${minutes}m"
            }
        }
}

data class SetlistItem(
    val id: String,
    val setlistId: String,
    val song: Song,
    val position: Int,
    val key: String? = null, // Override song's default key
    val tempo: Int? = null, // Override song's default tempo
    val notes: String? = null, // Performance-specific notes
    val duration: Int? = null // Estimated duration in seconds
) {
    // Get effective key (override or song default)
    val effectiveKey: String?
        get() = key ?: song.key
    
    // Get effective tempo (override or song default)
    val effectiveTempo: Int?
        get() = tempo ?: song.tempo
    
    val formattedDuration: String
        get() {
            val totalSeconds = duration ?: 180 // Default 3 minutes
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "${minutes}:${seconds.toString().padStart(2, '0')}"
        }
}