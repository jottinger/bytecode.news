/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.rss.service

import com.enigmastation.streampack.rss.entity.RssEntry
import com.enigmastation.streampack.rss.entity.RssFeed
import com.enigmastation.streampack.rss.model.AddFeedOutcome
import com.enigmastation.streampack.rss.repository.RssEntryRepository
import com.enigmastation.streampack.rss.repository.RssFeedRepository
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Orchestrates feed registration: discovery, dedup, storage, and entry seeding */
@Service
class RssSubscriptionService(
    private val discoveryService: FeedDiscoveryService,
    private val feedRepository: RssFeedRepository,
    private val entryRepository: RssEntryRepository,
) {

    private val logger = LoggerFactory.getLogger(RssSubscriptionService::class.java)

    /**
     * Register a feed from a URL. Discovers the feed, stores metadata, and seeds current entries.
     */
    @Transactional
    fun addFeed(url: String): AddFeedOutcome {
        val normalized = normalizeUrl(url)

        // Try discovery with normalized URL first, then original if different
        val result =
            discoveryService.discover(normalized)
                ?: if (normalized != url) discoveryService.discover(url) else null

        if (result == null) {
            logger.info("No feed discovered at {}", url)
            return AddFeedOutcome.DiscoveryFailed(url)
        }

        // Check for existing feed by the resolved feed URL
        val existing = feedRepository.findByFeedUrl(result.feedUrl)
        if (existing != null) {
            logger.debug("Feed already exists: {}", result.feedUrl)
            return AddFeedOutcome.AlreadyExists(existing)
        }

        val syndFeed = result.feed
        val feed =
            feedRepository.save(
                RssFeed(
                    feedUrl = result.feedUrl,
                    siteUrl = syndFeed.link,
                    title = syndFeed.title ?: result.feedUrl,
                    description = syndFeed.description?.take(2000),
                    lastFetchedAt = Instant.now(),
                )
            )

        // Seed all current entries to establish the baseline
        val entries =
            syndFeed.entries.mapNotNull { entry ->
                val guid = entry.uri ?: entry.link ?: return@mapNotNull null
                val link = entry.link ?: guid
                val title = entry.title ?: ""
                val publishedAt = entry.publishedDate?.toInstant()

                RssEntry(
                    feed = feed,
                    guid = guid,
                    link = link,
                    title = title.take(500),
                    publishedAt = publishedAt,
                )
            }

        entryRepository.saveAll(entries)
        logger.info("Added feed \"{}\" with {} entries", feed.title, entries.size)
        return AddFeedOutcome.Added(feed, entries.size)
    }

    /** Ensure the URL has a scheme */
    private fun normalizeUrl(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }
}
