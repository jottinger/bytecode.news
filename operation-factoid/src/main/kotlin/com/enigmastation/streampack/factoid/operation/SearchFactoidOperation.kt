/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.operation

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.extensions.joinToStringWithAnd
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.factoid.model.FactoidSearchRequest
import com.enigmastation.streampack.factoid.service.FactoidService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Handles addressed factoid search: "search term" */
@Component
class SearchFactoidOperation(private val factoidService: FactoidService) :
    TranslatingOperation<FactoidSearchRequest>(FactoidSearchRequest::class) {

    override val priority: Int = 65
    override val addressed: Boolean = true
    override val operationGroup: String = "factoid"

    override fun translate(payload: String, message: Message<*>): FactoidSearchRequest? {
        val compressed = payload.compress()
        if (!compressed.startsWith("search ", ignoreCase = true)) return null
        val term = compressed.substringAfter("search ", "").trim()
        if (term.isBlank()) return null
        return FactoidSearchRequest(term)
    }

    override fun handle(payload: FactoidSearchRequest, message: Message<*>): OperationOutcome {
        val factoids = factoidService.searchForTerm(payload.term)
        return if (factoids.isEmpty()) {
            OperationResult.Success("No factoids found searching for '${payload.term}'.")
        } else {
            val refFactoids = factoids.map { "{{ref:$it}}" }
            val joined = refFactoids.joinToStringWithAnd()
            val display =
                if (joined.length > 200) {
                    factoids.take(10).map { "{{ref:$it}}" }.joinToStringWithAnd()
                } else {
                    joined
                }
            OperationResult.Success("Search for '${payload.term}' matched: $display")
        }
    }
}
