/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.rss.repository

import com.enigmastation.streampack.rss.entity.RssFeed
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface RssFeedRepository : JpaRepository<RssFeed, UUID> {
    fun findByFeedUrl(feedUrl: String): RssFeed?

    fun findAllByActiveTrue(): List<RssFeed>
}
