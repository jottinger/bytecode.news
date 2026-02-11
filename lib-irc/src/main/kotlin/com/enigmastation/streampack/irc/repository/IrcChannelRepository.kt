/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.repository

import com.enigmastation.streampack.irc.entity.IrcChannel
import com.enigmastation.streampack.irc.entity.IrcNetwork
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface IrcChannelRepository : JpaRepository<IrcChannel, UUID> {
    fun findByNetworkAndNameAndDeletedFalse(network: IrcNetwork, name: String): IrcChannel?

    fun findByNetworkAndDeletedFalse(network: IrcNetwork): List<IrcChannel>
}
