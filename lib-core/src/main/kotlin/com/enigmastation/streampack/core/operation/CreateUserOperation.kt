/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.operation

import com.enigmastation.streampack.core.model.CreateUserRequest
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Provisions a new user account. Restricted to SUPER_ADMIN. */
@Component
class CreateUserOperation(private val userRegistrationService: UserRegistrationService) :
    TypedOperation<CreateUserRequest>(CreateUserRequest::class) {

    private val logger = LoggerFactory.getLogger(CreateUserOperation::class.java)

    override val priority = 50

    override fun handle(payload: CreateUserRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val principal = provenance.user ?: return OperationResult.Error("Not authenticated")

        if (principal.role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Insufficient privileges")
        }

        return try {
            val created =
                userRegistrationService.createUser(
                    username = payload.username,
                    email = payload.email,
                    displayName = payload.displayName,
                    role = payload.role,
                )
            OperationResult.Success(created)
        } catch (e: Exception) {
            logger.warn("Failed to create user {}: {}", payload.username, e.message)
            OperationResult.Error("Username already exists")
        }
    }
}
