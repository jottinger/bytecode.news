/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ContentOperationConfirmation
import com.enigmastation.streampack.blog.model.SoftDeleteCommentRequest
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import java.time.Instant
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Admin soft-deletes a comment, preserving thread structure */
@Component
class SoftDeleteCommentOperation(private val commentRepository: CommentRepository) :
    TypedOperation<SoftDeleteCommentRequest>(SoftDeleteCommentRequest::class) {

    override val priority = 50

    override fun handle(payload: SoftDeleteCommentRequest, message: Message<*>): OperationOutcome {
        requireRole(message, Role.ADMIN)?.let {
            return it
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
