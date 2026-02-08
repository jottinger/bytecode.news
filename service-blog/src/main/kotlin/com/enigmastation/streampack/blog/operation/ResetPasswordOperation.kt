/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ResetPasswordRequest
import com.enigmastation.streampack.core.entity.TokenType
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.ServiceBindingRepository
import com.enigmastation.streampack.core.service.Operation
import com.enigmastation.streampack.core.service.VerificationTokenService
import org.springframework.messaging.Message
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/** User-facing password reset that consumes a token from email and sets a new password */
@Component
class ResetPasswordOperation(
    private val verificationTokenService: VerificationTokenService,
    private val serviceBindingRepository: ServiceBindingRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is ResetPasswordRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as ResetPasswordRequest
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val serviceId = provenance.serviceId ?: return OperationResult.Error("No service context")

        val user =
            verificationTokenService.consumeToken(request.token, TokenType.PASSWORD_RESET)
                ?: return OperationResult.Error("Invalid or expired token")

        val binding =
            serviceBindingRepository.resolve(provenance.protocol, serviceId, user.username)
                ?: return OperationResult.Error("Binding not found")

        val newHash = passwordEncoder.encode(request.newPassword)!!
        val updatedMetadata = binding.metadata.toMutableMap()
        updatedMetadata["passwordHash"] = newHash
        serviceBindingRepository.saveAndFlush(binding.copy(metadata = updatedMetadata))

        return OperationResult.Success("Password reset successfully")
    }
}
