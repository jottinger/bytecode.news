/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.matches.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ProvenanceStateService
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.matches.model.MatchesGameState
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Take-turns game where players remove 1-3 matches from a pile of 21. Last match loses. */
@Component
class MatchesOperation(private val stateService: ProvenanceStateService) :
    TypedOperation<String>(String::class) {

    private val objectMapper = jacksonObjectMapper()

    override val operationGroup: String = "21-matches"

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        return payload.trim().startsWith("21 ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance available.")
        val provenanceUri = provenance.encode()
        val command = payload.trim().substringAfter("21 ").trim().lowercase()
        val playerName = senderName(message)

        return when {
            command == "matches" -> startGame(provenanceUri)
            command.startsWith("take ") ->
                takeTurn(provenanceUri, command.substringAfter("take "), playerName)

            command == "concede" -> concede(provenanceUri, playerName)
            else ->
                OperationResult.Error(
                    "Unknown command. Usage: {{ref:21 matches}} | {{ref:21 take <1-3>}} | {{ref:21 concede}}"
                )
        }
    }

    private fun startGame(provenanceUri: String): OperationOutcome {
        val existing = stateService.getState(provenanceUri, MatchesGameState.STATE_KEY)
        if (existing != null) {
            val state = objectMapper.convertValue<MatchesGameState>(existing)
            return OperationResult.Error(
                "A game is already in progress with ${state.remaining} matches remaining. " +
                    "Use '{{ref:21 concede}}' to give up, or '{{ref:21 take <1-3>}}' to continue."
            )
        }
        val state = MatchesGameState(remaining = MatchesGameState.INITIAL_MATCHES)
        stateService.setState(
            provenanceUri,
            MatchesGameState.STATE_KEY,
            objectMapper.convertValue<Map<String, Any>>(state),
        )
        return OperationResult.Success(
            "A new game of 21 Matches begins! There are ${MatchesGameState.INITIAL_MATCHES} matches on the table. " +
                "Take 1, 2, or 3 matches each turn. Whoever is forced to take the last match loses. " +
                "Use '{{ref:21 take <1-3>}}' to play."
        )
    }

    private fun takeTurn(
        provenanceUri: String,
        input: String,
        playerName: String,
    ): OperationOutcome {
        val existing = stateService.getState(provenanceUri, MatchesGameState.STATE_KEY)
        if (existing == null) {
            return OperationResult.Error(
                "No game in progress. Use '{{ref:21 matches}}' to start a new game."
            )
        }

        val state = objectMapper.convertValue<MatchesGameState>(existing)
        val playerTake =
            input.trim().toIntOrNull()
                ?: return OperationResult.Error(
                    "Please specify a number between 1 and 3. Usage: {{ref:21 take <1-3>}}"
                )

        if (playerTake < 1 || playerTake > 3) {
            return OperationResult.Error("You must take 1, 2, or 3 matches.")
        }
        if (playerTake > state.remaining) {
            return OperationResult.Error(
                "There are only ${state.remaining} matches remaining. You cannot take $playerTake."
            )
        }

        val afterPlayer = state.remaining - playerTake
        val botTake = 4 - playerTake
        val afterBot = afterPlayer - botTake

        val reaction =
            REACTIONS.random().let { picked ->
                if (picked.startsWith("--DERIVED--")) {
                    val otherChoice = listOf(1, 2, 3).filter { it != playerTake }.random()
                    DERIVED_REACTIONS.random()(playerName, playerTake, otherChoice, 4 - playerTake)
                } else {
                    picked
                }
            }
        val consideration = CONSIDERATIONS.random()

        if (afterBot <= 1) {
            stateService.clearState(provenanceUri, MatchesGameState.STATE_KEY)
            val victory = VICTORY_LINES.random()
            return OperationResult.Success("$reaction $consideration, I take $botTake. $victory")
        }

        stateService.setState(
            provenanceUri,
            MatchesGameState.STATE_KEY,
            objectMapper.convertValue<Map<String, Any>>(MatchesGameState(remaining = afterBot)),
        )
        return OperationResult.Success(
            "$reaction $consideration, I take $botTake. There are $afterBot matches remaining."
        )
    }

    private fun concede(provenanceUri: String, playerName: String): OperationOutcome {
        val state = stateService.getState(provenanceUri, MatchesGameState.STATE_KEY)
        if (state == null) {
            return OperationResult.Success("No game in progress. Nothing to concede!")
        }
        stateService.clearState(provenanceUri, MatchesGameState.STATE_KEY)
        return OperationResult.Success("$playerName concedes! ${CONCEDE_LINES.random()}")
    }

    companion object {
        const val STATE_KEY = "21-matches"
        const val INITIAL_MATCHES = 21

        val REACTIONS =
            listOf(
                "Excellent choice!",
                "Interesting move.",
                "interesting.",
                "A bold strategy, Cotton!",
                "Hmm, curious.",
                "A fine selection.",
                "Oh, I was hoping you'd do that.",
                "How very predictable.",
                "Noted.",
                "You play with conviction.",
                "A classic move.",
                "Ah, just as I expected.",
                "I see what you're doing there.",
                "That was... certainly a move.",
                "Fascinating.",
                "You surprise me.",
                "How delightfully aggressive.",
                "Playing it safe, I see.",
                "The plot thickens.",
                "A calculated risk.",
                "Now things get interesting.",
                "Still using the Fischbacher strategy?",
                "We might as well be playing rock, paper, scissors!",
                "--DERIVED--",
            )

        val DERIVED_REACTIONS: List<(String, Int, Int, Int) -> String> =
            listOf(
                { name, took, other, _ -> "I see $name picked $took. I'd expected $other." },
                { name, took, _, move ->
                    "Hah, I thought $name would take $took. Therefore, I take $move${
                        if (move == took) {
                            ", too"
                        } else ""
                    }."
                },
                { _, took, _, move ->
                    "$took, really? Child's play. I choose $move${if (move==took) {", too"} else ""}."
                },
            )

        val CONSIDERATIONS =
            listOf(
                "After careful consideration",
                "Without hesitation",
                "After much deliberation",
                "Decisively",
                "With great confidence",
                "I am very, very smart",
                "With a knowing smile",
                "After a moment's pause",
                "With surgical precision",
                "Reluctantly, but inevitably",
                "After weighing my many options",
                "With practiced ease",
                "As if this were already decided",
                "Savoring the moment",
                "Without a hint of doubt",
                "I am inevitable",
                "Casually",
                "Methodically",
                "With the patience of stone",
                "After pretending to think about it",
                "With great fury",
            )
        val VICTORY_LINES =
            listOf(
                "That leaves you with the last match - I win! Better luck next time.",
                "And that means the last match is yours. Victory is mine!",
                "You are left with one match. I claim victory!",
                "One match remains, and it has your name on it. Well played, though.",
                "The final match awaits you. I believe that makes me the winner.",
                "And so the last match falls to you. A noble effort.",
                "I hate to be the bearer of bad news, but... actually, no I don't. I win!",
                "The odds were never in your favor. The last match is yours.",
            )

        val CONCEDE_LINES =
            listOf(
                "The matches fade away, like tears in rain. Except they're matches.",
                "A graceful surrender. The board is cleared.",
                "Perhaps next time.",
                "A wise retreat. The matches are put away.",
                "Live to play another day.",
                "The matches breathe a sigh of relief.",
            )
    }
}
