/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.repository

import com.enigmastation.streampack.core.entity.ChannelControlOptions
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ChannelControlOptionsRepository : JpaRepository<ChannelControlOptions, UUID> {
    fun findByProvenanceUriAndDeletedFalse(provenanceUri: String): ChannelControlOptions?

    fun findByAutojoinTrueAndDeletedFalse(): List<ChannelControlOptions>
}
