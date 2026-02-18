/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * TitleFetcher that extracts titles from HTML. Prefers og:title (designed for link sharing) over
 * the HTML title tag (often contains only site branding without JS rendering).
 */
@Component
class HtmlTitleFetcher(private val pageFetcher: PageFetcher) : TitleFetcher {
    private val logger = LoggerFactory.getLogger(HtmlTitleFetcher::class.java)

    override fun fetchTitle(url: String): String? {
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
}
