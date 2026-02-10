/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.urltitle.service

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.urltitle.config.UrlTitleProperties
import com.enigmastation.streampack.urltitle.entity.IgnoredHost
import com.enigmastation.streampack.urltitle.repository.IgnoredHostRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UrlTitleService(
    private val ignoredHostRepository: IgnoredHostRepository,
    private val properties: UrlTitleProperties,
) : InitializingBean {

    private val logger = LoggerFactory.getLogger(UrlTitleService::class.java)

    private val urlPattern = Regex("https?://\\S+")

    /** Seeds default ignored hosts from configuration on startup */
    @Transactional
    override fun afterPropertiesSet() {
        properties.defaultIgnoredHosts.forEach { hostName ->
            val normalized = hostName.lowercase()
            if (ignoredHostRepository.findByHostNameIgnoreCase(normalized) == null) {
                ignoredHostRepository.save(IgnoredHost(hostName = normalized))
            }
        }
    }

    /** Fetches the HTML title from a URL, returning null on any failure */
    fun fetchTitle(url: String): String? {
        return try {
            val client =
                HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds.toLong()))
                    .build()
            val request =
                HttpRequest.newBuilder()
                    .uri(URI(url))
                    .timeout(Duration.ofSeconds(properties.readTimeoutSeconds.toLong()))
                    .header("User-Agent", "Mozilla/5.0 (compatible; Nevet/1.0; +https://jvm.news)")
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val title = Jsoup.parse(response.body()).title()
                title.ifBlank { null }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug("Failed to fetch title for {}: {}", url, e.message)
            null
        }
    }

    /** Computes Jaccard similarity between URL path tokens and title tokens */
    fun calculateJaccardSimilarity(url: String, title: String): Double {
        val cleanedUrl = cleanUrl(url)
        val urlWords = tokenize(cleanedUrl)
        val titleWords = tokenize("$title ${cleanUrl(extractHost(url))}")

        val intersection = urlWords.intersect(titleWords).size
        val union = urlWords.union(titleWords).size

        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    /** Extracts all URLs from text, cleaning trailing sentence punctuation */
    fun extractUrls(text: String): List<String> {
        return urlPattern
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ')', '>', ';', ':', '!', '?') }
            .filter { it.isNotBlank() }
            .toList()
    }

    /** Checks whether a URL's host is in the ignored list */
    @Transactional(readOnly = true)
    fun isIgnoredHost(url: String): Boolean {
        val host =
            try {
                URI(url).host?.lowercase()
            } catch (_: Exception) {
                null
            } ?: return false
        return ignoredHostRepository.findByHostNameIgnoreCase(host) != null
    }

    @Transactional
    fun addIgnoredHost(hostName: String) {
        val normalized = hostName.lowercase()
        if (ignoredHostRepository.findByHostNameIgnoreCase(normalized) == null) {
            ignoredHostRepository.save(IgnoredHost(hostName = normalized))
        }
    }

    @Transactional
    fun deleteIgnoredHost(hostName: String) {
        val normalized = hostName.lowercase()
        val existing = ignoredHostRepository.findByHostNameIgnoreCase(normalized)
        if (existing != null) {
            ignoredHostRepository.delete(existing)
        }
    }

    @Transactional(readOnly = true)
    fun findAllIgnoredHosts(): List<String> {
        return ignoredHostRepository.findAll().map { it.hostName }
    }

    companion object {
        fun tokenize(text: String): Set<String> {
            return text.lowercase().split("\\W+".toRegex()).filter { it.isNotEmpty() }.toSet()
        }

        fun extractHost(url: String): String {
            return try {
                URI(url).host ?: ""
            } catch (_: Exception) {
                ""
            }
        }

        /** Strips protocol, www, file extensions, numbers, and converts separators to spaces */
        fun cleanUrl(url: String): String =
            url.replace("https://", "")
                .replace("http://", "")
                .replace("www", "")
                .replace(".", " ")
                .replace("-", " ")
                .replace("index", "")
                .replace("html", "")
                .replace("htm", "")
                .replace("/", " ")
                .replace(Regex("[0-9]+"), "")
                .compress()
    }
}
