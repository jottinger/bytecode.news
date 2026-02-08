/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.LoginRequest
import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.ServiceBindingRepository
import com.enigmastation.streampack.core.service.JwtService
import com.enigmastation.streampack.core.service.Operation
import org.springframework.messaging.Message
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/** Authenticates a user by username/password and returns a JWT */
@Component
class LoginOperation(
    private val serviceBindingRepository: ServiceBindingRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val jwtService: JwtService,
) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is LoginRequest

    override fun execute(message: Message<*>): OperationOutcome {
        val request = message.payload as LoginRequest
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val serviceId = provenance?.serviceId ?: return OperationResult.Error("No service context")

        val binding =
            serviceBindingRepository.resolve(provenance.protocol, serviceId, request.username)
                ?: return OperationResult.Error("Invalid credentials")

        val storedHash =
            binding.metadata["passwordHash"] as? String
                ?: return OperationResult.Error("Invalid credentials")

        if (!passwordEncoder.matches(request.password, storedHash)) {
            return OperationResult.Error("Invalid credentials")
        }

        val user = binding.user
        if (user.deleted) return OperationResult.Error("Invalid credentials")

        val principal = user.toUserPrincipal()
        val token = jwtService.generateToken(principal)
        return OperationResult.Success(LoginResponse(token, principal))
    }
}
