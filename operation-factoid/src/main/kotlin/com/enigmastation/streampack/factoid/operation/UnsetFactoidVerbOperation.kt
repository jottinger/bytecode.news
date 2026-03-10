/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.operation

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import com.enigmastation.streampack.factoid.model.FactoidUnsetRequest
import com.enigmastation.streampack.factoid.service.FactoidService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Handles "factoid unset selector.attribute". */
@Component
class UnsetFactoidVerbOperation(private val factoidService: FactoidService) :
    TranslatingOperation<FactoidUnsetRequest>(FactoidUnsetRequest::class) {

    override val priority: Int = 70
    override val addressed: Boolean = true
    override val operationGroup: String = "factoid"

    override fun translate(payload: String, message: Message<*>): FactoidUnsetRequest? {
        val compressed = payload.compress()
        if (!compressed.startsWith("factoid unset ", ignoreCase = true)) return null
        val argument = compressed.substringAfter("factoid unset ", "").trim()
        if (argument.isBlank()) return null

        val parsed = GetFactoidOperation.parseQuery(argument)
        if (parsed.attribute == FactoidAttributeType.UNKNOWN) {
            return FactoidUnsetRequest(parsed.selector, FactoidAttributeType.UNKNOWN)
        }
        return FactoidUnsetRequest(parsed.selector, parsed.attribute)
    }

    override fun handle(payload: FactoidUnsetRequest, message: Message<*>): OperationOutcome {
        if (payload.attribute == FactoidAttributeType.UNKNOWN) {
            return OperationResult.Error("Usage: unset <selector>.<attribute>")
        }
        if (!payload.attribute.mutable) {
            return OperationResult.Error(
                "Attribute '${payload.attribute.name.lowercase()}' cannot be unset."
            )
        }

        return when (
            val result = factoidService.deleteAttribute(payload.selector, payload.attribute)
        ) {
            is FactoidService.DeleteResult.Ok ->
                OperationResult.Success(
                    "ok, unset ${payload.attribute.name.lowercase()} on ${payload.selector}."
                )
            is FactoidService.DeleteResult.Locked ->
                OperationResult.Error("Factoid '${result.selector}' is locked.")
            is FactoidService.DeleteResult.NotFound ->
                OperationResult.Error(
                    "No ${payload.attribute.name.lowercase()} attribute found for '${payload.selector}'."
                )
        }
    }
}
