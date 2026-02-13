/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.bridge.repository

import com.enigmastation.streampack.bridge.entity.BridgePair
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface BridgePairRepository : JpaRepository<BridgePair, UUID> {
    fun findByFirstUriAndDeletedFalse(firstUri: String): BridgePair?

    fun findBySecondUriAndDeletedFalse(secondUri: String): BridgePair?

    fun findByFirstUriAndSecondUriAndDeletedFalse(firstUri: String, secondUri: String): BridgePair?

    fun findByDeletedFalse(): List<BridgePair>
}
