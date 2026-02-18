/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Default PageFetcher that retrieves page content over HTTP, handling gzip responses */
@Component
class HttpPageFetcher : PageFetcher {
    private val logger = LoggerFactory.getLogger(HttpPageFetcher::class.java)

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val YOUTUBE_HOSTS =
            setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

        private fun isYouTubeHost(host: String?): Boolean =
            host != null && host.lowercase() in YOUTUBE_HOSTS
    }

    override fun fetch(url: String): String? {
        return try {
            val client =
                HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
            val uri = URI(url)
            val builder =
                HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    )
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip")

            // YouTube serves a consent interstitial to datacenter IPs without this cookie
            if (isYouTubeHost(uri.host)) {
                builder.header("Cookie", "CONSENT=PENDING+999")
            }

            val request = builder.GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val finalUri = response.uri()

            if (response.statusCode() !in 200..299) {
                logger.warn(
                    "HTTP {} fetching {} (final URI: {})",
                    response.statusCode(),
                    url,
                    finalUri,
                )
                return null
            }

            val encoding = response.headers().firstValue("Content-Encoding").orElse("")
            val inputStream: InputStream =
                if (encoding.equals("gzip", ignoreCase = true)) {
                    GZIPInputStream(response.body())
                } else {
                    response.body()
                }

            val body = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            logger.debug(
                "Fetched {} -> {} (status={}, encoding={}, body={} chars)",
                url,
                finalUri,
                response.statusCode(),
                encoding.ifEmpty { "none" },
                body.length,
            )
            body
        } catch (e: Exception) {
            logger.warn("Failed to fetch {}: {}", url, e.message)
            null
        }
    }
}
