/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.rss.model

import com.rometools.rome.feed.synd.SyndFeed

/** Holds the resolved feed URL and parsed feed content from discovery */
data class DiscoveryResult(val feedUrl: String, val feed: SyndFeed)
