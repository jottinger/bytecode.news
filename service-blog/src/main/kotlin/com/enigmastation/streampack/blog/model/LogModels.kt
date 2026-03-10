/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import com.enigmastation.streampack.core.model.MessageDirection
import java.time.Instant

data class LogProvenanceSummary(
    val provenanceUri: String,
    val protocol: String,
    val serviceId: String?,
    val replyTo: String,
    val latestTimestamp: Instant?,
    val latestSender: String?,
    val latestContentPreview: String?,
)

data class LogProvenanceListResponse(val provenances: List<LogProvenanceSummary>)

data class LogEntry(
    val timestamp: Instant,
    val sender: String,
    val content: String,
    val direction: MessageDirection,
)

data class LogDayResponse(val provenanceUri: String, val day: String, val entries: List<LogEntry>)
