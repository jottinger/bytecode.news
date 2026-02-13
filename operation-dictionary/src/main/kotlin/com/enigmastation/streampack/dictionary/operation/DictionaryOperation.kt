/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.dictionary.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.dictionary.model.DictionaryRequest
import com.enigmastation.streampack.dictionary.service.DictionaryLookupService
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/**
 * Looks up word definitions from the Free Dictionary API.
 *
 * Acts as a factoid cache-miss handler: runs after the factoid get operation (priority 90) so that
 * known definitions are served from the factoid store. On first lookup, fetches the definition from
 * the API, caches it as a factoid via EventGateway, and returns the result.
 */
@Component
class DictionaryOperation(
    private val lookupService: DictionaryLookupService,
    private val eventGateway: EventGateway,
) : TranslatingOperation<DictionaryRequest>(DictionaryRequest::class) {

    override val priority: Int = 95

    override val addressed: Boolean = true

    private val pattern = Regex("^define\\s+(.+)$", RegexOption.IGNORE_CASE)

    override fun translate(payload: String, message: Message<*>): DictionaryRequest? {
        val match = pattern.matchEntire(payload.trim()) ?: return null
        val word = match.groupValues[1].trim().lowercase()
        if (word.isBlank()) return null
        return DictionaryRequest(word)
    }

    override fun handle(payload: DictionaryRequest, message: Message<*>): OperationOutcome? {
        val result = lookupService.lookup(payload.word) ?: return null
        val description = "${result.word} (${result.partOfSpeech}): ${result.definition}"

        seedFactoid(payload.selector, description)

        return OperationResult.Success("${payload.selector}: $description")
    }

    /** Create a factoid so future lookups hit the factoid cache instead of the API */
    private fun seedFactoid(selector: String, value: String) {
        try {
            val factoidText = "$selector=<reply>$value"
            val msg = MessageBuilder.withPayload(factoidText).build()
            eventGateway.process(msg)
        } catch (e: Exception) {
            logger.debug("Could not cache definition as factoid: {}", e.message)
        }
    }
}
