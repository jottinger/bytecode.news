/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.UnsuspendAccountRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserStatus
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.Operation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Restores a suspended user account to active status */
@Component
class UnsuspendAccountOperation(private val userRepository: UserRepository) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean =
        message.payload is UnsuspendAccountRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as UnsuspendAccountRequest
        requireRole(message, Role.ADMIN)?.let {
            return it
        }

        val targetUser =
            userRepository.findByUsername(request.username)
                ?: return OperationResult.Error("User not found")

        if (!targetUser.isSuspended()) {
            return OperationResult.Error("User is not suspended")
        }

        userRepository.saveAndFlush(targetUser.copy(status = UserStatus.ACTIVE))
        return OperationResult.Success("Account unsuspended")
    }
}
