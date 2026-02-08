/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.RegistrationRequest
import com.enigmastation.streampack.core.entity.TokenType
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.EmailService
import com.enigmastation.streampack.core.service.Operation
import com.enigmastation.streampack.core.service.UserRegistrationService
import com.enigmastation.streampack.core.service.VerificationTokenService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/** Registers a new user account through the event system and sends a verification email */
@Component
class RegistrationOperation(
    private val userRegistrationService: UserRegistrationService,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val userRepository: UserRepository,
    private val verificationTokenService: VerificationTokenService,
    private val emailService: EmailService,
) : Operation {
    private val logger = LoggerFactory.getLogger(RegistrationOperation::class.java)

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

            val user = userRepository.findByUsername(principal.username)
            if (user != null) {
                val token = verificationTokenService.createToken(user, TokenType.EMAIL_VERIFICATION)
                emailService.sendVerificationEmail(user.email, token.token)
            } else {
                logger.warn("User {} not found after registration", principal.username)
            }

            OperationResult.Success(principal)
        } catch (e: Exception) {
            OperationResult.Error("Username already taken")
        }
    }
}
