/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.repository

import com.enigmastation.streampack.github.entity.GitHubRepo
import com.enigmastation.streampack.github.entity.GitHubSubscription
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface GitHubSubscriptionRepository : JpaRepository<GitHubSubscription, UUID> {
    fun findByRepoAndDestinationUri(repo: GitHubRepo, destinationUri: String): GitHubSubscription?

    fun findByRepoAndActiveTrue(repo: GitHubRepo): List<GitHubSubscription>

    fun findByDestinationUriAndActiveTrue(destinationUri: String): List<GitHubSubscription>
}
