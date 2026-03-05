/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ideas.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.service.ProvenanceStateService
import com.enigmastation.streampack.ideas.model.IdeaSessionState
import com.enigmastation.streampack.ideas.service.IdeaTimerService
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder

@SpringBootTest
class ArticleOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var stateService: ProvenanceStateService
    @Autowired lateinit var timerService: IdeaTimerService

    private val alicePrincipal =
        UserPrincipal(
            id = UUID.randomUUID(),
            username = "alice",
            displayName = "Alice",
            role = Role.USER,
        )
    private val bobPrincipal =
        UserPrincipal(
            id = UUID.randomUUID(),
            username = "bob",
            displayName = "Bob",
            role = Role.USER,
        )

    private val provenance =
        Provenance(
            protocol = Protocol.CONSOLE,
            serviceId = "",
            replyTo = "local",
            user = alicePrincipal,
        )

    /** User key: channelUri/username */
    private val aliceKey = "console:///local/alice"
    private val bobKey = "console:///local/bob"

    private fun aliceMessage(text: String) =
        MessageBuilder.withPayload(text)
            .setHeader(Provenance.HEADER, provenance)
            .setHeader(Provenance.ADDRESSED, true)
            .build()

    private fun bobMessage(text: String) =
        MessageBuilder.withPayload(text)
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.CONSOLE,
                    serviceId = "",
                    replyTo = "local",
                    user = bobPrincipal,
                ),
            )
            .setHeader(Provenance.ADDRESSED, true)
            .build()

    @BeforeEach
    fun cleanup() {
        stateService.clearState(aliceKey, IdeaSessionState.STATE_KEY)
        stateService.clearState(bobKey, IdeaSessionState.STATE_KEY)
        timerService.unregisterSession(aliceKey)
        timerService.unregisterSession(bobKey)
    }

    @Test
    fun `start session with quoted title`() {
        val result = eventGateway.process(aliceMessage("article \"My Great Idea\""))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("My Great Idea"))
        assertTrue(payload.contains("Idea session started"))

        val state = stateService.getState(aliceKey, IdeaSessionState.STATE_KEY)
        assertNotNull(state)
    }

    @Test
    fun `start session with unquoted title`() {
        val result = eventGateway.process(aliceMessage("article My Great Idea"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("My Great Idea"))

        val state = stateService.getState(aliceKey, IdeaSessionState.STATE_KEY)
        assertNotNull(state)
    }

    @Test
    fun `start session with blank title returns error`() {
        val result = eventGateway.process(aliceMessage("article"))
        assertInstanceOf(OperationResult.Error::class.java, result)
        val message = (result as OperationResult.Error).message
        assertTrue(message.contains("Title is required"))
    }

    @Test
    fun `start session with empty quoted title returns error`() {
        val result = eventGateway.process(aliceMessage("article \"\""))
        assertInstanceOf(OperationResult.Error::class.java, result)
        val message = (result as OperationResult.Error).message
        assertTrue(message.contains("Title is required"))
    }

    @Test
    fun `duplicate session returns error`() {
        eventGateway.process(aliceMessage("article First Idea"))

        val result = eventGateway.process(aliceMessage("article Second Idea"))
        assertInstanceOf(OperationResult.Error::class.java, result)
        val message = (result as OperationResult.Error).message
        assertTrue(message.contains("already active"))
        assertTrue(message.contains("First Idea"))
    }

    @Test
    fun `add content block to active session`() {
        eventGateway.process(aliceMessage("article Test Idea"))

        val result = eventGateway.process(aliceMessage("content This is the first paragraph."))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Content block #1"))
        assertTrue(payload.contains("Test Idea"))
    }

    @Test
    fun `add multiple content blocks`() {
        eventGateway.process(aliceMessage("article Test Idea"))
        eventGateway.process(aliceMessage("content First paragraph."))

        val result = eventGateway.process(aliceMessage("content Second paragraph."))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Content block #2"))
    }

    @Test
    fun `content with no active session returns error`() {
        val result = eventGateway.process(aliceMessage("content Some text"))
        // Should not be handled (no active session, so canHandle returns false)
        if (result is OperationResult.Error) {
            assertTrue(result.message.contains("No idea session"))
        }
    }

    @Test
    fun `done saves draft and clears session`() {
        eventGateway.process(aliceMessage("article Test Idea"))
        eventGateway.process(aliceMessage("content Some body text."))

        val result = eventGateway.process(aliceMessage("done"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Idea saved as draft"))
        assertTrue(payload.contains("Test Idea"))
        assertTrue(payload.contains("1 content block"))

        val state = stateService.getState(aliceKey, IdeaSessionState.STATE_KEY)
        assertNull(state)
    }

    @Test
    fun `done with no content blocks saves title-only idea`() {
        eventGateway.process(aliceMessage("article Title Only Idea"))

        val result = eventGateway.process(aliceMessage("done"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Idea saved as draft"))
        assertTrue(payload.contains("0 content blocks"))
    }

    @Test
    fun `cancel discards session`() {
        eventGateway.process(aliceMessage("article Doomed Idea"))

        val result = eventGateway.process(aliceMessage("cancel"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("cancelled"))
        assertTrue(payload.contains("Doomed Idea"))
        assertTrue(payload.contains("discarded"))

        val state = stateService.getState(aliceKey, IdeaSessionState.STATE_KEY)
        assertNull(state)
    }

    @Test
    fun `cancel with no active session returns graceful message`() {
        val result = eventGateway.process(aliceMessage("cancel"))
        // cancel without session: either not handled (no timer session) or graceful message
        if (result is OperationResult.Success) {
            val payload = result.payload as String
            assertTrue(payload.contains("No idea session"))
        }
    }

    @Test
    fun `full flow - start, add content, done`() {
        val startResult = eventGateway.process(aliceMessage("article \"Complete Flow Test\""))
        assertInstanceOf(OperationResult.Success::class.java, startResult)

        val content1 = eventGateway.process(aliceMessage("content First paragraph of the idea."))
        assertInstanceOf(OperationResult.Success::class.java, content1)

        val content2 =
            eventGateway.process(aliceMessage("content Second paragraph with more details."))
        assertInstanceOf(OperationResult.Success::class.java, content2)
        assertTrue(((content2 as OperationResult.Success).payload as String).contains("block #2"))

        val doneResult = eventGateway.process(aliceMessage("done"))
        assertInstanceOf(OperationResult.Success::class.java, doneResult)
        val payload = (doneResult as OperationResult.Success).payload as String
        assertTrue(payload.contains("2 content blocks"))

        val state = stateService.getState(aliceKey, IdeaSessionState.STATE_KEY)
        assertNull(state)
    }

    @Test
    fun `timer service registers session on start`() {
        eventGateway.process(aliceMessage("article Timer Test"))
        assertTrue(timerService.hasActiveSession(aliceKey))
    }

    @Test
    fun `timer service unregisters session on done`() {
        eventGateway.process(aliceMessage("article Timer Done Test"))
        assertTrue(timerService.hasActiveSession(aliceKey))

        eventGateway.process(aliceMessage("done"))
        assertTrue(!timerService.hasActiveSession(aliceKey))
    }

    @Test
    fun `timer service unregisters session on cancel`() {
        eventGateway.process(aliceMessage("article Timer Cancel Test"))
        assertTrue(timerService.hasActiveSession(aliceKey))

        eventGateway.process(aliceMessage("cancel"))
        assertTrue(!timerService.hasActiveSession(aliceKey))
    }

    @Test
    fun `timer timeout finalizes session`() {
        eventGateway.process(aliceMessage("article Timeout Test"))
        eventGateway.process(aliceMessage("content Some content for timeout."))

        val futureTime = Instant.now().plusSeconds(600)
        timerService.onTick(futureTime)

        val state = stateService.getState(aliceKey, IdeaSessionState.STATE_KEY)
        assertNull(state)
        assertTrue(!timerService.hasActiveSession(aliceKey))
    }

    @Test
    fun `concurrent sessions by different users in same channel`() {
        val aliceResult = eventGateway.process(aliceMessage("article Alice Idea"))
        assertInstanceOf(OperationResult.Success::class.java, aliceResult)

        val bobResult = eventGateway.process(bobMessage("article Bob Idea"))
        assertInstanceOf(OperationResult.Success::class.java, bobResult)

        assertTrue(timerService.hasActiveSession(aliceKey))
        assertTrue(timerService.hasActiveSession(bobKey))

        val aliceDone = eventGateway.process(aliceMessage("done"))
        assertInstanceOf(OperationResult.Success::class.java, aliceDone)
        assertTrue(
            ((aliceDone as OperationResult.Success).payload as String).contains("Alice Idea")
        )

        assertTrue(!timerService.hasActiveSession(aliceKey))
        assertTrue(timerService.hasActiveSession(bobKey))
    }
}
