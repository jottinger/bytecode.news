/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.github.model.DeliveryMode
import com.enigmastation.streampack.github.model.GitHubSubscriptionOutcome
import com.enigmastation.streampack.github.model.RemoveRepoOutcome
import com.enigmastation.streampack.github.service.GitHubSubscriptionService
import com.enigmastation.streampack.polling.operation.PollingSourceManagementOperation
import org.springframework.stereotype.Component

/** Handles github list/subscribe/unsubscribe/subscriptions/remove commands */
@Component
class GitHubManagementOperation(private val subscriptionService: GitHubSubscriptionService) :
    PollingSourceManagementOperation() {

    override val commandPrefix: String = "github"
    override val priority: Int = 56
    override val operationGroup: String = "github"

    override fun onList(): OperationOutcome {
        val repos = subscriptionService.listRepos()
        if (repos.isEmpty()) {
            return OperationResult.Success("No GitHub repositories registered")
        }
        val lines =
            repos.joinToString("\n") { repo ->
                val status = buildString {
                    if (!repo.active) append(" [inactive]")
                    if (repo.deliveryMode == DeliveryMode.WEBHOOK) append(" [webhook]")
                }
                "${repo.fullName()}$status"
            }
        return OperationResult.Success(lines)
    }

    override fun onSubscribe(identifier: String, destinationUri: String): OperationOutcome {
        logger.debug(
            "onSubscribe called: identifier='{}', destination='{}'",
            identifier,
            destinationUri,
        )
        return when (val outcome = subscriptionService.subscribe(identifier, destinationUri)) {
            is GitHubSubscriptionOutcome.Subscribed ->
                OperationResult.Success("Subscribed to ${outcome.repo.fullName()}")
            is GitHubSubscriptionOutcome.AlreadySubscribed ->
                OperationResult.Success("Already subscribed to ${outcome.repo.fullName()}")
            is GitHubSubscriptionOutcome.RepoNotFound ->
                OperationResult.Error("No registered repository found for ${outcome.ownerRepo}")
            is GitHubSubscriptionOutcome.Unsubscribed,
            is GitHubSubscriptionOutcome.NotSubscribed ->
                OperationResult.Error("Unexpected outcome")
        }
    }

    override fun onUnsubscribe(identifier: String, destinationUri: String): OperationOutcome {
        return when (val outcome = subscriptionService.unsubscribe(identifier, destinationUri)) {
            is GitHubSubscriptionOutcome.Unsubscribed ->
                OperationResult.Success("Unsubscribed from ${outcome.repo.fullName()}")
            is GitHubSubscriptionOutcome.NotSubscribed ->
                OperationResult.Error("Not subscribed to ${outcome.repo.fullName()}")
            is GitHubSubscriptionOutcome.RepoNotFound ->
                OperationResult.Error("No registered repository found for ${outcome.ownerRepo}")
            is GitHubSubscriptionOutcome.Subscribed,
            is GitHubSubscriptionOutcome.AlreadySubscribed ->
                OperationResult.Error("Unexpected outcome")
        }
    }

    override fun onSubscriptions(destinationUri: String): OperationOutcome {
        val subscriptions = subscriptionService.listSubscriptions(destinationUri)
        if (subscriptions.isEmpty()) {
            return OperationResult.Success("No active subscriptions for this channel")
        }
        val lines = subscriptions.joinToString("\n") { it.repo.fullName() }
        return OperationResult.Success(lines)
    }

    override fun onRemove(identifier: String): OperationOutcome {
        return when (val outcome = subscriptionService.removeRepo(identifier)) {
            is RemoveRepoOutcome.Removed ->
                OperationResult.Success(
                    "Removed ${outcome.repo.fullName()} " +
                        "(${outcome.subscriptionsDeactivated} subscriptions deactivated)"
                )
            is RemoveRepoOutcome.RepoNotFound ->
                OperationResult.Error("No registered repository found for ${outcome.ownerRepo}")
            is RemoveRepoOutcome.AlreadyInactive ->
                OperationResult.Success("${outcome.repo.fullName()} is already inactive")
        }
    }
}
