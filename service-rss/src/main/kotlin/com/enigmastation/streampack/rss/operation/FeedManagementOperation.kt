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
        if (
            trimmed == "feed list" ||
                trimmed == "feed subscriptions" ||
                trimmed.startsWith("feed subscriptions for ")
        ) {
            return true
        }
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
            lower == "feed subscriptions" || lower.startsWith("feed subscriptions for ") ->
                handleSubscriptions(trimmed, message)
            lower.startsWith("feed subscribe ") -> {
                val remainder = trimmed.substring("feed subscribe ".length).trim()
                handleSubscribe(remainder, message)
            }
            lower.startsWith("feed unsubscribe ") -> {
                val remainder = trimmed.substring("feed unsubscribe ".length).trim()
                handleUnsubscribe(remainder, message)
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

    private fun handleSubscriptions(trimmed: String, message: Message<*>): OperationResult {
        val lower = trimmed.lowercase()
        val destinationUri =
            if (lower.startsWith("feed subscriptions for ")) {
                val uri = trimmed.substring("feed subscriptions for ".length).trim()
                if (uri.isBlank()) {
                    return OperationResult.Error("Usage: feed subscriptions for <provenance-uri>")
                }
                uri
            } else {
                destinationUri(message) ?: return OperationResult.Error("No provenance")
            }
        val subscriptions = feedService.listSubscriptions(destinationUri)
        if (subscriptions.isEmpty()) {
            return OperationResult.Success("No active subscriptions for this channel")
        }
        val lines = subscriptions.joinToString("\n") { it.feed.title + " - " + it.feed.feedUrl }
        return OperationResult.Success(lines)
    }

    private fun handleSubscribe(remainder: String, message: Message<*>): OperationResult {
        if (remainder.isBlank()) {
            return OperationResult.Error("Usage: feed subscribe <url> [to <provenance-uri>]")
        }
        val (url, destinationUri) =
            parseUrlAndTarget(remainder, message) ?: return OperationResult.Error("No provenance")
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

    private fun handleUnsubscribe(remainder: String, message: Message<*>): OperationResult {
        if (remainder.isBlank()) {
            return OperationResult.Error("Usage: feed unsubscribe <url> [to <provenance-uri>]")
        }
        val (url, destinationUri) =
            parseUrlAndTarget(remainder, message) ?: return OperationResult.Error("No provenance")
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

    /**
     * Splits remainder into a feed URL and destination URI. When the remainder contains " to
     * <uri>", the explicit URI is used as the destination. Otherwise the message provenance is the
     * destination. Returns null when no destination can be determined.
     */
    private fun parseUrlAndTarget(remainder: String, message: Message<*>): Pair<String, String>? {
        val toIndex = remainder.lowercase().lastIndexOf(" to ")
        if (toIndex >= 0) {
            val url = remainder.substring(0, toIndex).trim()
            val target = remainder.substring(toIndex + " to ".length).trim()
            if (url.isNotBlank() && target.isNotBlank()) {
                return url to target
            }
        }
        val destinationUri = destinationUri(message) ?: return null
        return remainder to destinationUri
    }

    /** Encode the message's provenance as a destination URI */
    private fun destinationUri(message: Message<*>): String? {
        val provenance = message.headers[Provenance.HEADER] as? Provenance ?: return null
        return provenance.encode()
    }
}
