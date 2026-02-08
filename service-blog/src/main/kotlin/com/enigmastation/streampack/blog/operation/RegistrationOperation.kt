/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.RegistrationRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.Operation
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.springframework.messaging.Message
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/** Registers a new user account through the event system */
@Component
class RegistrationOperation(
    private val userRegistrationService: UserRegistrationService,
    private val passwordEncoder: BCryptPasswordEncoder,
) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is RegistrationRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as RegistrationRequest
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val serviceId = provenance?.serviceId ?: return OperationResult.Error("No service context")

        val hash = passwordEncoder.encode(request.password)!!

        return try {
            val principal =
                userRegistrationService.register(
                    username = request.username,
                    email = request.email,
                    displayName = request.displayName,
                    protocol = provenance.protocol,
                    serviceId = serviceId,
                    externalIdentifier = request.username,
                    metadata = mapOf<String, Any>("passwordHash" to hash),
                )
            OperationResult.Success(principal)
        } catch (e: Exception) {
            OperationResult.Error("Username already taken")
        }
    }
}
