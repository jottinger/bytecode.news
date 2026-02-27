/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.safecracker.service

import java.time.Instant

/** Tracks active safecracker games for countdown announcements and timeout */
data class ActiveGame(
    val provenanceUri: String,
    val startedAt: Instant,
    var lastAnnouncementAt: Instant,
)
