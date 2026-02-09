/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.blog.model.TokenRefreshRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.JwtService
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for JWT token refresh via the event system.
 *
 * Validates that a valid token produces a fresh JWT, and that invalid, tampered, or deleted-user
 * tokens are rejected.
 */
@SpringBootTest
@Transactional
class TokenRefreshOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var userRepository: UserRepository

    private lateinit var testPrincipal: UserPrincipal

    @BeforeEach
    fun setUp() {
        testPrincipal =
            userRegistrationService.register(
                username = "testuser",
                email = "test@example.com",
                displayName = "Test User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "testuser",
                metadata = mapOf("passwordHash" to passwordEncoder.encode("password")!!),
            )
    }

    private val provenance =
        Provenance(protocol = Protocol.HTTP, serviceId = "blog-service", replyTo = "auth/refresh")

    private fun refreshMessage(token: String) =
        MessageBuilder.withPayload(TokenRefreshRequest(token))
            .setHeader(Provenance.HEADER, provenance)
            .build()

    @Test
    fun `valid token refresh returns new token and principal`() {
        val originalToken = jwtService.generateToken(testPrincipal)

        val result = eventGateway.process(refreshMessage(originalToken))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as LoginResponse
        assertNotNull(response.token)
        assertEquals("testuser", response.principal.username)
        assertEquals(Role.USER, response.principal.role)
    }

    @Test
    fun `tampered token returns error`() {
        val result = eventGateway.process(refreshMessage("not.a.valid.jwt.token"))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid or expired token", (result as OperationResult.Error).message)
    }

    @Test
    fun `deleted user token returns error`() {
        val token = jwtService.generateToken(testPrincipal)

        // Soft-delete the user
        val user = userRepository.findByUsername("testuser")!!
        userRepository.saveAndFlush(user.copy(deleted = true))

        val result = eventGateway.process(refreshMessage(token))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid or expired token", (result as OperationResult.Error).message)
    }

    @Test
    fun `refresh produces a different token string`() {
        val originalToken = jwtService.generateToken(testPrincipal)

        // Small delay to ensure different iat claim
        Thread.sleep(1000)

        val result = eventGateway.process(refreshMessage(originalToken))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as LoginResponse
        assertNotEquals(originalToken, response.token)
    }
}
