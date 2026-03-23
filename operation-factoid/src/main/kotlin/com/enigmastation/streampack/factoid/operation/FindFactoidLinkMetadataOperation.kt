/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import com.enigmastation.streampack.factoid.model.FactoidLinkMetadataResponse
import com.enigmastation.streampack.factoid.model.FindFactoidLinkMetadataRequest
import com.enigmastation.streampack.factoid.repository.FactoidAttributeRepository
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Returns shared factoid link metadata for a selector via the operation pipeline. */
@Component
class FindFactoidLinkMetadataOperation(
    private val factoidAttributeRepository: FactoidAttributeRepository
) : TypedOperation<FindFactoidLinkMetadataRequest>(FindFactoidLinkMetadataRequest::class) {

    override fun handle(
        payload: FindFactoidLinkMetadataRequest,
        message: Message<*>,
    ): OperationOutcome {
        val selector = payload.selector.trim()
        if (selector.isBlank()) {
            return OperationResult.Success(FactoidLinkMetadataResponse(selector = ""))
        }

        val text =
            factoidAttributeRepository
                .findByFactoidSelectorIgnoreCaseAndAttributeType(
                    selector,
                    FactoidAttributeType.TEXT,
                )
                ?.attributeValue
                ?.trim()
                .orEmpty()
                .ifBlank { null }

        val urls =
            factoidAttributeRepository
                .findByFactoidSelectorIgnoreCaseAndAttributeType(
                    selector,
                    FactoidAttributeType.URLS,
                )
                ?.attributeValue
                ?.trim()
                .orEmpty()
                .ifBlank { null }

        return OperationResult.Success(
            FactoidLinkMetadataResponse(selector = selector, text = text, urls = urls)
        )
    }
}
