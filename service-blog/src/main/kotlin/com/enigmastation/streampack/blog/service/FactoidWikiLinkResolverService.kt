/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.service

import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import com.enigmastation.streampack.factoid.repository.FactoidAttributeRepository
import java.net.URI
import org.springframework.stereotype.Service

@Service
class FactoidWikiLinkResolverService(
    private val factoidAttributeRepository: FactoidAttributeRepository
) : FactoidWikiLinkResolver {
    override fun resolve(selector: String): FactoidWikiLinkMetadata? {
        if (selector.isBlank()) return null
        val url =
            factoidAttributeRepository
                .findByFactoidSelectorIgnoreCaseAndAttributeType(
                    selector,
                    FactoidAttributeType.URLS,
                )
                ?.attributeValue
                ?.trim()
                .orEmpty()
        val text =
            factoidAttributeRepository
                .findByFactoidSelectorIgnoreCaseAndAttributeType(
                    selector,
                    FactoidAttributeType.TEXT,
                )
                ?.attributeValue
                ?.trim()
                .orEmpty()

        val safeUrl = firstHttpUrl(url)
        if (safeUrl == null && text.isBlank()) return null
        return FactoidWikiLinkMetadata(href = safeUrl, title = text.ifBlank { null })
    }

    private fun firstHttpUrl(urls: String): String? {
        if (urls.isBlank()) return null
        return urls
            .split(",")
            .asSequence()
            .map { it.trim() }
            .firstNotNullOfOrNull { sanitizeHttpUrl(it) }
    }

    private fun sanitizeHttpUrl(url: String): String? =
        try {
            if (url.isBlank()) return null
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase().orEmpty()
            if (scheme != "http" && scheme != "https") null else uri.toString()
        } catch (_: Exception) {
            null
        }
}
