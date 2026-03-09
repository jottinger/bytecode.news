/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.factoid.repository.FactoidAttributeRepository
import com.enigmastation.streampack.taxonomy.model.FindFactoidTagTaxonomyRequest
import com.enigmastation.streampack.taxonomy.model.TaxonomyTermCount
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Provides factoid tag counts for taxonomy aggregation. */
@Component
class FindFactoidTagTaxonomyOperation(
    private val factoidAttributeRepository: FactoidAttributeRepository
) : TypedOperation<FindFactoidTagTaxonomyRequest>(FindFactoidTagTaxonomyRequest::class) {

    override fun handle(
        payload: FindFactoidTagTaxonomyRequest,
        message: Message<*>,
    ): OperationOutcome {
        val tags =
            factoidAttributeRepository.findTagCounts().map { TaxonomyTermCount(it.name, it.count) }
        return OperationResult.Success(tags)
    }
}
