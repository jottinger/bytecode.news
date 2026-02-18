/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import java.net.URI
import java.net.URLEncoder
import java.util.Optional
import java.util.concurrent.TimeUnit
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * TitleFetcher that extracts titles from HTML. Prefers og:title (designed for link sharing) over
 * the HTML title tag (often contains only site branding without JS rendering). Results are cached
 * to avoid refetching large pages for repeated URLs.
 */
@Component
class HtmlTitleFetcher(private val pageFetcher: PageFetcher) : TitleFetcher {
    private val logger = LoggerFactory.getLogger(HtmlTitleFetcher::class.java)

    private val titleCache: LoadingCache<String, Optional<String>> =
        CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(
                CacheLoader.from { url -> Optional.ofNullable(loadTitle(url!!)) }
            )

    companion object {
        private val YOUTUBE_HOSTS =
            setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

        private const val YOUTUBE_OEMBED = "https://www.youtube.com/oembed?format=json&url="
    }

    override fun fetchTitle(url: String): String? {
        return titleCache.get(url).orElse(null)
    }

    /** Resolves the title for a URL, trying YouTube oembed first then HTML scraping */
    private fun loadTitle(url: String): String? {
        val youtubeTitle = fetchYouTubeTitle(url)
        if (youtubeTitle != null) return youtubeTitle

        val body = pageFetcher.fetch(url)
        if (body == null) {
            logger.debug("No body returned for {}", url)
            return null
        }
        val doc = Jsoup.parse(body)

        val ogTitle =
            doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotBlank() }
        if (ogTitle != null) {
            logger.debug("Found og:title for {}: {}", url, ogTitle)
            return ogTitle
        } else {
            logger.debug("No og:title found for {}", url)
        }

        val twitterTitle =
            doc.select("meta[name=twitter:title]").attr("content").takeIf { it.isNotBlank() }
        if (twitterTitle != null) {
            logger.debug("Found twitter:title for {}: {}", url, twitterTitle)
            return twitterTitle
        }

        val title = doc.title().takeIf { it.isNotBlank() }
        if (title != null) {
            logger.debug("Found <title> for {}: {}", url, title)
            return title
        }

        logger.debug("No title found in any source for {}", url)
        return null
    }

    /** Uses YouTube's oembed API to get the video title without scraping HTML */
    private fun fetchYouTubeTitle(url: String): String? {
        val host =
            try {
                URI(url).host?.lowercase()
            } catch (_: Exception) {
                return null
            }
        if (host == null || host !in YOUTUBE_HOSTS) return null

        val oembedUrl = YOUTUBE_OEMBED + URLEncoder.encode(url, Charsets.UTF_8)
        logger.debug("Fetching YouTube title via oembed for {}", url)
        val json = pageFetcher.fetch(oembedUrl) ?: return null
        return try {
            val title = ObjectMapper().readTree(json).get("title")?.asText()
            if (title.isNullOrBlank()) {
                logger.debug("YouTube oembed returned no title for {}", url)
                null
            } else {
                logger.debug("Found YouTube title via oembed for {}: {}", url, title)
                title
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse YouTube oembed response for {}: {}", url, e.message)
            null
        }
    }
}
