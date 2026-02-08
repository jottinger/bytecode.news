/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ContentListResponse
import com.enigmastation.streampack.blog.model.ContentSummary
import com.enigmastation.streampack.blog.model.FindDraftsRequest
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Lists unapproved drafts for the admin review queue */
@Component
class FindDraftsOperation(
    private val postRepository: PostRepository,
    private val slugRepository: SlugRepository,
) : TypedOperation<FindDraftsRequest>(FindDraftsRequest::class) {

    override val priority = 50

    override fun handle(payload: FindDraftsRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance context")

        val principal = provenance.user ?: return OperationResult.Error("Authentication required")

        if (principal.role != Role.ADMIN && principal.role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Admin access required")
        }

        val pageResult = postRepository.findDrafts(PageRequest.of(payload.page, payload.size))

        val summaries =
            pageResult.content.map { post ->
                val canonicalSlug = slugRepository.findCanonical(post.id)
                ContentSummary(
                    id = post.id,
                    title = post.title,
                    slug = canonicalSlug?.path ?: "",
                    excerpt = post.excerpt,
                    authorDisplayName = post.author?.displayName ?: "Anonymous",
                    publishedAt = post.publishedAt,
                )
            }

        return OperationResult.Success(
            ContentListResponse(
                posts = summaries,
                page = pageResult.number,
                totalPages = pageResult.totalPages,
                totalCount = pageResult.totalElements,
            )
        )
    }
}
