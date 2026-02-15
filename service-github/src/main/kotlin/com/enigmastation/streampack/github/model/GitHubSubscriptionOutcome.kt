/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.model

import com.enigmastation.streampack.github.entity.GitHubRepo

/** Result of attempting to subscribe or unsubscribe a destination to a GitHub repository */
sealed interface GitHubSubscriptionOutcome {
    data class Subscribed(val repo: GitHubRepo) : GitHubSubscriptionOutcome

    data class Unsubscribed(val repo: GitHubRepo) : GitHubSubscriptionOutcome

    data class AlreadySubscribed(val repo: GitHubRepo) : GitHubSubscriptionOutcome

    data class NotSubscribed(val repo: GitHubRepo) : GitHubSubscriptionOutcome

    data class RepoNotFound(val ownerRepo: String) : GitHubSubscriptionOutcome
}
