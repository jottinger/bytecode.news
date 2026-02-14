/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import org.jsoup.Jsoup
import org.springframework.stereotype.Component

/** TitleFetcher that extracts titles from HTML, falling back to OpenGraph and Twitter meta tags */
@Component
class HtmlTitleFetcher(private val pageFetcher: PageFetcher) : TitleFetcher {

    override fun fetchTitle(url: String): String? {
        val body = pageFetcher.fetch(url) ?: return null
        val doc = Jsoup.parse(body)

        val title = doc.title().takeIf { it.isNotBlank() }
        if (title != null) return title

        val ogTitle =
            doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotBlank() }
        if (ogTitle != null) return ogTitle

        val twitterTitle =
            doc.select("meta[name=twitter:title]").attr("content").takeIf { it.isNotBlank() }
        return twitterTitle
    }
}
