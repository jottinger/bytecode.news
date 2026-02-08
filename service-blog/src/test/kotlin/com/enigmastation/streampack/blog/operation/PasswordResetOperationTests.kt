/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.TestChannelConfiguration
import com.enigmastation.streampack.blog.model.LoginRequest
import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.blog.model.PasswordResetRequest
import com.enigmastation.streampack.blog.model.PasswordResetResponse
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.service.UserRegistrationService
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
 * Integration tests for admin-initiated password reset via the event system.
 *
 * Verifies privilege enforcement, temporary password generation, and that the temporary password
 * works for login.
 */
@SpringBootTest
@Transactional
@Import(TestChannelConfiguration::class)
class PasswordResetOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder

    private lateinit var regularUser: UserPrincipal
    private lateinit var adminUser: UserPrincipal

    @BeforeEach
    fun setUp() {
        regularUser =
            userRegistrationService.register(
                username = "regularuser",
                email = "regular@example.com",
                displayName = "Regular User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "regularuser",
                metadata = mapOf("passwordHash" to passwordEncoder.encode("oldpassword")!!),
            )
        adminUser =
            userRegistrationService.register(
                username = "adminuser",
                email = "admin@example.com",
                displayName = "Admin User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "adminuser",
                metadata = mapOf("passwordHash" to passwordEncoder.encode("adminpass")!!),
                role = Role.ADMIN,
            )
    }

    private fun resetMessage(username: String, asUser: UserPrincipal?) =
        MessageBuilder.withPayload(PasswordResetRequest(username))
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "admin/reset-password",
                    user = asUser,
                ),
            )
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
    fun `admin can reset user password`() {
        val result = eventGateway.process(resetMessage("regularuser", adminUser))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as PasswordResetResponse
        assertEquals("regularuser", response.username)
        assertNotNull(response.temporaryPassword)
    }

    @Test
    fun `temporary password works for login`() {
        val resetResult = eventGateway.process(resetMessage("regularuser", adminUser))
        val response = (resetResult as OperationResult.Success).payload as PasswordResetResponse

        val loginResult =
            eventGateway.process(loginMessage("regularuser", response.temporaryPassword))

        assertInstanceOf(OperationResult.Success::class.java, loginResult)
        val loginResponse = (loginResult as OperationResult.Success).payload as LoginResponse
        assertEquals("regularuser", loginResponse.principal.username)
    }

    @Test
    fun `non-admin cannot reset passwords`() {
        val result = eventGateway.process(resetMessage("adminuser", regularUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Insufficient privileges", (result as OperationResult.Error).message)
    }

    @Test
    fun `unauthenticated request returns error`() {
        val result = eventGateway.process(resetMessage("regularuser", null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Not authenticated", (result as OperationResult.Error).message)
    }

    @Test
    fun `reset for nonexistent user returns error`() {
        val result = eventGateway.process(resetMessage("nobody", adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Binding not found", (result as OperationResult.Error).message)
    }
}
