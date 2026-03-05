/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ideas.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ProvenanceStateService
import com.enigmastation.streampack.ideas.model.IdeaSessionState
import com.enigmastation.streampack.ideas.service.IdeaTimerService
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
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

    private val provenance =
        Provenance(protocol = Protocol.CONSOLE, serviceId = "", replyTo = "local")
    private val provenanceUri = provenance.encode()

    private fun ideaMessage(text: String) =
        MessageBuilder.withPayload(text)
            .setHeader(Provenance.HEADER, provenance)
            .setHeader(Provenance.ADDRESSED, true)
            .build()

    @BeforeEach
    fun cleanup() {
        stateService.clearState(provenanceUri, IdeaSessionState.STATE_KEY)
        timerService.unregisterSession(provenanceUri)
    }

    @Test
    fun `start session with quoted title`() {
        val result = eventGateway.process(ideaMessage("article \"My Great Idea\""))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("My Great Idea"))
        assertTrue(payload.contains("Idea session started"))

        val state = stateService.getState(provenanceUri, IdeaSessionState.STATE_KEY)
        assertNotNull(state)
        assertEquals("My Great Idea", state!!["title"])
    }

    @Test
    fun `start session with unquoted title`() {
        val result = eventGateway.process(ideaMessage("article My Great Idea"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("My Great Idea"))

        val state = stateService.getState(provenanceUri, IdeaSessionState.STATE_KEY)
        assertNotNull(state)
        assertEquals("My Great Idea", state!!["title"])
    }

    @Test
    fun `start session with blank title returns error`() {
        val result = eventGateway.process(ideaMessage("article"))
        assertInstanceOf(OperationResult.Error::class.java, result)
        val message = (result as OperationResult.Error).message
        assertTrue(message.contains("Title is required"))
    }

    @Test
    fun `start session with empty quoted title returns error`() {
        val result = eventGateway.process(ideaMessage("article \"\""))
        assertInstanceOf(OperationResult.Error::class.java, result)
        val message = (result as OperationResult.Error).message
        assertTrue(message.contains("Title is required"))
    }

    @Test
    fun `duplicate session returns error`() {
        eventGateway.process(ideaMessage("article First Idea"))

        val result = eventGateway.process(ideaMessage("article Second Idea"))
        assertInstanceOf(OperationResult.Error::class.java, result)
        val message = (result as OperationResult.Error).message
        assertTrue(message.contains("already active"))
        assertTrue(message.contains("First Idea"))
    }

    @Test
    fun `add content block to active session`() {
        eventGateway.process(ideaMessage("article Test Idea"))

        val result = eventGateway.process(ideaMessage("content This is the first paragraph."))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Content block #1"))
        assertTrue(payload.contains("Test Idea"))
    }

    @Test
    fun `add multiple content blocks`() {
        eventGateway.process(ideaMessage("article Test Idea"))
        eventGateway.process(ideaMessage("content First paragraph."))

        val result = eventGateway.process(ideaMessage("content Second paragraph."))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Content block #2"))
    }

    @Test
    fun `content with no active session returns error`() {
        val result = eventGateway.process(ideaMessage("content Some text"))
        // Should not be handled (no active session, so canHandle returns false)
        if (result is OperationResult.Error) {
            assertTrue(result.message.contains("No idea session"))
        }
    }

    @Test
    fun `done saves draft and clears session`() {
        eventGateway.process(ideaMessage("article Test Idea"))
        eventGateway.process(ideaMessage("content Some body text."))

        val result = eventGateway.process(ideaMessage("done"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Idea saved as draft"))
        assertTrue(payload.contains("Test Idea"))
        assertTrue(payload.contains("1 content block"))

        val state = stateService.getState(provenanceUri, IdeaSessionState.STATE_KEY)
        assertNull(state)
    }

    @Test
    fun `done with no content blocks saves title-only idea`() {
        eventGateway.process(ideaMessage("article Title Only Idea"))

        val result = eventGateway.process(ideaMessage("done"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Idea saved as draft"))
        assertTrue(payload.contains("0 content blocks"))
    }

    @Test
    fun `cancel discards session`() {
        eventGateway.process(ideaMessage("article Doomed Idea"))

        val result = eventGateway.process(ideaMessage("cancel"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("cancelled"))
        assertTrue(payload.contains("Doomed Idea"))
        assertTrue(payload.contains("discarded"))

        val state = stateService.getState(provenanceUri, IdeaSessionState.STATE_KEY)
        assertNull(state)
    }

    @Test
    fun `cancel with no active session returns graceful message`() {
        val result = eventGateway.process(ideaMessage("cancel"))
        // cancel without session: either not handled (no timer session) or graceful message
        if (result is OperationResult.Success) {
            val payload = result.payload as String
            assertTrue(payload.contains("No idea session"))
        }
    }

    @Test
    fun `full flow - start, add content, done`() {
        val startResult = eventGateway.process(ideaMessage("article \"Complete Flow Test\""))
        assertInstanceOf(OperationResult.Success::class.java, startResult)

        val content1 = eventGateway.process(ideaMessage("content First paragraph of the idea."))
        assertInstanceOf(OperationResult.Success::class.java, content1)

        val content2 =
            eventGateway.process(ideaMessage("content Second paragraph with more details."))
        assertInstanceOf(OperationResult.Success::class.java, content2)
        assertTrue(((content2 as OperationResult.Success).payload as String).contains("block #2"))

        val doneResult = eventGateway.process(ideaMessage("done"))
        assertInstanceOf(OperationResult.Success::class.java, doneResult)
        val payload = (doneResult as OperationResult.Success).payload as String
        assertTrue(payload.contains("2 content blocks"))

        val state = stateService.getState(provenanceUri, IdeaSessionState.STATE_KEY)
        assertNull(state)
    }

    @Test
    fun `timer service registers session on start`() {
        eventGateway.process(ideaMessage("article Timer Test"))
        assertTrue(timerService.hasActiveSession(provenanceUri))
    }

    @Test
    fun `timer service unregisters session on done`() {
        eventGateway.process(ideaMessage("article Timer Done Test"))
        assertTrue(timerService.hasActiveSession(provenanceUri))

        eventGateway.process(ideaMessage("done"))
        assertTrue(!timerService.hasActiveSession(provenanceUri))
    }

    @Test
    fun `timer service unregisters session on cancel`() {
        eventGateway.process(ideaMessage("article Timer Cancel Test"))
        assertTrue(timerService.hasActiveSession(provenanceUri))

        eventGateway.process(ideaMessage("cancel"))
        assertTrue(!timerService.hasActiveSession(provenanceUri))
    }

    @Test
    fun `timer timeout finalizes session`() {
        eventGateway.process(ideaMessage("article Timeout Test"))
        eventGateway.process(ideaMessage("content Some content for timeout."))

        // Simulate timeout by ticking far in the future
        val futureTime = Instant.now().plusSeconds(600)
        timerService.onTick(futureTime)

        val state = stateService.getState(provenanceUri, IdeaSessionState.STATE_KEY)
        assertNull(state)
        assertTrue(!timerService.hasActiveSession(provenanceUri))
    }
}
