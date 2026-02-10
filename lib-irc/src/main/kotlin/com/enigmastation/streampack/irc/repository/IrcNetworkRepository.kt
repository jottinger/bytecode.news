/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.repository

import com.enigmastation.streampack.irc.entity.IrcNetwork
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface IrcNetworkRepository : JpaRepository<IrcNetwork, UUID> {
    fun findByNameAndDeletedFalse(name: String): IrcNetwork?

    fun findByDeletedFalse(): List<IrcNetwork>

    fun findByAutoconnectTrueAndDeletedFalse(): List<IrcNetwork>
}
