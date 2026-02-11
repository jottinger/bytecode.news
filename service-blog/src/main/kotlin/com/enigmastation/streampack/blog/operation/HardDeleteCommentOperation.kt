/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ContentOperationConfirmation
import com.enigmastation.streampack.blog.model.HardDeleteCommentRequest
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import jakarta.persistence.EntityManager
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Admin permanently deletes a comment; DB cascades to child comments */
@Component
class HardDeleteCommentOperation(
    private val commentRepository: CommentRepository,
    private val entityManager: EntityManager,
) : TypedOperation<HardDeleteCommentRequest>(HardDeleteCommentRequest::class) {

    override val priority = 50

    override fun handle(payload: HardDeleteCommentRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance context")

        val principal = provenance.user ?: return OperationResult.Error("Authentication required")

        if (principal.role != Role.ADMIN && principal.role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Admin access required")
        }

        if (!commentRepository.existsById(payload.id)) {
            return OperationResult.Error("Comment not found")
        }

        commentRepository.hardDeleteById(payload.id)
        entityManager.clear()

        logger.info("Comment permanently removed: {}", payload.id)

        return OperationResult.Success(
            ContentOperationConfirmation(id = payload.id, message = "Comment permanently removed")
        )
    }
}
