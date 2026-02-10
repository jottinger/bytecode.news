/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.repository

import com.enigmastation.streampack.irc.entity.IrcChannel
import com.enigmastation.streampack.irc.entity.IrcMessage
import com.enigmastation.streampack.irc.entity.IrcNetwork
import com.enigmastation.streampack.irc.model.IrcMessageType
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class IrcRepositoryTests {

    @Autowired lateinit var networkRepository: IrcNetworkRepository
    @Autowired lateinit var channelRepository: IrcChannelRepository
    @Autowired lateinit var messageRepository: IrcMessageRepository

    @Test
    fun `save and retrieve network`() {
        val network = IrcNetwork(name = "libera", host = "irc.libera.chat", nick = "nevet")
        val saved = networkRepository.save(network)

        assertNotEquals(UUID(0, 0), saved.id)
        assertEquals(7, saved.id.version())

        val found = networkRepository.findByNameAndDeletedFalse("libera")
        assertNotNull(found)
        assertEquals("libera", found!!.name)
        assertEquals("irc.libera.chat", found.host)
        assertEquals(6697, found.port)
        assertEquals(true, found.tls)
        assertEquals("nevet", found.nick)
    }

    @Test
    fun `save channel with network FK`() {
        val network =
            networkRepository.save(
                IrcNetwork(name = "libera-ch", host = "irc.libera.chat", nick = "nevet")
            )
        val channel =
            channelRepository.save(IrcChannel(network = network, name = "#java", autojoin = true))

        assertNotEquals(UUID(0, 0), channel.id)
        val found = channelRepository.findByNetworkAndNameAndDeletedFalse(network, "#java")
        assertNotNull(found)
        assertEquals("#java", found!!.name)
        assertEquals(true, found.autojoin)
    }

    @Test
    fun `save message and verify ordering`() {
        val network =
            networkRepository.save(
                IrcNetwork(name = "libera-msg", host = "irc.libera.chat", nick = "nevet")
            )
        val channel = channelRepository.save(IrcChannel(network = network, name = "#test-order"))

        messageRepository.save(
            IrcMessage(
                channel = channel,
                nick = "alice",
                content = "first message",
                messageType = IrcMessageType.MESSAGE,
            )
        )
        messageRepository.save(
            IrcMessage(
                channel = channel,
                nick = "bob",
                content = "second message",
                messageType = IrcMessageType.MESSAGE,
            )
        )

        val page =
            messageRepository.findByChannelOrderByTimestampDesc(channel, PageRequest.of(0, 10))
        assertEquals(2, page.totalElements)
        // Most recent first
        assertEquals("bob", page.content[0].nick)
        assertEquals("alice", page.content[1].nick)
    }

    @Test
    fun `findByAutoconnectTrue returns only autoconnect networks`() {
        networkRepository.save(
            IrcNetwork(name = "auto-net", host = "irc.auto.net", nick = "nevet", autoconnect = true)
        )
        networkRepository.save(
            IrcNetwork(
                name = "manual-net",
                host = "irc.manual.net",
                nick = "nevet",
                autoconnect = false,
            )
        )

        val autoNetworks = networkRepository.findByAutoconnectTrueAndDeletedFalse()
        assertEquals(1, autoNetworks.size)
        assertEquals("auto-net", autoNetworks[0].name)
    }

    @Test
    fun `findByAutojoinTrue returns only autojoin channels for a network`() {
        val network =
            networkRepository.save(
                IrcNetwork(name = "libera-aj", host = "irc.libera.chat", nick = "nevet")
            )
        channelRepository.save(
            IrcChannel(network = network, name = "#autojoin-ch", autojoin = true)
        )
        channelRepository.save(IrcChannel(network = network, name = "#manual-ch", autojoin = false))

        val autojoinChannels =
            channelRepository.findByNetworkAndAutojoinTrueAndDeletedFalse(network)
        assertEquals(1, autojoinChannels.size)
        assertEquals("#autojoin-ch", autojoinChannels[0].name)
    }

    @Test
    fun `soft delete excludes network from queries`() {
        networkRepository.save(
            IrcNetwork(name = "active-net", host = "irc.active.net", nick = "nevet")
        )
        networkRepository.save(
            IrcNetwork(
                name = "deleted-net",
                host = "irc.deleted.net",
                nick = "nevet",
                deleted = true,
            )
        )

        val active = networkRepository.findByDeletedFalse()
        assertEquals(1, active.size)
        assertEquals("active-net", active[0].name)

        assertNull(networkRepository.findByNameAndDeletedFalse("deleted-net"))
    }

    @Test
    fun `soft delete excludes channel from queries`() {
        val network =
            networkRepository.save(
                IrcNetwork(name = "libera-del", host = "irc.libera.chat", nick = "nevet")
            )
        channelRepository.save(IrcChannel(network = network, name = "#active-ch"))
        channelRepository.save(IrcChannel(network = network, name = "#deleted-ch", deleted = true))

        val channels = channelRepository.findByNetworkAndDeletedFalse(network)
        assertEquals(1, channels.size)
        assertEquals("#active-ch", channels[0].name)

        assertNull(channelRepository.findByNetworkAndNameAndDeletedFalse(network, "#deleted-ch"))
    }

    @Test
    fun `network toSummary produces readable output`() {
        val network =
            IrcNetwork(
                name = "libera",
                host = "irc.libera.chat",
                port = 6697,
                tls = true,
                nick = "nevet",
                autoconnect = true,
            )
        assertEquals(
            "libera (irc.libera.chat:6697 TLS, nick=nevet, autoconnect)",
            network.toSummary(),
        )
    }
}
