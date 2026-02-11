/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.rss.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.rss.model.RemoveFeedOutcome
import com.enigmastation.streampack.rss.model.SubscriptionOutcome
import com.enigmastation.streampack.rss.service.RssSubscriptionService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Handles feed management commands: list, subscribe, unsubscribe, subscriptions, remove */
@Component
class FeedManagementOperation(private val feedService: RssSubscriptionService) :
    com.enigmastation.streampack.core.service.TypedOperation<String>(String::class) {

    override val priority: Int = 56

    override val addressed: Boolean = true

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        val trimmed = payload.trim().lowercase()
        // Read commands are open to everyone
        if (trimmed == "feed list" || trimmed == "feed subscriptions") return true
        // Mutation commands require ADMIN
        val isMutation =
            trimmed.startsWith("feed subscribe ") ||
                trimmed.startsWith("feed unsubscribe ") ||
                trimmed.startsWith("feed remove ")
        if (!isMutation) return false
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val role = provenance?.user?.role ?: Role.GUEST
        return role >= Role.ADMIN
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        val trimmed = payload.trim()
        val lower = trimmed.lowercase()

        return when {
            lower == "feed list" -> handleList()
            lower == "feed subscriptions" -> handleSubscriptions(message)
            lower.startsWith("feed subscribe ") -> {
                val url = trimmed.substring("feed subscribe ".length).trim()
                handleSubscribe(url, message)
            }
            lower.startsWith("feed unsubscribe ") -> {
                val url = trimmed.substring("feed unsubscribe ".length).trim()
                handleUnsubscribe(url, message)
            }
            lower.startsWith("feed remove ") -> {
                val url = trimmed.substring("feed remove ".length).trim()
                handleRemove(url, message)
            }
            else -> OperationResult.NotHandled
        }
    }

    private fun handleList(): OperationResult {
        val feeds = feedService.listFeeds()
        if (feeds.isEmpty()) {
            return OperationResult.Success("No feeds registered")
        }
        val lines =
            feeds.joinToString("\n") { feed ->
                val status = if (feed.active) "" else " [inactive]"
                "${feed.title} - ${feed.feedUrl}$status"
            }
        return OperationResult.Success(lines)
    }

    private fun handleSubscriptions(message: Message<*>): OperationResult {
        val destinationUri =
            destinationUri(message) ?: return OperationResult.Error("No provenance")
        val subscriptions = feedService.listSubscriptions(destinationUri)
        if (subscriptions.isEmpty()) {
            return OperationResult.Success("No active subscriptions for this channel")
        }
        val lines = subscriptions.joinToString("\n") { it.feed.title + " - " + it.feed.feedUrl }
        return OperationResult.Success(lines)
    }

    private fun handleSubscribe(url: String, message: Message<*>): OperationResult {
        if (url.isBlank()) return OperationResult.Error("Usage: feed subscribe <url>")
        val destinationUri =
            destinationUri(message) ?: return OperationResult.Error("No provenance")
        return when (val outcome = feedService.subscribe(url, destinationUri)) {
            is SubscriptionOutcome.Subscribed ->
                OperationResult.Success("Subscribed to \"${outcome.feed.title}\"")
            is SubscriptionOutcome.AlreadySubscribed ->
                OperationResult.Success("Already subscribed to \"${outcome.feed.title}\"")
            is SubscriptionOutcome.FeedNotFound ->
                OperationResult.Error("No registered feed found for ${outcome.url}")
            is SubscriptionOutcome.Unsubscribed,
            is SubscriptionOutcome.NotSubscribed -> OperationResult.Error("Unexpected outcome")
        }
    }

    private fun handleUnsubscribe(url: String, message: Message<*>): OperationResult {
        if (url.isBlank()) return OperationResult.Error("Usage: feed unsubscribe <url>")
        val destinationUri =
            destinationUri(message) ?: return OperationResult.Error("No provenance")
        return when (val outcome = feedService.unsubscribe(url, destinationUri)) {
            is SubscriptionOutcome.Unsubscribed ->
                OperationResult.Success("Unsubscribed from \"${outcome.feed.title}\"")
            is SubscriptionOutcome.NotSubscribed ->
                OperationResult.Error("Not subscribed to \"${outcome.feed.title}\"")
            is SubscriptionOutcome.FeedNotFound ->
                OperationResult.Error("No registered feed found for ${outcome.url}")
            is SubscriptionOutcome.Subscribed,
            is SubscriptionOutcome.AlreadySubscribed -> OperationResult.Error("Unexpected outcome")
        }
    }

    private fun handleRemove(url: String, message: Message<*>): OperationResult {
        if (url.isBlank()) return OperationResult.Error("Usage: feed remove <url>")
        return when (val outcome = feedService.removeFeed(url)) {
            is RemoveFeedOutcome.Removed ->
                OperationResult.Success(
                    "Removed feed \"${outcome.feed.title}\" " +
                        "(${outcome.subscriptionsDeactivated} subscriptions deactivated)"
                )
            is RemoveFeedOutcome.FeedNotFound ->
                OperationResult.Error("No registered feed found for ${outcome.url}")
            is RemoveFeedOutcome.AlreadyInactive ->
                OperationResult.Success("Feed \"${outcome.feed.title}\" is already inactive")
        }
    }

    /** Encode the message's provenance as a destination URI */
    private fun destinationUri(message: Message<*>): String? {
        val provenance = message.headers[Provenance.HEADER] as? Provenance ?: return null
        return provenance.encode()
    }
}
