/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.DeleteAccountRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.Operation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Soft-deletes a user account, with self-deletion and admin-deletion support */
@Component
class DeleteAccountOperation(private val userRepository: UserRepository) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is DeleteAccountRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as DeleteAccountRequest
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val principal = provenance.user ?: return OperationResult.Error("Not authenticated")

        val targetUsername = request.username ?: principal.username

        // Non-admin users can only delete themselves
        if (
            targetUsername != principal.username &&
                principal.role != Role.ADMIN &&
                principal.role != Role.SUPER_ADMIN
        ) {
            return OperationResult.Error("Insufficient privileges")
        }

        val targetUser =
            userRepository.findByUsername(targetUsername)
                ?: return OperationResult.Error("User not found")

        if (targetUser.role == Role.SUPER_ADMIN && targetUsername != principal.username) {
            return OperationResult.Error("Cannot delete a super admin")
        }

        userRepository.saveAndFlush(targetUser.copy(deleted = true))
        return OperationResult.Success("Account deleted")
    }
}
