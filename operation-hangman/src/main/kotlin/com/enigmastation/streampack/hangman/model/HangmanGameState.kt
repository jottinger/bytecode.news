/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.hangman.model

/** Per-channel hangman game state, serialized to JSONB via ProvenanceStateService */
data class HangmanGameState(
    val word: String,
    val guessedLetters: Set<Char> = emptySet(),
    val livesRemaining: Int = 6,
) {
    val maskedWord: String
        get() = word.map { if (it in guessedLetters) it else '_' }.joinToString(" ")

    val isWon: Boolean
        get() = word.all { it in guessedLetters }

    val isLost: Boolean
        get() = livesRemaining == 0

    val isOver: Boolean
        get() = isWon || isLost

    /** Serializes to Map for ProvenanceStateService storage */
    fun toMap(): Map<String, Any> =
        mapOf(
            "word" to word,
            "guessedLetters" to guessedLetters.map { it.toString() },
            "livesRemaining" to livesRemaining,
        )

    companion object {
        const val STATE_KEY = "hangman"
        const val MAX_LIVES = 6

        /** Deserializes from ProvenanceStateService Map */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(data: Map<String, Any>): HangmanGameState {
            val letters =
                (data["guessedLetters"] as? List<String>)?.map { it[0] }?.toSet() ?: emptySet()
            return HangmanGameState(
                word = data["word"] as String,
                guessedLetters = letters,
                livesRemaining = (data["livesRemaining"] as Number).toInt(),
            )
        }
    }
}
