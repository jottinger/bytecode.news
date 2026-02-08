/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ChangeRoleRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.Operation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Changes a user's role, restricted to SUPER_ADMIN only */
@Component
class ChangeRoleOperation(private val userRepository: UserRepository) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is ChangeRoleRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as ChangeRoleRequest
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val principal = provenance.user ?: return OperationResult.Error("Not authenticated")

        if (principal.role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Insufficient privileges")
        }

        if (request.username == principal.username) {
            return OperationResult.Error("Cannot change own role")
        }

        val targetUser =
            userRepository.findByUsername(request.username)
                ?: return OperationResult.Error("User not found")

        val updatedUser = userRepository.saveAndFlush(targetUser.copy(role = request.newRole))
        return OperationResult.Success(updatedUser.toUserPrincipal())
    }
}
