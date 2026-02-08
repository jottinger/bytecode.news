/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ChangePasswordRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.ServiceBindingRepository
import com.enigmastation.streampack.core.service.Operation
import org.springframework.messaging.Message
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/** Changes the authenticated user's password in their service binding metadata */
@Component
class ChangePasswordOperation(
    private val serviceBindingRepository: ServiceBindingRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is ChangePasswordRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as ChangePasswordRequest
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance")
        val principal = provenance.user ?: return OperationResult.Error("Not authenticated")
        val serviceId = provenance.serviceId ?: return OperationResult.Error("No service context")

        val binding =
            serviceBindingRepository.resolve(provenance.protocol, serviceId, principal.username)
                ?: return OperationResult.Error("Binding not found")

        val storedHash =
            binding.metadata["passwordHash"] as? String
                ?: return OperationResult.Error("No password set")

        if (!passwordEncoder.matches(request.oldPassword, storedHash)) {
            return OperationResult.Error("Invalid current password")
        }

        val newHash = passwordEncoder.encode(request.newPassword)!!
        val updatedMetadata = binding.metadata.toMutableMap()
        updatedMetadata["passwordHash"] = newHash
        serviceBindingRepository.saveAndFlush(binding.copy(metadata = updatedMetadata))

        return OperationResult.Success("Password changed successfully")
    }
}
