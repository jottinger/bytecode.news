/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.CommentDetail
import com.enigmastation.streampack.blog.model.EditCommentRequest
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.blog.service.MarkdownRenderingService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import java.time.Duration
import java.time.Instant
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Edits a comment; author can edit within 5 minutes, admin can edit anytime */
@Component
class EditCommentOperation(
    private val commentRepository: CommentRepository,
    private val markdownRenderingService: MarkdownRenderingService,
) : TypedOperation<EditCommentRequest>(EditCommentRequest::class) {

    override val priority = 50

    override fun handle(payload: EditCommentRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance context")

        val principal = provenance.user ?: return OperationResult.Error("Authentication required")

        val comment =
            commentRepository.findActiveByIdWithAuthor(payload.id)
                ?: return OperationResult.Error("Comment not found")

        val isAdmin = principal.role == Role.ADMIN || principal.role == Role.SUPER_ADMIN
        val isAuthor = comment.author.id == principal.id

        if (!isAdmin) {
            if (!isAuthor) {
                return OperationResult.Error("Not authorized to edit this comment")
            }
            val elapsed = Duration.between(comment.createdAt, Instant.now())
            if (elapsed.toMinutes() >= 5) {
                return OperationResult.Error("Edit window has expired")
            }
        }

        if (payload.markdownSource.isBlank()) {
            return OperationResult.Error("Comment content is required")
        }

        val renderedHtml = markdownRenderingService.render(payload.markdownSource)
        val now = Instant.now()

        val updated =
            commentRepository.save(
                comment.copy(
                    markdownSource = payload.markdownSource,
                    renderedHtml = renderedHtml,
                    updatedAt = now,
                )
            )

        logger.info("Comment edited: {}", updated.id)

        return OperationResult.Success(
            CommentDetail(
                id = updated.id,
                postId = updated.post.id,
                authorDisplayName = updated.author.displayName,
                renderedHtml = updated.renderedHtml,
                createdAt = updated.createdAt,
            )
        )
    }
}
