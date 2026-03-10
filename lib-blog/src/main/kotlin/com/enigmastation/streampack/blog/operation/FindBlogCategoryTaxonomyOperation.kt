/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.repository.PostCategoryRepository
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.taxonomy.model.FindBlogCategoryTaxonomyRequest
import com.enigmastation.streampack.taxonomy.model.TaxonomyTermCount
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Provides blog category counts for taxonomy aggregation. */
@Component
class FindBlogCategoryTaxonomyOperation(
    private val postCategoryRepository: PostCategoryRepository
) : TypedOperation<FindBlogCategoryTaxonomyRequest>(FindBlogCategoryTaxonomyRequest::class) {

    override fun handle(
        payload: FindBlogCategoryTaxonomyRequest,
        message: Message<*>,
    ): OperationOutcome {
        val categories =
            postCategoryRepository.findCategoryCounts().map { TaxonomyTermCount(it.name, it.count) }
        return OperationResult.Success(categories)
    }
}
