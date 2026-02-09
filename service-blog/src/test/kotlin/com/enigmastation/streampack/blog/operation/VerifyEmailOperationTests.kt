/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.VerifyEmailRequest
import com.enigmastation.streampack.core.entity.TokenType
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.VerificationTokenService
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for email verification via token consumption.
 *
 * Verifies that valid tokens mark the user's email as verified, and that expired, used, or invalid
 * tokens are rejected.
 */
@SpringBootTest
@Transactional
class VerifyEmailOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var verificationTokenService: VerificationTokenService

    private lateinit var testUser: User
    private lateinit var validToken: String

    private val provenance =
        Provenance(protocol = Protocol.HTTP, serviceId = "blog-service", replyTo = "auth/verify")

    @BeforeEach
    fun setUp() {
        testUser =
            userRepository.saveAndFlush(
                User(
                    username = "verifyuser",
                    email = "verify@example.com",
                    displayName = "Verify User",
                )
            )
        val token = verificationTokenService.createToken(testUser, TokenType.EMAIL_VERIFICATION)
        validToken = token.token
    }

    private fun verifyMessage(token: String) =
        MessageBuilder.withPayload(VerifyEmailRequest(token))
            .setHeader(Provenance.HEADER, provenance)
            .build()

    @Test
    fun `valid token verifies email`() {
        val result = eventGateway.process(verifyMessage(validToken))

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("Email verified", (result as OperationResult.Success).payload)

        val updatedUser = userRepository.findByUsername("verifyuser")!!
        assertTrue(updatedUser.emailVerified)
    }

    @Test
    fun `invalid token returns error`() {
        val result = eventGateway.process(verifyMessage("not-a-real-token"))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid or expired token", (result as OperationResult.Error).message)
    }

    @Test
    fun `already-used token returns error`() {
        eventGateway.process(verifyMessage(validToken))
        val result = eventGateway.process(verifyMessage(validToken))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid or expired token", (result as OperationResult.Error).message)
    }

    @Test
    fun `expired token returns error`() {
        val expiredUser =
            userRepository.saveAndFlush(
                User(
                    username = "expireduser",
                    email = "expired@example.com",
                    displayName = "Expired User",
                )
            )
        val token = verificationTokenService.createToken(expiredUser, TokenType.EMAIL_VERIFICATION)
        // Manually expire the token by updating it in the repository
        val repo =
            org.springframework.test.util.ReflectionTestUtils.getField(
                verificationTokenService,
                "verificationTokenRepository",
            ) as com.enigmastation.streampack.core.repository.VerificationTokenRepository
        repo.saveAndFlush(token.copy(expiresAt = Instant.now().minusSeconds(3600)))

        val result = eventGateway.process(verifyMessage(token.token))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid or expired token", (result as OperationResult.Error).message)
    }
}
