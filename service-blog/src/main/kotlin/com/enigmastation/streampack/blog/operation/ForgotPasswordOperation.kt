/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ForgotPasswordRequest
import com.enigmastation.streampack.core.entity.TokenType
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.EmailService
import com.enigmastation.streampack.core.service.Operation
import com.enigmastation.streampack.core.service.VerificationTokenService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/**
 * Generates a password reset token and sends it via email. Never leaks whether the email exists.
 */
@Component
class ForgotPasswordOperation(
    private val userRepository: UserRepository,
    private val verificationTokenService: VerificationTokenService,
    private val emailService: EmailService,
) : Operation {
    private val logger = LoggerFactory.getLogger(ForgotPasswordOperation::class.java)

    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean = message.payload is ForgotPasswordRequest

    override fun execute(message: Message<*>): OperationResult {
        val request = message.payload as ForgotPasswordRequest

        val user = userRepository.findByEmail(request.email)
        if (user != null && !user.deleted) {
            val token = verificationTokenService.createToken(user, TokenType.PASSWORD_RESET)
            emailService.sendPasswordResetEmail(user.email, token.token)
        } else {
            logger.debug(
                "Forgot password requested for unknown or deleted email: {}",
                request.email,
            )
        }

        return OperationResult.Success(
            "If an account with that email exists, a reset link has been sent"
        )
    }
}
