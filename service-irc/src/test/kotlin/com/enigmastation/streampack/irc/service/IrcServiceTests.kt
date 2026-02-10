/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.service

import com.enigmastation.streampack.irc.repository.IrcChannelRepository
import com.enigmastation.streampack.irc.repository.IrcNetworkRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class IrcServiceTests {

    @Autowired lateinit var ircService: IrcService
    @Autowired lateinit var networkRepository: IrcNetworkRepository
    @Autowired lateinit var channelRepository: IrcChannelRepository

    @Test
    fun `connect persists network entity`() {
        val result = ircService.connect("libera", "irc.libera.chat", "nevet", null, null)
        assertTrue(result.contains("registered"))

        val network = networkRepository.findByNameAndDeletedFalse("libera")
        assertNotNull(network)
        assertEquals("irc.libera.chat", network!!.host)
        assertEquals("nevet", network.nick)
    }

    @Test
    fun `connect with duplicate name returns error`() {
        ircService.connect("libera", "irc.libera.chat", "nevet", null, null)
        val result = ircService.connect("libera", "irc.other.net", "nevet2", null, null)
        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun `join persists channel entity for existing network`() {
        ircService.connect("libera", "irc.libera.chat", "nevet", null, null)
        val result = ircService.join("libera", "#java")
        assertTrue(result.contains("registered"))

        val network = networkRepository.findByNameAndDeletedFalse("libera")!!
        val channel = channelRepository.findByNetworkAndNameAndDeletedFalse(network, "#java")
        assertNotNull(channel)
    }

    @Test
    fun `join with unknown network returns error`() {
        val result = ircService.join("nonexistent", "#java")
        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun `setAutojoin updates entity flag`() {
        ircService.connect("libera", "irc.libera.chat", "nevet", null, null)
        ircService.join("libera", "#java")
        val result = ircService.setAutojoin("libera", "#java", true)
        assertTrue(result.contains("true"))

        val network = networkRepository.findByNameAndDeletedFalse("libera")!!
        val channel = channelRepository.findByNetworkAndNameAndDeletedFalse(network, "#java")!!
        assertTrue(channel.autojoin)
    }

    @Test
    fun `setAutoconnect updates entity flag`() {
        ircService.connect("libera", "irc.libera.chat", "nevet", null, null)
        val result = ircService.setAutoconnect("libera", true)
        assertTrue(result.contains("true"))

        val network = networkRepository.findByNameAndDeletedFalse("libera")!!
        assertTrue(network.autoconnect)
    }

    @Test
    fun `setAutomute updates entity flag`() {
        ircService.connect("libera", "irc.libera.chat", "nevet", null, null)
        ircService.join("libera", "#java")
        val result = ircService.setAutomute("libera", "#java", true)
        assertTrue(result.contains("true"))

        val network = networkRepository.findByNameAndDeletedFalse("libera")!!
        val channel = channelRepository.findByNetworkAndNameAndDeletedFalse(network, "#java")!!
        assertTrue(channel.automute)
    }

    @Test
    fun `setVisible updates entity flag`() {
        ircService.connect("libera", "irc.libera.chat", "nevet", null, null)
        ircService.join("libera", "#java")
        val result = ircService.setVisible("libera", "#java", false)
        assertTrue(result.contains("false"))

        val network = networkRepository.findByNameAndDeletedFalse("libera")!!
        val channel = channelRepository.findByNetworkAndNameAndDeletedFalse(network, "#java")!!
        assertEquals(false, channel.visible)
    }

    @Test
    fun `setLogged updates entity flag`() {
        ircService.connect("libera", "irc.libera.chat", "nevet", null, null)
        ircService.join("libera", "#java")
        val result = ircService.setLogged("libera", "#java", false)
        assertTrue(result.contains("false"))

        val network = networkRepository.findByNameAndDeletedFalse("libera")!!
        val channel = channelRepository.findByNetworkAndNameAndDeletedFalse(network, "#java")!!
        assertEquals(false, channel.logged)
    }

    @Test
    fun `disconnect with unknown network returns error`() {
        val result = ircService.disconnect("nonexistent")
        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun `status with no networks shows empty message`() {
        val result = ircService.status(null)
        assertEquals("No IRC networks configured", result)
    }

    @Test
    fun `status with networks shows summaries`() {
        ircService.connect("libera", "irc.libera.chat", "nevet", null, null)
        val result = ircService.status(null)
        assertTrue(result.contains("libera"))
    }
}
