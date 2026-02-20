/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.urltitle.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.urltitle.config.UrlTitleProperties
import com.enigmastation.streampack.urltitle.service.UrlTitleService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Fetches HTML titles for URLs in text-channel messages and reports non-redundant ones */
@Component
class UrlTitleOperation(
    private val urlTitleService: UrlTitleService,
    private val properties: UrlTitleProperties,
) : TypedOperation<String>(String::class) {

    override val priority: Int = 91
    override val addressed: Boolean = false
    override val operationGroup: String = "urltitle"

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        val provenance = message.headers[Provenance.HEADER] as? Provenance ?: return false
        if (provenance.protocol !in properties.protocols) return false
        return urlTitleService.extractUrls(payload).isNotEmpty()
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome? {
        val urls = urlTitleService.extractUrls(payload)
        val titles =
            urls
                .distinct()
                .filter { !urlTitleService.isIgnoredHost(it) }
                .mapNotNull { url ->
                    val title = urlTitleService.fetchTitle(url) ?: return@mapNotNull null
                    val similarity = urlTitleService.calculateJaccardSimilarity(url, title)
                    logger.info("url: {}, title: {}, similarity: {}", url, title, similarity)
                    if (similarity >= properties.similarityThreshold) null else url to title
                }

        if (titles.isEmpty()) return null

        val response =
            if (titles.size == 1) {
                titles.first().second
            } else {
                titles.joinToString(" || ") { (url, title) -> """$url "$title"""" }
            }
        return OperationResult.Success(response)
    }
}
