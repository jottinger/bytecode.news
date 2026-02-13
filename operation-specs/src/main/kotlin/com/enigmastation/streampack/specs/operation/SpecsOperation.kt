/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.specs.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.specs.model.SpecRequest
import com.enigmastation.streampack.specs.model.SpecType
import com.enigmastation.streampack.specs.service.SpecLookupService
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/**
 * Looks up RFC, JEP, and JSR specifications by number.
 *
 * Acts as a factoid cache-miss handler: runs after the factoid get operation (priority 90) so that
 * known specs are served from the factoid store. On first lookup, fetches the spec title from the
 * source website, caches it as a factoid via EventGateway, and returns the result.
 */
@Component
class SpecsOperation(
    private val lookupService: SpecLookupService,
    private val eventGateway: EventGateway,
) : TranslatingOperation<SpecRequest>(SpecRequest::class) {

    override val priority: Int = 95

    override val addressed: Boolean = true

    private val specPattern = Regex("^(rfc|jep|jsr|pep)\\s*(\\d+)$", RegexOption.IGNORE_CASE)

    override fun translate(payload: String, message: Message<*>): SpecRequest? {
        val match = specPattern.matchEntire(payload.trim()) ?: return null
        val typeName = match.groupValues[1].uppercase()
        val identifier = match.groupValues[2].toIntOrNull() ?: return null
        if (identifier <= 0) return null
        val type =
            try {
                SpecType.valueOf(typeName)
            } catch (_: IllegalArgumentException) {
                return null
            }
        return SpecRequest(type, identifier)
    }

    override fun handle(payload: SpecRequest, message: Message<*>): OperationOutcome? {
        val title = lookupService.lookup(payload) ?: return null

        val description = "$title (${payload.url})"

        seedFactoid(payload.selector, title)
        seedFactoid("${payload.selector}.url", payload.url)

        return OperationResult.Success("${payload.selector}: $description")
    }

    /** Create a factoid so future lookups hit the factoid cache instead of the web */
    private fun seedFactoid(selector: String, value: String) {
        try {
            val factoidText = "$selector=$value"
            val msg = MessageBuilder.withPayload(factoidText).build()
            eventGateway.process(msg)
        } catch (e: Exception) {
            logger.debug("Could not cache spec as factoid: {}", e.message)
        }
    }
}
