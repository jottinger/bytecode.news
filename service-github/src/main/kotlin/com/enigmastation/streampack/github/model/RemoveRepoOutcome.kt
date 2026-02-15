/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.model

import com.enigmastation.streampack.github.entity.GitHubRepo

/** Result of attempting to deactivate a GitHub repository */
sealed interface RemoveRepoOutcome {
    data class Removed(val repo: GitHubRepo, val subscriptionsDeactivated: Int) : RemoveRepoOutcome

    data class RepoNotFound(val ownerRepo: String) : RemoveRepoOutcome

    data class AlreadyInactive(val repo: GitHubRepo) : RemoveRepoOutcome
}
