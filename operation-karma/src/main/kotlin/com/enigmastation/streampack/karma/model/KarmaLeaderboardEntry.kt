/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.karma.model

import java.time.LocalDate

/** One leaderboard row with decayed score and aggregate vote stats. */
data class KarmaLeaderboardEntry(
    val subject: String,
    val score: Int,
    val upvotes: Int,
    val downvotes: Int,
    val lastUpdated: LocalDate,
)
