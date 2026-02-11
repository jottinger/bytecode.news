/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Comment
import com.enigmastation.streampack.blog.model.CommentDetail
import com.enigmastation.streampack.blog.model.CreateCommentRequest
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.service.MarkdownRenderingService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.TypedOperation
import java.time.Instant
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Creates a new comment on a blog post, optionally nested under a parent comment */
@Component
class CreateCommentOperation(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val markdownRenderingService: MarkdownRenderingService,
) : TypedOperation<CreateCommentRequest>(CreateCommentRequest::class) {

    override val priority = 50

    override fun handle(payload: CreateCommentRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance context")

        val principal = provenance.user ?: return OperationResult.Error("Authentication required")

        val user =
            userRepository.findActiveById(principal.id)
                ?: return OperationResult.Error("User not found")

        if (!user.emailVerified) {
            return OperationResult.Error("Email verification required")
        }

        if (payload.markdownSource.isBlank()) {
            return OperationResult.Error("Comment content is required")
        }

        val post =
            postRepository.findActiveById(payload.postId)
                ?: return OperationResult.Error("Post not found")

        val parentComment =
            if (payload.parentCommentId != null) {
                val parent =
                    commentRepository.findById(payload.parentCommentId).orElse(null)
                        ?: return OperationResult.Error("Parent comment not found")
                if (parent.post.id != post.id) {
                    return OperationResult.Error("Parent comment belongs to a different post")
                }
                parent
            } else {
                null
            }

        val renderedHtml = markdownRenderingService.render(payload.markdownSource)
        val now = Instant.now()

        val comment =
            commentRepository.save(
                Comment(
                    post = post,
                    author = user,
                    parentComment = parentComment,
                    markdownSource = payload.markdownSource,
                    renderedHtml = renderedHtml,
                    createdAt = now,
                    updatedAt = now,
                )
            )

        logger.info("Comment created: {} on post {}", comment.id, post.id)

        return OperationResult.Success(
            CommentDetail(
                id = comment.id,
                postId = post.id,
                authorDisplayName = user.displayName,
                renderedHtml = comment.renderedHtml,
                createdAt = comment.createdAt,
            )
        )
    }
}
