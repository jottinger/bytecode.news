/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.operation

import com.enigmastation.streampack.core.model.LinkProtocolRequest
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Binds a protocol identity to a user. Restricted to SUPER_ADMIN. */
@Component
class LinkProtocolOperation(
    private val userRepository: UserRepository,
    private val userRegistrationService: UserRegistrationService,
) : TypedOperation<LinkProtocolRequest>(LinkProtocolRequest::class) {

    private val logger = LoggerFactory.getLogger(LinkProtocolOperation::class.java)

    override val priority = 50

    override fun handle(payload: LinkProtocolRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val principal = provenance.user ?: return OperationResult.Error("Not authenticated")

        if (principal.role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Insufficient privileges")
        }

        val targetUser =
            userRepository.findByUsername(payload.username)
                ?: return OperationResult.Error("User not found")

        return try {
            userRegistrationService.linkProtocol(
                userId = targetUser.id,
                protocol = payload.protocol,
                serviceId = payload.serviceId,
                externalIdentifier = payload.externalIdentifier,
                metadata = payload.metadata,
            )
            OperationResult.Success(targetUser.toUserPrincipal())
        } catch (e: Exception) {
            logger.warn("Failed to link protocol for user {}: {}", payload.username, e.message)
            OperationResult.Error("Duplicate binding")
        }
    }
}
