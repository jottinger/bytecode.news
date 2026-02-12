/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.specs.service

import com.enigmastation.streampack.specs.model.SpecRequest
import com.enigmastation.streampack.specs.model.SpecType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Fetches spec titles from RFC Editor, OpenJDK, and JCP websites */
@Service
class SpecLookupService {

    private val logger = LoggerFactory.getLogger(SpecLookupService::class.java)

    /** Look up the title for a spec request, or null if the spec does not exist */
    fun lookup(request: SpecRequest): String? {
        return lookupUrl(request.url, request.type)
    }

    /** Fetch a spec page by URL and extract its title */
    fun lookupUrl(url: String, type: SpecType): String? {
        val body = fetchBody(url) ?: return null
        return extractTitle(body, type)
    }

    private fun fetchBody(url: String): String? {
        return try {
            val client =
                HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
            val request =
                HttpRequest.newBuilder()
                    .uri(URI(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (compatible; Nevet/1.0; +https://jvm.news)")
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                response.body()
            } else {
                logger.debug("HTTP {} fetching {}", response.statusCode(), url)
                null
            }
        } catch (e: Exception) {
            logger.debug("Failed to fetch {}: {}", url, e.message)
            null
        }
    }

    private fun extractTitle(html: String, type: SpecType): String? {
        return try {
            val document = Jsoup.parse(html)
            val element = document.selectFirst(type.cssSelector) ?: return null
            val raw = element.text().trim()
            if (raw.isBlank()) return null
            cleanTitle(raw, type)
        } catch (e: Exception) {
            logger.debug("Failed to extract title: {}", e.message)
            null
        }
    }

    /** Strip common prefixes that duplicate the spec identifier */
    private fun cleanTitle(raw: String, type: SpecType): String {
        return when (type) {
            SpecType.RFC -> raw.removePrefix("RFC ").replace(Regex("^\\d+\\s*-\\s*"), "").trim()
            SpecType.JEP -> raw.removePrefix("JEP ").replace(Regex("^\\d+:\\s*"), "").trim()
            SpecType.JSR -> raw.removePrefix("JSR ").replace(Regex("^\\d+:\\s*"), "").trim()
        }.replace("TM", "")
    }
}
