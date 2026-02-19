/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.repository

import com.enigmastation.streampack.core.entity.MessageLog
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MessageLogRepository : JpaRepository<MessageLog, UUID> {
    fun findByProvenanceUriOrderByTimestampDesc(
        provenanceUri: String,
        pageable: Pageable,
    ): Page<MessageLog>

    /** Returns messages within a time window in chronological order */
    fun findByProvenanceUriAndTimestampBetweenOrderByTimestampAsc(
        provenanceUri: String,
        from: Instant,
        to: Instant,
        pageable: Pageable,
    ): Page<MessageLog>
}
