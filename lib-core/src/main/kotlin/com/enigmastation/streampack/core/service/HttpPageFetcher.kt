/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Default PageFetcher that retrieves page content over HTTP */
@Component
class HttpPageFetcher : PageFetcher {
    private val logger = LoggerFactory.getLogger(HttpPageFetcher::class.java)

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    override fun fetch(url: String): String? {
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
                    .header("User-Agent", USER_AGENT)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    )
                    .header("Accept-Language", "en-US,en;q=0.5")
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
}
