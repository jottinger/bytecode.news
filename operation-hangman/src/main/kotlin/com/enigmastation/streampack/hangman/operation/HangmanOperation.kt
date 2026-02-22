/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.hangman.operation

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ProvenanceStateService
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.hangman.model.HangmanGameState
import com.enigmastation.streampack.hangman.service.HangmanService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Word guessing game where players reveal letters one at a time before running out of lives */
@Component
class HangmanOperation(
    private val stateService: ProvenanceStateService,
    private val hangmanService: HangmanService,
) : TypedOperation<String>(String::class) {

    override val priority: Int = 50
    override val addressed: Boolean = true
    override val operationGroup: String = "hangman"

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        val cmd = payload.compress().lowercase()
        return cmd == "hangman" || cmd.startsWith("hangman ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome? {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance available.")
        val provenanceUri = provenance.encode()
        val compressed = payload.compress()
        val args = compressed.substringAfter("hangman", "").trim()

        return when {
            args.isEmpty() -> startOrShowGame(provenanceUri)
            args.lowercase() == "concede" -> concede(provenanceUri)
            args.lowercase().startsWith("solve ") ->
                solve(provenanceUri, args.substringAfter("solve ").trim())
            args.lowercase().startsWith("block ") -> null
            args.lowercase().startsWith("unblock ") -> null
            args.length == 1 && args[0].isLetter() ->
                guessLetter(provenanceUri, args[0].lowercaseChar())
            else ->
                OperationResult.Error(
                    "Unknown hangman command. Use '{{ref:hangman}}' to start, " +
                        "'{{ref:hangman <letter>}}' to guess, or '{{ref:hangman solve <word>}}' to solve."
                )
        }
    }

    private fun startOrShowGame(provenanceUri: String): OperationOutcome {
        val existing = stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
        if (existing != null) {
            val state = HangmanGameState.fromMap(existing)
            return OperationResult.Success(formatState(state))
        }

        val word = hangmanService.selectWord()
        val state = HangmanGameState(word = word)
        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, state.toMap())
        return OperationResult.Success(
            "Hangman: ${state.maskedWord} (${state.livesRemaining}/${HangmanGameState.MAX_LIVES} lives)" +
                " -- Use '{{ref:hangman <letter>}}' to guess, '{{ref:hangman solve <word>}}' to solve."
        )
    }

    private fun guessLetter(provenanceUri: String, letter: Char): OperationOutcome {
        val existing =
            stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
                ?: return OperationResult.Error(
                    "No game in progress. Use '{{ref:hangman}}' to start a new game."
                )

        val state = HangmanGameState.fromMap(existing)

        if (letter in state.guessedLetters) {
            return OperationResult.Success("You already guessed '$letter'. ${formatState(state)}")
        }

        val correct = letter in state.word
        val updated =
            if (correct) {
                state.copy(guessedLetters = state.guessedLetters + letter)
            } else {
                state.copy(
                    guessedLetters = state.guessedLetters + letter,
                    livesRemaining = state.livesRemaining - 1,
                )
            }

        if (updated.isWon) {
            stateService.clearState(provenanceUri, HangmanGameState.STATE_KEY)
            return OperationResult.Success(
                "You got it! The word was '${state.word}'. Use '{{ref:hangman}}' to play again."
            )
        }

        if (updated.isLost) {
            stateService.clearState(provenanceUri, HangmanGameState.STATE_KEY)
            return OperationResult.Success(
                "No lives left! The word was '${state.word}'. Use '{{ref:hangman}}' to start over."
            )
        }

        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, updated.toMap())

        return if (correct) {
            OperationResult.Success(
                "Hangman: ${updated.maskedWord} " +
                    "(${updated.livesRemaining}/${HangmanGameState.MAX_LIVES} lives, " +
                    "guessed: ${formatGuessed(updated)}) -- '$letter' is in the word!"
            )
        } else {
            OperationResult.Success(
                "Hangman: ${updated.maskedWord} " +
                    "(${updated.livesRemaining}/${HangmanGameState.MAX_LIVES} lives, " +
                    "guessed: ${formatGuessed(updated)}) -- No '$letter'. " +
                    "${updated.livesRemaining} lives left."
            )
        }
    }

    private fun solve(provenanceUri: String, guess: String): OperationOutcome {
        val existing =
            stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
                ?: return OperationResult.Error(
                    "No game in progress. Use '{{ref:hangman}}' to start a new game."
                )

        val state = HangmanGameState.fromMap(existing)

        if (guess.lowercase() == state.word) {
            stateService.clearState(provenanceUri, HangmanGameState.STATE_KEY)
            return OperationResult.Success(
                "You got it! The word was '${state.word}'. Use '{{ref:hangman}}' to play again."
            )
        }

        val updated = state.copy(livesRemaining = state.livesRemaining - 1)
        if (updated.isLost) {
            stateService.clearState(provenanceUri, HangmanGameState.STATE_KEY)
            return OperationResult.Success(
                "No lives left! The word was '${state.word}'. Use '{{ref:hangman}}' to start over."
            )
        }

        stateService.setState(provenanceUri, HangmanGameState.STATE_KEY, updated.toMap())
        return OperationResult.Success(
            "'${guess.lowercase()}' is not the word. ${formatState(updated)}"
        )
    }

    private fun concede(provenanceUri: String): OperationOutcome {
        val existing =
            stateService.getState(provenanceUri, HangmanGameState.STATE_KEY)
                ?: return OperationResult.Success(
                    "No game in progress. Use '{{ref:hangman}}' to start one."
                )

        val state = HangmanGameState.fromMap(existing)
        stateService.clearState(provenanceUri, HangmanGameState.STATE_KEY)
        return OperationResult.Success(
            "You concede. The word was '${state.word}'. Use '{{ref:hangman}}' for a new game."
        )
    }

    private fun formatState(state: HangmanGameState): String {
        val guessed =
            if (state.guessedLetters.isNotEmpty()) {
                ", guessed: ${formatGuessed(state)}"
            } else {
                ""
            }
        return "Hangman: ${state.maskedWord} " +
            "(${state.livesRemaining}/${HangmanGameState.MAX_LIVES} lives$guessed)"
    }

    private fun formatGuessed(state: HangmanGameState): String =
        state.guessedLetters.sorted().joinToString(", ")
}
