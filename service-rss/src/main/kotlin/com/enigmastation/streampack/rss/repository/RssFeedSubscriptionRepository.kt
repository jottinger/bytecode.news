/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.rss.repository

import com.enigmastation.streampack.rss.entity.RssFeed
import com.enigmastation.streampack.rss.entity.RssFeedSubscription
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface RssFeedSubscriptionRepository : JpaRepository<RssFeedSubscription, UUID> {
    fun findByFeedAndDestinationUri(feed: RssFeed, destinationUri: String): RssFeedSubscription?

    fun findByFeedAndActiveTrue(feed: RssFeed): List<RssFeedSubscription>

    fun findByDestinationUriAndActiveTrue(destinationUri: String): List<RssFeedSubscription>
}
