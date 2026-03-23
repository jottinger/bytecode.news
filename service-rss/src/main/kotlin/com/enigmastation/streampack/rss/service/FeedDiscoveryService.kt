/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.rss.service

import com.enigmastation.streampack.rss.config.RssProperties
import com.enigmastation.streampack.rss.model.DiscoveryResult
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Fetches a URL and discovers the RSS/Atom feed, either directly or via HTML link discovery */
@Service
class FeedDiscoveryService(private val properties: RssProperties) {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryService::class.java)

    /** Fetches a known feed URL and parses it directly, without HTML discovery fallback */
    fun fetchFeed(feedUrl: String): SyndFeed? {
        val body = fetchBody(feedUrl) ?: return null
        return tryParseFeed(feedUrl, body)
    }

    /**
     * Discover a feed from the given URL. Tries direct parsing first, then falls back to HTML
     * alternate-link discovery.
     */
    fun discover(url: String): DiscoveryResult? {
        val body = fetchBody(url)
        if (body == null) {
            logger.debug("Discovery failed: no response body from {}", url)
            return null
        }

        logger.debug("Discovery fetched {} bytes from {}", body.length, url)

        // Try direct ROME parse
        val directFeed = tryParseFeed(url, body)
        if (directFeed != null) {
            logger.debug("Direct parse succeeded for {} (title: {})", url, directFeed.title)
            return DiscoveryResult(feedUrl = url, feed = directFeed)
        }

        // Fall back to HTML link discovery
        return discoverFromHtml(url, body)
    }

    private fun fetchBody(url: String): String? {
        val client =
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds.toLong()))
                .build()

        repeat(2) { attempt ->
            try {
                val request =
                    HttpRequest.newBuilder()
                        .uri(URI(url))
                        .timeout(Duration.ofSeconds(properties.readTimeoutSeconds.toLong()))
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (compatible; Nevet/1.0; +https://bytecode.news)",
                        )
                        .header(
                            "Accept",
                            "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, */*;q=0.8",
                        )
                        .GET()
                        .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                val contentType = response.headers().firstValue("content-type").orElse("(none)")
                val body = response.body()
                logger.debug(
                    "HTTP {} from {} (content-type: {}, body: {} bytes)",
                    response.statusCode(),
                    url,
                    contentType,
                    body?.length ?: 0,
                )
                if (response.statusCode() in 200..299) {
                    return body
                } else {
                    // Return body on non-2xx if it looks like XML; some servers misconfigure status
                    // codes
                    if (body != null && body.trimStart().startsWith("<?xml", ignoreCase = true)) {
                        logger.debug(
                            "Non-2xx response contains XML, attempting parse anyway for {}",
                            url,
                        )
                        return body
                    }
                    return null
                }
            } catch (e: Exception) {
                logger.debug("Failed to fetch {} on attempt {}: {}", url, attempt + 1, e.message)
                if (attempt == 0) {
                    Thread.sleep(100)
                }
            }
        }

        return null
    }

    private fun tryParseFeed(url: String, body: String): com.rometools.rome.feed.synd.SyndFeed? {
        return try {
            val input = SyndFeedInput()
            input.isAllowDoctypes = true
            input.build(StringReader(body))
        } catch (e: Exception) {
            logger.debug("ROME parse failed for {} ({} bytes): {}", url, body.length, e.message)
            null
        }
    }

    /** Parse HTML for alternate feed links and try each one until a valid feed is found */
    private fun discoverFromHtml(baseUrl: String, html: String): DiscoveryResult? {
        val document = Jsoup.parse(html, baseUrl)
        val feedLinks =
            document.select(
                "link[rel=alternate][type=application/rss+xml], " +
                    "link[rel=alternate][type=application/atom+xml]"
            )

        for (link in feedLinks) {
            val href = link.absUrl("href")
            if (href.isBlank()) continue

            val feedBody = fetchBody(href) ?: continue
            val feed = tryParseFeed(href, feedBody)
            if (feed != null) {
                return DiscoveryResult(feedUrl = href, feed = feed)
            }
        }

        logger.debug("No feed links discovered in HTML from {}", baseUrl)
        return null
    }
}
