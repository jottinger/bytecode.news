/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.karma.controller

import com.enigmastation.streampack.karma.dto.KarmaLeaderboardEntryResponse
import com.enigmastation.streampack.karma.dto.KarmaLeaderboardResponse
import com.enigmastation.streampack.karma.model.KarmaLeaderboardEntry
import com.enigmastation.streampack.karma.service.KarmaService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Read-only REST endpoints for karma leaderboard browsing. */
@RestController
@RequestMapping("/karma")
class KarmaController(private val karmaService: KarmaService) {

    /** Returns both top and bottom karma slices so clients can render a compact dashboard view. */
    @GetMapping("/leaderboard", produces = ["application/json"])
    fun leaderboard(@RequestParam(defaultValue = "10") limit: Int): KarmaLeaderboardResponse {
        val bounded = limit.coerceIn(1, 100)
        return KarmaLeaderboardResponse(
            top = karmaService.getLeaderboard(bounded, ascending = false).map { it.toResponse() },
            bottom = karmaService.getLeaderboard(bounded, ascending = true).map { it.toResponse() },
            limit = bounded,
        )
    }

    private fun KarmaLeaderboardEntry.toResponse() =
        KarmaLeaderboardEntryResponse(
            subject = subject,
            score = score,
            upvotes = upvotes,
            downvotes = downvotes,
            lastUpdated = lastUpdated,
        )
}
