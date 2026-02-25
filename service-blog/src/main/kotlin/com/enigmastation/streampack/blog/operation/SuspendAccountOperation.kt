/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.SuspendAccountRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserStatus
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.Operation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Suspends a user account: user cannot log in, content graph remains navigable for admin review */
@Component
class SuspendAccountOperation(private val userRepository: UserRepository) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is SuspendAccountRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as SuspendAccountRequest
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val principal = provenance.user ?: return OperationResult.Error("Not authenticated")

        if (principal.role != Role.ADMIN && principal.role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Insufficient privileges")
        }

        val targetUser =
            userRepository.findByUsername(request.username)
                ?: return OperationResult.Error("User not found")

        if (targetUser.role == Role.SUPER_ADMIN) {
            return OperationResult.Error("Cannot suspend a super admin")
        }

        if (!targetUser.isActive()) {
            return OperationResult.Error("User is not active")
        }

        userRepository.saveAndFlush(targetUser.copy(status = UserStatus.SUSPENDED))
        return OperationResult.Success("Account suspended")
    }
}
