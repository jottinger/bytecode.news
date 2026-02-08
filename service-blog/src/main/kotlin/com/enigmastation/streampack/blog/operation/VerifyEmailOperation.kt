/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.VerifyEmailRequest
import com.enigmastation.streampack.core.entity.TokenType
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.Operation
import com.enigmastation.streampack.core.service.VerificationTokenService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Consumes an email verification token and marks the user's email as verified */
@Component
class VerifyEmailOperation(
    private val verificationTokenService: VerificationTokenService,
    private val userRepository: UserRepository,
) : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is VerifyEmailRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as VerifyEmailRequest

        val user =
            verificationTokenService.consumeToken(request.token, TokenType.EMAIL_VERIFICATION)
                ?: return OperationResult.Error("Invalid or expired token")

        userRepository.saveAndFlush(user.copy(emailVerified = true))
        return OperationResult.Success("Email verified")
    }
}
