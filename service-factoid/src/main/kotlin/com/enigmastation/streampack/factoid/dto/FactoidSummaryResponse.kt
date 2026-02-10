/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.dto

import java.time.Instant

/** Summary representation of a factoid for paginated listings */
data class FactoidSummaryResponse(
    val selector: String,
    val locked: Boolean,
    val updatedBy: String?,
    val updatedAt: Instant,
)
