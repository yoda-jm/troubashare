package com.troubashare.domain.model

/**
 * Viewing/editing mode for the current session.
 *
 * - ADMIN:      library management; shared layer editable; no personal member identity.
 * - PERFORMER:  own personal layer editable; shared + promoted layers read-only.
 * - CONDUCTOR:  all layers visible (read-only except own); can promote own layers.
 */
enum class AppMode {
    ADMIN,
    PERFORMER,
    CONDUCTOR
}
