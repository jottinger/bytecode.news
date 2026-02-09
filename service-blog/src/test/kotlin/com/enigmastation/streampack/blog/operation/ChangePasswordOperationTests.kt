/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ChangePasswordRequest
import com.enigmastation.streampack.blog.model.LoginRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.service.UserRegistrationService
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for password change via the event system.
 *
 * Demonstrates the authenticated operation pattern: the Provenance carries a UserPrincipal, which
 * the operation uses to identify who is changing their password.
 */
@SpringBootTest
@Transactional
class ChangePasswordOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder

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
                metadata = mapOf("passwordHash" to passwordEncoder.encode("oldpassword")!!),
            )
    }

    /** Builds a change-password message with the authenticated user on the provenance */
    private fun changePasswordMessage(
        oldPassword: String,
        newPassword: String,
        user: UserPrincipal? = testPrincipal,
    ) =
        MessageBuilder.withPayload(ChangePasswordRequest(oldPassword, newPassword))
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "auth/change-password",
                    user = user,
                ),
            )
            .build()

    @Test
    fun `successful password change`() {
        val result = eventGateway.process(changePasswordMessage("oldpassword", "newpassword"))

        assertInstanceOf(OperationResult.Success::class.java, result)

        // Verify the new password works by logging in with it
        val loginResult =
            eventGateway.process(
                MessageBuilder.withPayload(LoginRequest("testuser", "newpassword"))
                    .setHeader(
                        Provenance.HEADER,
                        Provenance(
                            protocol = Protocol.HTTP,
                            serviceId = "blog-service",
                            replyTo = "auth/login",
                        ),
                    )
                    .build()
            )
        assertInstanceOf(OperationResult.Success::class.java, loginResult)
    }

    @Test
    fun `wrong current password returns error`() {
        val result = eventGateway.process(changePasswordMessage("wrongpassword", "newpassword"))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid current password", (result as OperationResult.Error).message)
    }

    @Test
    fun `unauthenticated request returns error`() {
        val result = eventGateway.process(changePasswordMessage("oldpassword", "newpassword", null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Not authenticated", (result as OperationResult.Error).message)
    }

    @Test
    fun `old password no longer works after change`() {
        eventGateway.process(changePasswordMessage("oldpassword", "newpassword"))

        val loginResult =
            eventGateway.process(
                MessageBuilder.withPayload(LoginRequest("testuser", "oldpassword"))
                    .setHeader(
                        Provenance.HEADER,
                        Provenance(
                            protocol = Protocol.HTTP,
                            serviceId = "blog-service",
                            replyTo = "auth/login",
                        ),
                    )
                    .build()
            )
        assertInstanceOf(OperationResult.Error::class.java, loginResult)
    }

    @Test
    fun `change password for nonexistent binding returns error`() {
        val fakePrincipal =
            UserPrincipal(
                id = UUID.randomUUID(),
                username = "nobody",
                displayName = "Nobody",
                role = Role.USER,
            )
        val result =
            eventGateway.process(changePasswordMessage("oldpassword", "newpassword", fakePrincipal))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Binding not found", (result as OperationResult.Error).message)
    }
}
