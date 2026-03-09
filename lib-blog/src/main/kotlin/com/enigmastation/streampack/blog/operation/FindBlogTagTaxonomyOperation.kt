/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.repository.PostTagRepository
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.taxonomy.model.FindBlogTagTaxonomyRequest
import com.enigmastation.streampack.taxonomy.model.TaxonomyTermCount
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Provides blog tag counts for taxonomy aggregation. */
@Component
class FindBlogTagTaxonomyOperation(private val postTagRepository: PostTagRepository) :
    TypedOperation<FindBlogTagTaxonomyRequest>(FindBlogTagTaxonomyRequest::class) {

    override fun handle(
        payload: FindBlogTagTaxonomyRequest,
        message: Message<*>,
    ): OperationOutcome {
        val tags = postTagRepository.findTagCounts().map { TaxonomyTermCount(it.name, it.count) }
        return OperationResult.Success(tags)
    }
}
