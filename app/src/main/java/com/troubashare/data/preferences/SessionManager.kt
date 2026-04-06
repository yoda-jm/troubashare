package com.troubashare.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.troubashare.domain.model.AppMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the active session (mode + member identity) across the whole app.
 *
 * A session is a lightweight runtime choice — not tied to a specific file or song.
 * It persists across app restarts so users don't have to re-select every time.
 */
@Singleton
class SessionManager @Inject constructor(
    context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(loadMode())
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    private val _activeMemberId = MutableStateFlow(loadMemberId())
    val activeMemberId: StateFlow<String?> = _activeMemberId.asStateFlow()

    fun setSession(mode: AppMode, memberId: String?) {
        _mode.value = mode
        _activeMemberId.value = memberId
        prefs.edit {
            putString("mode", mode.name)
            putString("member_id", memberId)
        }
    }

    private fun loadMode(): AppMode =
        try { AppMode.valueOf(prefs.getString("mode", null) ?: "") }
        catch (_: Exception) { AppMode.ADMIN }

    private fun loadMemberId(): String? = prefs.getString("member_id", null)
}
