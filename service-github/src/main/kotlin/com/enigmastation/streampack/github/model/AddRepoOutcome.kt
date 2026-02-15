/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.model

import com.enigmastation.streampack.github.entity.GitHubRepo

/** Result of attempting to add a GitHub repository */
sealed interface AddRepoOutcome {
    data class Added(
        val repo: GitHubRepo,
        val issueCount: Int,
        val prCount: Int,
        val releaseCount: Int,
    ) : AddRepoOutcome

    data class AlreadyExists(val repo: GitHubRepo) : AddRepoOutcome

    data class InvalidRepo(val ownerRepo: String, val reason: String) : AddRepoOutcome

    data class ApiFailed(val ownerRepo: String, val reason: String) : AddRepoOutcome
}
