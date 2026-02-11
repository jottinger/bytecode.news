/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.operation

import com.enigmastation.streampack.core.model.AlterUserRequest
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/**
 * Modifies a user's fields. Replaces ChangeRoleOperation with richer authorization.
 *
 * Authorization hierarchy:
 * - ADMIN: can modify users with role < ADMIN, can set role to values < ADMIN
 * - SUPER_ADMIN: can modify any user, can set role to any value, cannot change own role
 * - Neither can change their own role
 */
@Component
class AlterUserOperation(private val userRepository: UserRepository) :
    TypedOperation<AlterUserRequest>(AlterUserRequest::class) {

    override val priority = 50

    override fun handle(payload: AlterUserRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val principal = provenance.user ?: return OperationResult.Error("Not authenticated")

        if (principal.role < Role.ADMIN) {
            return OperationResult.Error("Insufficient privileges")
        }

        val targetUser =
            userRepository.findByUsername(payload.username)
                ?: return OperationResult.Error("User not found")

        // Prevent self-role-change
        if (payload.role != null && targetUser.username == principal.username) {
            return OperationResult.Error("Cannot change own role")
        }

        // ADMIN authorization checks
        if (principal.role == Role.ADMIN) {
            if (targetUser.role >= Role.ADMIN) {
                return OperationResult.Error("Insufficient privileges")
            }
            if (payload.role != null && payload.role >= Role.ADMIN) {
                return OperationResult.Error("Insufficient privileges")
            }
        }

        return try {
            val updated =
                targetUser.copy(
                    username = payload.newUsername ?: targetUser.username,
                    email = payload.email ?: targetUser.email,
                    displayName = payload.displayName ?: targetUser.displayName,
                    role = payload.role ?: targetUser.role,
                )
            val saved = userRepository.saveAndFlush(updated)
            OperationResult.Success(saved.toUserPrincipal())
        } catch (e: Exception) {
            logger.warn("Failed to alter user {}: {}", payload.username, e.message)
            OperationResult.Error("Username already exists")
        }
    }
}
