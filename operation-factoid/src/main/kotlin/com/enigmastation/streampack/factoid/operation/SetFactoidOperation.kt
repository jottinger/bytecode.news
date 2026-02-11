/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.operation

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import com.enigmastation.streampack.factoid.model.FactoidSetRequest
import com.enigmastation.streampack.factoid.service.FactoidService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Handles addressed factoid assignment: "selector=value" or "selector.attribute=value" */
@Component
class SetFactoidOperation(private val factoidService: FactoidService) :
    TranslatingOperation<FactoidSetRequest>(FactoidSetRequest::class) {

    override val priority: Int = 75
    override val addressed: Boolean = true

    override fun translate(payload: String, message: Message<*>): FactoidSetRequest? {
        return parseInput(payload)
    }

    override fun handle(payload: FactoidSetRequest, message: Message<*>): OperationOutcome? {
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val senderNick =
            message.headers["nick"] as? String ?: provenance?.user?.username ?: "unknown"

        val result =
            factoidService.save(payload.selector, payload.attribute, payload.value, senderNick)
        return when (result) {
            is FactoidService.SaveResult.Ok -> {
                logger.debug("Factoid '{}' updated by {}", payload.selector, senderNick)
                OperationResult.Success("ok, $senderNick: updated ${payload.selector}.")
            }
            is FactoidService.SaveResult.Locked ->
                OperationResult.Error("Factoid '${payload.selector}' is locked.")
        }
    }

    companion object {
        /** Parses "selector[.attribute]=value" into a FactoidSetRequest */
        fun parseInput(input: String): FactoidSetRequest? {
            val eqIndex = input.indexOf('=')
            if (eqIndex < 1) return null

            val selectorAttribute = input.substring(0, eqIndex).compress()
            val value = input.substring(eqIndex + 1).compress()
            if (selectorAttribute.isBlank() || value.isBlank()) return null

            val lastDotIndex = selectorAttribute.lastIndexOf('.')
            val (selector, attribute) =
                if (lastDotIndex != -1) {
                    val potentialAttribute =
                        selectorAttribute.substring(lastDotIndex + 1).trim().lowercase()
                    if (potentialAttribute in FactoidAttributeType.knownAttributes) {
                        val attr = FactoidAttributeType.knownAttributes[potentialAttribute]!!
                        if (attr.mutable) {
                            selectorAttribute.substring(0, lastDotIndex).trim() to attr
                        } else {
                            return null
                        }
                    } else {
                        selectorAttribute to FactoidAttributeType.TEXT
                    }
                } else {
                    selectorAttribute to FactoidAttributeType.TEXT
                }

            if (selector.isBlank()) return null
            return FactoidSetRequest(selector.lowercase(), attribute, value)
        }
    }
}
