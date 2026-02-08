/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.PasswordResetRequest
import com.enigmastation.streampack.blog.model.PasswordResetResponse
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.ServiceBindingRepository
import com.enigmastation.streampack.core.service.Operation
import java.security.SecureRandom
import org.springframework.messaging.Message
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/** Admin-initiated password reset that generates a temporary password */
@Component
class PasswordResetOperation(
    private val serviceBindingRepository: ServiceBindingRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is PasswordResetRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as PasswordResetRequest
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val principal = provenance.user ?: return OperationResult.Error("Not authenticated")
        val serviceId = provenance.serviceId ?: return OperationResult.Error("No service context")

        if (principal.role != Role.ADMIN && principal.role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Insufficient privileges")
        }

        val binding =
            serviceBindingRepository.resolve(provenance.protocol, serviceId, request.username)
                ?: return OperationResult.Error("Binding not found")

        val temporaryPassword = generateRandomPassword()
        val hash = passwordEncoder.encode(temporaryPassword)!!
        val updatedMetadata = binding.metadata.toMutableMap()
        updatedMetadata["passwordHash"] = hash
        serviceBindingRepository.saveAndFlush(binding.copy(metadata = updatedMetadata))

        return OperationResult.Success(PasswordResetResponse(request.username, temporaryPassword))
    }

    private fun generateRandomPassword(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        return (1..16).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}
