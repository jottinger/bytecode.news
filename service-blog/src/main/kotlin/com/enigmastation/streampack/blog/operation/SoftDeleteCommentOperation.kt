/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ContentOperationConfirmation
import com.enigmastation.streampack.blog.model.SoftDeleteCommentRequest
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Admin soft-deletes a comment, preserving thread structure */
@Component
class SoftDeleteCommentOperation(private val commentRepository: CommentRepository) :
    TypedOperation<SoftDeleteCommentRequest>(SoftDeleteCommentRequest::class) {

    private val logger = LoggerFactory.getLogger(SoftDeleteCommentOperation::class.java)

    override val priority = 50

    override fun handle(payload: SoftDeleteCommentRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance context")

        val principal = provenance.user ?: return OperationResult.Error("Authentication required")

        if (principal.role != Role.ADMIN && principal.role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Admin access required")
        }

        val comment =
            commentRepository.findById(payload.id).orElse(null)
                ?: return OperationResult.Error("Comment not found")

        if (comment.deleted) {
            return OperationResult.Error("Comment is already deleted")
        }

        commentRepository.save(comment.copy(deleted = true, updatedAt = Instant.now()))

        logger.info("Comment soft-deleted: {}", comment.id)

        return OperationResult.Success(
            ContentOperationConfirmation(id = comment.id, message = "Comment deleted")
        )
    }
}
