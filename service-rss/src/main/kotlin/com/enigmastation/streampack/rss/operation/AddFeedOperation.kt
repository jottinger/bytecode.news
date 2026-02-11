/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.rss.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.rss.model.AddFeedOutcome
import com.enigmastation.streampack.rss.model.AddFeedRequest
import com.enigmastation.streampack.rss.service.RssSubscriptionService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Handles the "feed add <url>" text command and typed AddFeedRequest payloads */
@Component
class AddFeedOperation(private val feedService: RssSubscriptionService) :
    TranslatingOperation<AddFeedRequest>(AddFeedRequest::class) {

    override val priority: Int = 55

    override val addressed: Boolean = true

    override fun translate(payload: String, message: Message<*>): AddFeedRequest? {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("feed add ", ignoreCase = true)) return null
        val url = trimmed.removeRange(0, "feed add ".length).trim()
        if (url.isBlank()) return null
        return AddFeedRequest(url)
    }

    override fun canHandle(payload: AddFeedRequest, message: Message<*>): Boolean {
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val role = provenance?.user?.role ?: Role.GUEST
        return role >= Role.ADMIN
    }

    override fun handle(payload: AddFeedRequest, message: Message<*>): OperationOutcome {
        return when (val outcome = feedService.addFeed(payload.url)) {
            is AddFeedOutcome.Added ->
                OperationResult.Success(
                    "Added feed \"${outcome.feed.title}\" with ${outcome.entryCount} entries"
                )
            is AddFeedOutcome.AlreadyExists ->
                OperationResult.Success("Feed \"${outcome.feed.title}\" already exists")
            is AddFeedOutcome.DiscoveryFailed ->
                OperationResult.Error("No RSS/Atom feed found at ${outcome.url}")
        }
    }
}
