package com.troubashare.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

/**
 * Manages the stable device identity and monotonically-increasing sequence counter.
 * The deviceId never changes after first launch.
 * The sequence counter increments with every write operation.
 */
class DeviceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "device_identity",
        Context.MODE_PRIVATE
    )

    /** Stable UUID identifying this installation. Never changes after first launch. */
    val deviceId: String by lazy {
        prefs.getString("device_id", null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit { putString("device_id", id) }
            id
        }
    }

    /** Monotonically-increasing counter incremented on every write. */
    fun nextSeq(): Long {
        val next = prefs.getLong("seq", 0L) + 1L
        prefs.edit { putLong("seq", next) }
        return next
    }

    /** Current sequence without incrementing. */
    fun currentSeq(): Long = prefs.getLong("seq", 0L)
}
