/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.service

import com.enigmastation.streampack.github.entity.GitHubRepo
import com.enigmastation.streampack.github.model.GitHubIssueEvent
import com.enigmastation.streampack.github.model.GitHubPullRequestEvent
import com.enigmastation.streampack.github.model.GitHubReleaseEvent
import com.enigmastation.streampack.github.repository.GitHubSubscriptionRepository
import com.enigmastation.streampack.polling.service.EgressNotifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Formats webhook events and emits notifications identical to polling output */
@Service
class GitHubWebhookService(
    private val subscriptionRepository: GitHubSubscriptionRepository,
    private val notifier: EgressNotifier,
) {

    private val logger = LoggerFactory.getLogger(GitHubWebhookService::class.java)

    fun handleIssue(repo: GitHubRepo, event: GitHubIssueEvent) {
        if (event.action != "opened") return
        val issue = event.issue
        val message =
            "[${repo.fullName()}] New issue #${issue.number}: ${issue.title} - ${issue.htmlUrl}"
        fanOut(repo, message)
    }

    fun handlePullRequest(repo: GitHubRepo, event: GitHubPullRequestEvent) {
        if (event.action != "opened") return
        val pr = event.pullRequest
        val message = "[${repo.fullName()}] New PR #${pr.number}: ${pr.title} - ${pr.htmlUrl}"
        fanOut(repo, message)
    }

    fun handleRelease(repo: GitHubRepo, event: GitHubReleaseEvent) {
        if (event.action != "published") return
        val release = event.release
        val message = "[${repo.fullName()}] New release ${release.tagName} - ${release.htmlUrl}"
        fanOut(repo, message)
    }

    fun handlePing(repo: GitHubRepo, zen: String?) {
        val suffix = if (zen.isNullOrBlank()) "" else " ($zen)"
        val message = "[${repo.fullName()}] Webhook ping received - setup verified.$suffix"
        fanOut(repo, message)
    }

    private fun fanOut(repo: GitHubRepo, message: String) {
        val subscriptions = subscriptionRepository.findByRepoAndActiveTrue(repo)
        if (subscriptions.isEmpty()) return
        subscriptions.forEach { subscription ->
            notifier.send(message, subscription.destinationUri)
        }
    }
}
