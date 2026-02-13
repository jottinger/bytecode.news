/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.urltitle.service

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.service.TitleFetcher
import com.enigmastation.streampack.urltitle.config.UrlTitleProperties
import com.enigmastation.streampack.urltitle.entity.IgnoredHost
import com.enigmastation.streampack.urltitle.repository.IgnoredHostRepository
import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UrlTitleService(
    private val ignoredHostRepository: IgnoredHostRepository,
    private val properties: UrlTitleProperties,
    private val titleFetcher: TitleFetcher,
) : InitializingBean {

    private val logger = LoggerFactory.getLogger(UrlTitleService::class.java)

    private val urlPattern = Regex("https?://\\S+")

    /** Seeds default ignored hosts from configuration on startup */
    @Transactional
    override fun afterPropertiesSet() {
        properties.defaultIgnoredHosts.forEach { hostName ->
            val normalized = normalizeHost(hostName)
            if (ignoredHostRepository.findByHostNameIgnoreCase(normalized) == null) {
                ignoredHostRepository.save(IgnoredHost(hostName = normalized))
            }
        }
    }

    /** Delegates to the TitleFetcher to retrieve the page title */
    fun fetchTitle(url: String): String? = titleFetcher.fetchTitle(url)

    /** Computes Jaccard similarity between URL path tokens and title tokens */
    fun calculateJaccardSimilarity(url: String, title: String): Double {
        val cleanedUrl = cleanUrl(url)
        val urlWords = tokenize(cleanedUrl)
        val titleWords = tokenize(title)

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
                normalizeHost(URI(url).host ?: return false)
            } catch (_: Exception) {
                return false
            }
        return ignoredHostRepository.findByHostNameIgnoreCase(host) != null
    }

    @Transactional
    fun addIgnoredHost(hostName: String) {
        val normalized = normalizeHost(hostName)
        if (ignoredHostRepository.findByHostNameIgnoreCase(normalized) == null) {
            ignoredHostRepository.save(IgnoredHost(hostName = normalized))
        }
    }

    @Transactional
    fun deleteIgnoredHost(hostName: String) {
        val normalized = normalizeHost(hostName)
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
        /** Strips www. prefix and lowercases for consistent ignore-list matching */
        fun normalizeHost(host: String): String = host.lowercase().removePrefix("www.")

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
