/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.TestChannelConfiguration
import com.enigmastation.streampack.blog.model.LoginRequest
import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.blog.model.ResetPasswordRequest
import com.enigmastation.streampack.core.entity.TokenType
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.repository.VerificationTokenRepository
import com.enigmastation.streampack.core.service.UserRegistrationService
import com.enigmastation.streampack.core.service.VerificationTokenService
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for user-facing password reset via token.
 *
 * Verifies that a valid reset token allows setting a new password, and that the new password works
 * for login while the old one does not.
 */
@SpringBootTest
@Transactional
@Import(TestChannelConfiguration::class)
class ResetPasswordOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var verificationTokenService: VerificationTokenService
    @Autowired lateinit var verificationTokenRepository: VerificationTokenRepository
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder

    private lateinit var testUser: User
    private lateinit var validToken: String

    private val provenance =
        Provenance(
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            replyTo = "auth/reset-password",
        )

    @BeforeEach
    fun setUp() {
        userRegistrationService.register(
            username = "resetuser",
            email = "reset@example.com",
            displayName = "Reset User",
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            externalIdentifier = "resetuser",
            metadata = mapOf("passwordHash" to passwordEncoder.encode("oldpassword")!!),
        )
        testUser = userRepository.findByUsername("resetuser")!!
        val token = verificationTokenService.createToken(testUser, TokenType.PASSWORD_RESET)
        validToken = token.token
    }

    private fun resetMessage(token: String, newPassword: String) =
        MessageBuilder.withPayload(ResetPasswordRequest(token, newPassword))
            .setHeader(Provenance.HEADER, provenance)
            .build()

    private fun loginMessage(username: String, password: String) =
        MessageBuilder.withPayload(LoginRequest(username, password))
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "auth/login",
                ),
            )
            .build()

    @Test
    fun `valid token resets password`() {
        val result = eventGateway.process(resetMessage(validToken, "newpassword123"))

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("Password reset successfully", (result as OperationResult.Success).payload)
    }

    @Test
    fun `new password works for login`() {
        eventGateway.process(resetMessage(validToken, "newpassword123"))

        val loginResult = eventGateway.process(loginMessage("resetuser", "newpassword123"))

        assertInstanceOf(OperationResult.Success::class.java, loginResult)
        val response = (loginResult as OperationResult.Success).payload as LoginResponse
        assertEquals("resetuser", response.principal.username)
        assertNotNull(response.token)
    }

    @Test
    fun `old password no longer works after reset`() {
        eventGateway.process(resetMessage(validToken, "newpassword123"))

        val loginResult = eventGateway.process(loginMessage("resetuser", "oldpassword"))

        assertInstanceOf(OperationResult.Error::class.java, loginResult)
    }

    @Test
    fun `invalid token returns error`() {
        val result = eventGateway.process(resetMessage("not-a-real-token", "newpassword"))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid or expired token", (result as OperationResult.Error).message)
    }

    @Test
    fun `already-used token returns error`() {
        eventGateway.process(resetMessage(validToken, "newpassword123"))
        val result = eventGateway.process(resetMessage(validToken, "anotherpassword"))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid or expired token", (result as OperationResult.Error).message)
    }

    @Test
    fun `expired token returns error`() {
        val token = verificationTokenRepository.findByToken(validToken)!!
        verificationTokenRepository.saveAndFlush(
            token.copy(expiresAt = Instant.now().minusSeconds(3600))
        )

        val result = eventGateway.process(resetMessage(validToken, "newpassword"))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid or expired token", (result as OperationResult.Error).message)
    }
}
