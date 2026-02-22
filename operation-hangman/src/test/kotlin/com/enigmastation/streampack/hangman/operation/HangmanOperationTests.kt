/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.hangman.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ProvenanceStateService
import com.enigmastation.streampack.hangman.model.HangmanGameState
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
class HangmanOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var stateService: ProvenanceStateService

    private val provenance =
        Provenance(protocol = Protocol.CONSOLE, serviceId = "", replyTo = "local")
    private val provenanceUri = provenance.encode()

    private fun hangmanMessage(text: String) =
        MessageBuilder.withPayload(text)
            .setHeader(Provenance.HEADER, provenance)
            .setHeader(Provenance.ADDRESSED, true)
            .build()

    @BeforeEach
    fun cleanup() {
        stateService.clearState(provenanceUri, HangmanGameState.STATE_KEY)
    }

    @Test
    fun `start new game creates state and shows masked word`() {
        val result = eventGateway.process(hangmanMessage("hangman"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Hangman:"))
        assertTrue(payload.contains("6/6 lives"))

        val state = stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
        assertNotNull(state)
    }

    @Test
    fun `hangman with active game shows current state`() {
        eventGateway.process(hangmanMessage("hangman"))
        val result = eventGateway.process(hangmanMessage("hangman"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("Hangman:"))
    }

    @Test
    fun `guess correct letter updates state`() {
        // Seed a known word so we can test deterministically
        val state = HangmanGameState(word = "apple")
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())

        val result = eventGateway.process(hangmanMessage("hangman a"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("'a' is in the word"))
        assertTrue(payload.contains("6/6 lives"))
    }

    @Test
    fun `guess incorrect letter decrements lives`() {
        val state = HangmanGameState(word = "apple")
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())

        val result = eventGateway.process(hangmanMessage("hangman z"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("No 'z'"))
        assertTrue(payload.contains("5/6 lives"))
    }

    @Test
    fun `guess already guessed letter returns message`() {
        val state = HangmanGameState(word = "apple", guessedLetters = setOf('a'))
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())

        val result = eventGateway.process(hangmanMessage("hangman a"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("already guessed 'a'"))
    }

    @Test
    fun `correct solve wins the game`() {
        val state = HangmanGameState(word = "apple")
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())

        val result = eventGateway.process(hangmanMessage("hangman solve apple"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("You got it"))
        assertTrue(payload.contains("apple"))

        val cleared = stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
        assertNull(cleared)
    }

    @Test
    fun `incorrect solve decrements lives`() {
        val state = HangmanGameState(word = "apple")
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())

        val result = eventGateway.process(hangmanMessage("hangman solve orange"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("is not the word"))

        val updated = stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
        assertNotNull(updated)
        assertEquals(5, (updated!!["livesRemaining"] as Number).toInt())
    }

    @Test
    fun `concede reveals word and clears state`() {
        val state = HangmanGameState(word = "apple")
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())

        val result = eventGateway.process(hangmanMessage("hangman concede"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("concede"))
        assertTrue(payload.contains("apple"))

        val cleared = stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
        assertNull(cleared)
    }

    @Test
    fun `concede with no active game returns graceful message`() {
        val result = eventGateway.process(hangmanMessage("hangman concede"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("No game in progress"))
    }

    @Test
    fun `losing all lives ends game`() {
        val state = HangmanGameState(word = "apple", livesRemaining = 1)
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())

        val result = eventGateway.process(hangmanMessage("hangman z"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("No lives left"))
        assertTrue(payload.contains("apple"))

        val cleared = stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
        assertNull(cleared)
    }

    @Test
    fun `winning by guessing all letters`() {
        val state = HangmanGameState(word = "cat", guessedLetters = setOf('c', 'a'))
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())

        val result = eventGateway.process(hangmanMessage("hangman t"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("You got it"))
        assertTrue(payload.contains("cat"))

        val cleared = stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
        assertNull(cleared)
    }

    @Test
    fun `guess with no active game returns error`() {
        val result = eventGateway.process(hangmanMessage("hangman a"))
        assertInstanceOf(OperationResult.Error::class.java, result)
        val message = (result as OperationResult.Error).message
        assertTrue(message.contains("No game in progress"))
    }

    @Test
    fun `solve with no active game returns error`() {
        val result = eventGateway.process(hangmanMessage("hangman solve test"))
        assertInstanceOf(OperationResult.Error::class.java, result)
        val message = (result as OperationResult.Error).message
        assertTrue(message.contains("No game in progress"))
    }

    @Test
    fun `non-hangman message is not handled`() {
        val result = eventGateway.process(hangmanMessage("calc 2+3"))
        // Should not be handled by HangmanOperation
        if (result is OperationResult.Success) {
            val payload = result.payload as String
            assertTrue(!payload.contains("Hangman"))
        }
    }

    @Test
    fun `incorrect solve on last life ends game`() {
        val state = HangmanGameState(word = "apple", livesRemaining = 1)
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())

        val result = eventGateway.process(hangmanMessage("hangman solve wrong"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("No lives left"))
        assertTrue(payload.contains("apple"))

        val cleared = stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
        assertNull(cleared)
    }
}
