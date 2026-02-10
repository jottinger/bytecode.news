/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class IrcAdminOperationTests {

    @Autowired lateinit var eventGateway: EventGateway

    private val superAdmin =
        UserPrincipal(
            id = UUID.randomUUID(),
            username = "admin",
            displayName = "Admin",
            role = Role.SUPER_ADMIN,
        )

    private val regularUser =
        UserPrincipal(
            id = UUID.randomUUID(),
            username = "user",
            displayName = "User",
            role = Role.USER,
        )

    private fun ircMessage(text: String, user: UserPrincipal = superAdmin) =
        MessageBuilder.withPayload(text)
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.CONSOLE,
                    serviceId = "",
                    replyTo = "local",
                    user = user,
                ),
            )
            .build()

    @Test
    fun `irc connect with SUPER_ADMIN returns success`() {
        val result = eventGateway.process(ircMessage("irc connect libera irc.libera.chat nevet"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertTrue((result as OperationResult.Success).payload.toString().contains("registered"))
    }

    @Test
    fun `irc connect without SUPER_ADMIN returns error`() {
        val result =
            eventGateway.process(
                ircMessage("irc connect libera irc.libera.chat nevet", regularUser)
            )
        assertInstanceOf(OperationResult.Error::class.java, result)
        assertTrue((result as OperationResult.Error).message.contains("SUPER_ADMIN"))
    }

    @Test
    fun `irc status returns success`() {
        val result = eventGateway.process(ircMessage("irc status"))
        assertInstanceOf(OperationResult.Success::class.java, result)
    }

    @Test
    fun `irc join after connect returns success`() {
        eventGateway.process(ircMessage("irc connect libera irc.libera.chat nevet"))
        val result = eventGateway.process(ircMessage("irc join libera #java"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertTrue((result as OperationResult.Success).payload.toString().contains("registered"))
    }

    @Test
    fun `irc autojoin updates flag`() {
        eventGateway.process(ircMessage("irc connect libera irc.libera.chat nevet"))
        eventGateway.process(ircMessage("irc join libera #java"))
        val result = eventGateway.process(ircMessage("irc autojoin libera #java true"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertTrue((result as OperationResult.Success).payload.toString().contains("true"))
    }

    @Test
    fun `irc join nonexistent network returns error`() {
        val result = eventGateway.process(ircMessage("irc join nonexistent #java"))
        assertInstanceOf(OperationResult.Error::class.java, result)
    }

    @Test
    fun `bare irc returns help text`() {
        val result = eventGateway.process(ircMessage("irc"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertTrue(
            (result as OperationResult.Success).payload.toString().contains("IRC Admin Commands")
        )
    }

    @Test
    fun `irc unknown subcommand returns error`() {
        val result = eventGateway.process(ircMessage("irc frobnicate"))
        assertInstanceOf(OperationResult.Error::class.java, result)
        assertTrue((result as OperationResult.Error).message.contains("Unknown"))
    }

    @Test
    fun `non-irc message is not handled`() {
        val result = eventGateway.process(ircMessage("karma foo++"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `irc autoconnect updates flag`() {
        eventGateway.process(ircMessage("irc connect libera irc.libera.chat nevet"))
        val result = eventGateway.process(ircMessage("irc autoconnect libera true"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertTrue((result as OperationResult.Success).payload.toString().contains("true"))
    }

    @Test
    fun `irc mute returns success for valid channel`() {
        eventGateway.process(ircMessage("irc connect libera irc.libera.chat nevet"))
        eventGateway.process(ircMessage("irc join libera #java"))
        val result = eventGateway.process(ircMessage("irc mute libera #java"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertTrue((result as OperationResult.Success).payload.toString().contains("Muted"))
    }

    @Test
    fun `irc unmute returns success for valid channel`() {
        eventGateway.process(ircMessage("irc connect libera irc.libera.chat nevet"))
        eventGateway.process(ircMessage("irc join libera #java"))
        val result = eventGateway.process(ircMessage("irc unmute libera #java"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertTrue((result as OperationResult.Success).payload.toString().contains("Unmuted"))
    }

    @Test
    fun `irc disconnect returns success for known network`() {
        eventGateway.process(ircMessage("irc connect libera irc.libera.chat nevet"))
        val result = eventGateway.process(ircMessage("irc disconnect libera"))
        assertInstanceOf(OperationResult.Success::class.java, result)
    }

    @Test
    fun `irc connect with SASL credentials returns success`() {
        val result =
            eventGateway.process(
                ircMessage("irc connect libera irc.libera.chat nevet myaccount mypassword")
            )
        assertInstanceOf(OperationResult.Success::class.java, result)
    }
}
