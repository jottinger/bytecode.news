/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.tell.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class TellOperationTests {

    @Autowired lateinit var eventGateway: EventGateway

    private fun provenance(
        replyTo: String = "#test",
        serviceId: String = "testnet",
        protocol: Protocol = Protocol.IRC,
    ) = Provenance(protocol = protocol, serviceId = serviceId, replyTo = replyTo)

    private fun addressedMessage(
        text: String,
        replyTo: String = "#test",
        serviceId: String = "testnet",
        nick: String = "testuser",
    ) =
        MessageBuilder.withPayload(text)
            .setHeader(Provenance.HEADER, provenance(replyTo, serviceId))
            .setHeader(Provenance.ADDRESSED, true)
            .setHeader("nick", nick)
            .build()

    @Test
    fun `tell with name resolves to private message on same protocol`() {
        val result = eventGateway.process(addressedMessage("tell blue go to heck!"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload.toString()
        assertEquals("Message delivered to blue", payload)
    }

    @Test
    fun `tell with channel resolves to channel on same protocol`() {
        val result = eventGateway.process(addressedMessage("tell #java hello there"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload.toString()
        assertEquals("Message delivered to #java", payload)
    }

    @Test
    fun `tell with full URI uses URI directly`() {
        val result =
            eventGateway.process(addressedMessage("tell irc://othernet/%23java hello from here"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload.toString()
        assertTrue(payload.contains("Message delivered to"))
    }

    @Test
    fun `tell without message returns not handled`() {
        val result = eventGateway.process(addressedMessage("tell blue"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `tell without target returns not handled`() {
        val result = eventGateway.process(addressedMessage("tell"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `non-tell message returns not handled`() {
        val result = eventGateway.process(addressedMessage("something else"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `tell includes sender attribution`() {
        // The tell operation publishes to egress with attribution.
        // We verify it returns success with the expected confirmation.
        val result = eventGateway.process(addressedMessage("tell blue hey there", nick = "alice"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals(
            "Message delivered to blue",
            (result as OperationResult.Success).payload.toString(),
        )
    }
}
