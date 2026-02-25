/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.blog.model.TokenRefreshRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.JwtService
import com.enigmastation.streampack.core.service.Operation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Issues a fresh JWT from a valid existing token */
@Component
class TokenRefreshOperation(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is TokenRefreshRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as TokenRefreshRequest

        val principal =
            jwtService.validateToken(request.token)
                ?: return OperationResult.Error("Invalid or expired token")

        // Verify the user still exists and is active
        userRepository.findActiveById(principal.id)
            ?: return OperationResult.Error("Invalid or expired token")

        val newToken = jwtService.generateToken(principal)
        return OperationResult.Success(LoginResponse(newToken, principal))
    }
}
