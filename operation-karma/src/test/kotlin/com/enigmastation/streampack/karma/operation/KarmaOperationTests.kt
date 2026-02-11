/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.karma.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class KarmaOperationTests {

    @Autowired lateinit var eventGateway: EventGateway

    private fun provenance() =
        Provenance(protocol = Protocol.CONSOLE, serviceId = "", replyTo = "local")

    /** Builds an unaddressed karma message (no nick header) */
    private fun karmaMessage(text: String) =
        MessageBuilder.withPayload(text).setHeader(Provenance.HEADER, provenance()).build()

    /** Builds an unaddressed karma message with a sender nick header */
    private fun karmaMessage(text: String, nick: String) =
        MessageBuilder.withPayload(text)
            .setHeader(Provenance.HEADER, provenance())
            .setHeader("nick", nick)
            .build()

    @Test
    fun `foo++ returns success with karma of 1`() {
        val result = eventGateway.process(karmaMessage("foo++"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("foo now has karma of 1.", (result as OperationResult.Success).payload)
    }

    @Test
    fun `foo-- returns success with karma of -1`() {
        val result = eventGateway.process(karmaMessage("foo--"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("foo now has karma of -1.", (result as OperationResult.Success).payload)
    }

    @Test
    fun `c++-- parses as decrement on c++`() {
        val result = eventGateway.process(karmaMessage("c++--"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertEquals("c++ now has karma of -1.", payload)
    }

    @Test
    fun `c+++ parses as increment on c+`() {
        val result = eventGateway.process(karmaMessage("c+++"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertEquals("c+ now has karma of 1.", payload)
    }

    @Test
    fun `arrow fix prevents false match on arrow`() {
        val result = eventGateway.process(karmaMessage("foo --> bar"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `subject over 150 chars returns not handled`() {
        val longSubject = "a".repeat(151)
        val result = eventGateway.process(karmaMessage("$longSubject++"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `empty subject returns not handled`() {
        val result = eventGateway.process(karmaMessage("++"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `self-karma increment flips to decrement`() {
        val result = eventGateway.process(karmaMessage("testuser++", "testuser"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertEquals("You can't increment your own karma! Your karma is now -1.", payload)
    }

    @Test
    fun `self-karma decrement stays as decrement`() {
        val result = eventGateway.process(karmaMessage("testuser--", "testuser"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertEquals("Your karma is now -1.", payload)
    }

    @Test
    fun `immune subject returns not handled`() {
        val result = eventGateway.process(karmaMessage("immune_bot++"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `karma query returns karma info`() {
        // First set some karma
        eventGateway.process(karmaMessage("eclipse++"))
        eventGateway.process(karmaMessage("eclipse++"))

        val result = eventGateway.process(karmaMessage("karma eclipse"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("eclipse has karma of 2.", (result as OperationResult.Success).payload)
    }

    @Test
    fun `karma query for unknown subject returns no karma data`() {
        val result = eventGateway.process(karmaMessage("karma unknown_thing"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals(
            "unknown_thing has no karma data.",
            (result as OperationResult.Success).payload,
        )
    }

    @Test
    fun `karma self-query uses personalized response`() {
        eventGateway.process(karmaMessage("myuser++"))
        val result = eventGateway.process(karmaMessage("karma myuser", "myuser"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertEquals("myuser, you have karma of 1.", payload)
    }

    @Test
    fun `non-karma message returns not handled`() {
        val result = eventGateway.process(karmaMessage("just a regular message"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `trailing text after predicate is discarded`() {
        val result = eventGateway.process(karmaMessage("kotlin++ is great"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("kotlin now has karma of 1.", (result as OperationResult.Success).payload)
    }

    @Test
    fun `colon completion suffix is stripped from subject`() {
        val result = eventGateway.process(karmaMessage("jreicher: ++"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("jreicher now has karma of 1.", (result as OperationResult.Success).payload)
    }

    @Test
    fun `colon without space is stripped from subject`() {
        val result = eventGateway.process(karmaMessage("jreicher:++"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("jreicher now has karma of 1.", (result as OperationResult.Success).payload)
    }

    @Test
    fun `comma completion suffix is stripped from subject`() {
        val result = eventGateway.process(karmaMessage("jreicher, ++"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("jreicher now has karma of 1.", (result as OperationResult.Success).payload)
    }

    @Test
    fun `neutral karma displays correctly`() {
        eventGateway.process(karmaMessage("balanced++"))
        eventGateway.process(karmaMessage("balanced--"))
        val result = eventGateway.process(karmaMessage("karma balanced"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("balanced has neutral karma.", (result as OperationResult.Success).payload)
    }
}
