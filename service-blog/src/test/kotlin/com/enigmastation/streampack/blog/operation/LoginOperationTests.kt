/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.LoginRequest
import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.JwtService
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for login via the event system.
 *
 * These tests demonstrate the full round-trip: a LoginRequest message enters via the EventGateway,
 * the LoginOperation validates credentials against the ServiceBinding, and returns a JWT on
 * success.
 */
@SpringBootTest
@Transactional
class LoginOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder
    @Autowired lateinit var jwtService: JwtService

    @BeforeEach
    fun setUp() {
        userRegistrationService.register(
            username = "testuser",
            email = "test@example.com",
            displayName = "Test User",
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            externalIdentifier = "testuser",
            metadata = mapOf("passwordHash" to passwordEncoder.encode("correctpassword")!!),
        )
    }

    /** Builds a login message with the blog service provenance */
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
    fun `successful login returns JWT and principal`() {
        val result = eventGateway.process(loginMessage("testuser", "correctpassword"))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as LoginResponse
        assertNotNull(response.token)
        assertEquals("testuser", response.principal.username)
        assertEquals(Role.USER, response.principal.role)

        // The JWT should be valid and round-trip the identity
        val validated = jwtService.validateToken(response.token)
        assertNotNull(validated)
        assertEquals("testuser", validated!!.username)
    }

    @Test
    fun `wrong password returns error`() {
        val result = eventGateway.process(loginMessage("testuser", "wrongpassword"))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid credentials", (result as OperationResult.Error).message)
    }

    @Test
    fun `nonexistent user returns error`() {
        val result = eventGateway.process(loginMessage("nobody", "anypassword"))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid credentials", (result as OperationResult.Error).message)
    }

    @Test
    fun `login with bootstrap superadmin works`() {
        // The superadmin was created by SuperAdminBootstrap during context startup.
        // We don't know the generated password, but we can verify the operation
        // handles a SUPER_ADMIN user correctly by creating one with a known password.
        userRegistrationService.register(
            username = "supertest",
            email = "super@test.com",
            displayName = "Super Test",
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            externalIdentifier = "supertest",
            metadata = mapOf("passwordHash" to passwordEncoder.encode("superpass")!!),
            role = Role.SUPER_ADMIN,
        )

        val result = eventGateway.process(loginMessage("supertest", "superpass"))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as LoginResponse
        assertEquals(Role.SUPER_ADMIN, response.principal.role)
    }
}
