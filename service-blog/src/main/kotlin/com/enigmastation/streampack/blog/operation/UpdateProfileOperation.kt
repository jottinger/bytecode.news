/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.UpdateProfileRequest
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Updates the authenticated user's display name */
@Component
class UpdateProfileOperation(private val userRepository: UserRepository) :
    TypedOperation<UpdateProfileRequest>(UpdateProfileRequest::class) {

    override val priority = 50

    override fun handle(payload: UpdateProfileRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val principal = provenance.user ?: return OperationResult.Error("Not authenticated")

        val trimmed = payload.displayName.trim()
        if (trimmed.isBlank() || trimmed.length > 100) {
            return OperationResult.Error("Display name must be between 1 and 100 characters")
        }

        val user =
            userRepository.findActiveById(principal.id)
                ?: return OperationResult.Error("User not found")

        val updated = user.copy(displayName = trimmed)
        userRepository.saveAndFlush(updated)

        logger.info("Profile updated for user {}: displayName -> {}", principal.username, trimmed)

        return OperationResult.Success(
            mapOf("displayName" to trimmed, "message" to "Profile updated")
        )
    }
}
