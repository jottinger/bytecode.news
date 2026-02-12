/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import org.jsoup.Jsoup
import org.springframework.stereotype.Component

/** TitleFetcher that uses PageFetcher to retrieve HTML and Jsoup to extract the title */
@Component
class HtmlTitleFetcher(private val pageFetcher: PageFetcher) : TitleFetcher {

    override fun fetchTitle(url: String): String? {
        val body = pageFetcher.fetch(url) ?: return null
        val title = Jsoup.parse(body).title()
        return title.ifBlank { null }
    }
}
