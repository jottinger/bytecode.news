/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ContentDetail
import com.enigmastation.streampack.blog.model.EditContentRequest
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.blog.service.MarkdownRenderingService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import java.time.Instant
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Modifies an existing post's title and markdown content */
@Component
class EditContentOperation(
    private val postRepository: PostRepository,
    private val slugRepository: SlugRepository,
    private val commentRepository: CommentRepository,
    private val markdownRenderingService: MarkdownRenderingService,
) : TypedOperation<EditContentRequest>(EditContentRequest::class) {

    override fun handle(payload: EditContentRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance context")

        val principal = provenance.user ?: return OperationResult.Error("Authentication required")

        if (payload.title.isBlank()) {
            return OperationResult.Error("Title is required")
        }
        if (payload.markdownSource.isBlank()) {
            return OperationResult.Error("Content is required")
        }

        val post =
            postRepository.findActiveByIdWithAuthor(payload.id)
                ?: return OperationResult.Error("Post not found")

        val isAdmin = principal.role == Role.ADMIN || principal.role == Role.SUPER_ADMIN
        val isAuthor = post.author != null && post.author.id == principal.id

        // Author can edit own drafts; admin can edit anything
        if (!isAdmin) {
            if (!isAuthor) {
                return OperationResult.Error("Not authorized to edit this post")
            }
            if (post.status != PostStatus.DRAFT) {
                return OperationResult.Error("Not authorized to edit this post")
            }
        }

        val renderedHtml = markdownRenderingService.render(payload.markdownSource)
        val excerpt = markdownRenderingService.excerpt(payload.markdownSource)
        val now = Instant.now()

        val updated =
            postRepository.save(
                post.copy(
                    title = payload.title,
                    markdownSource = payload.markdownSource,
                    renderedHtml = renderedHtml,
                    excerpt = excerpt,
                    updatedAt = now,
                )
            )

        val canonicalSlug = slugRepository.findCanonical(updated.id)

        logger.info("Post edited: {}", updated.id)

        return OperationResult.Success(
            ContentDetail(
                id = updated.id,
                title = updated.title,
                slug = canonicalSlug?.path ?: "",
                renderedHtml = updated.renderedHtml,
                excerpt = updated.excerpt,
                authorId = updated.author?.id,
                authorDisplayName = updated.author?.displayName ?: "Anonymous",
                status = updated.status,
                publishedAt = updated.publishedAt,
                createdAt = updated.createdAt,
                updatedAt = updated.updatedAt,
                commentCount = commentRepository.countActiveByPost(updated.id).toInt(),
            )
        )
    }
}
