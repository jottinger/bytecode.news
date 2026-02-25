/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.repository

import com.enigmastation.streampack.core.entity.OneTimeCode
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

/** Persistence for one-time authentication codes */
interface OneTimeCodeRepository : JpaRepository<OneTimeCode, UUID> {

    fun findByEmailAndCode(email: String, code: String): OneTimeCode?

    /** Counts codes that have not been used and have not expired */
    @Query(
        "SELECT COUNT(c) FROM OneTimeCode c WHERE c.email = :email AND c.usedAt IS NULL AND c.expiresAt > :now"
    )
    fun countActiveByEmail(email: String, now: Instant): Long

    /** Removes codes that expired before the given cutoff */
    @Modifying
    @Query("DELETE FROM OneTimeCode c WHERE c.expiresAt < :cutoff")
    fun deleteExpired(cutoff: Instant)
}
