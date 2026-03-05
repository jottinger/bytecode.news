/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ContentListResponse
import com.enigmastation.streampack.blog.model.ContentSummary
import com.enigmastation.streampack.blog.model.FindDraftsRequest
import com.enigmastation.streampack.blog.repository.PostCategoryRepository
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.PostTagRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
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
    private val postTagRepository: PostTagRepository,
    private val postCategoryRepository: PostCategoryRepository,
) : TypedOperation<FindDraftsRequest>(FindDraftsRequest::class) {

    override val priority = 50

    override fun handle(payload: FindDraftsRequest, message: Message<*>): OperationOutcome {
        requireRole(message, Role.ADMIN)?.let {
            return it
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
                    tags = postTagRepository.findByPost(post.id).map { it.tag.name },
                    categories = postCategoryRepository.findByPost(post.id).map { it.category.name },
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
