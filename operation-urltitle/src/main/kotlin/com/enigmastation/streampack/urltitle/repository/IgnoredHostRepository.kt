/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.urltitle.repository

import com.enigmastation.streampack.urltitle.entity.IgnoredHost
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface IgnoredHostRepository : JpaRepository<IgnoredHost, UUID> {
    fun findByHostNameIgnoreCase(hostName: String): IgnoredHost?
}
