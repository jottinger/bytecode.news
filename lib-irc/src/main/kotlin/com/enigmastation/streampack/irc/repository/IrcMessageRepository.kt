/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.repository

import com.enigmastation.streampack.irc.entity.IrcChannel
import com.enigmastation.streampack.irc.entity.IrcMessage
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface IrcMessageRepository : JpaRepository<IrcMessage, UUID> {
    fun findByChannelOrderByTimestampDesc(channel: IrcChannel, pageable: Pageable): Page<IrcMessage>
}
