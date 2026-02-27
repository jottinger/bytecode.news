/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.matches.model

/** Per-channel 21-matches game state, serialized to JSONB via ProvenanceStateService */
data class MatchesGameState(val remaining: Int) {

    companion object {
        const val STATE_KEY = "21-matches"
        const val INITIAL_MATCHES = 21
    }
}
