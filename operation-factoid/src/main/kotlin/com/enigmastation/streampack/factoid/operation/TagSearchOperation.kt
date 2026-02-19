/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.operation

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.extensions.joinToStringWithAnd
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.factoid.model.TagSearchRequest
import com.enigmastation.streampack.factoid.service.FactoidService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Handles addressed tag search: "tag term" */
@Component
class TagSearchOperation(private val factoidService: FactoidService) :
    TranslatingOperation<TagSearchRequest>(TagSearchRequest::class) {

    override val priority: Int = 65
    override val addressed: Boolean = true

    override fun translate(payload: String, message: Message<*>): TagSearchRequest? {
        val compressed = payload.compress()
        if (!compressed.startsWith("tag ", ignoreCase = true)) return null
        val tag = compressed.substringAfter("tag ", "").trim()
        if (tag.isBlank()) return null
        return TagSearchRequest(tag)
    }

    override fun handle(payload: TagSearchRequest, message: Message<*>): OperationOutcome {
        val selectors = factoidService.searchByTag(payload.tag)
        return if (selectors.isEmpty()) {
            OperationResult.Success("No factoids found with tag '${payload.tag}'.")
        } else {
            val tildeSelectors = selectors.map { "~$it" }
            val joined = tildeSelectors.joinToStringWithAnd()
            val display =
                if (joined.length > 200) {
                    selectors.take(10).map { "~$it" }.joinToStringWithAnd()
                } else {
                    joined
                }
            OperationResult.Success("Factoids tagged '${payload.tag}': $display")
        }
    }
}
