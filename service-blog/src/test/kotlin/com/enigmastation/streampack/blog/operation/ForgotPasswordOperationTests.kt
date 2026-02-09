/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.ForgotPasswordRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.UserRegistrationService
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for user-facing forgot password flow.
 *
 * Verifies that valid emails trigger a reset email (via GreenMail), and that nonexistent emails
 * still return a success response without leaking information.
 */
@SpringBootTest
@Transactional
class ForgotPasswordOperationTests {

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder

    private val provenance =
        Provenance(
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            replyTo = "auth/forgot-password",
        )

    @BeforeEach
    fun setUp() {
        greenMail.reset()
        userRegistrationService.register(
            username = "forgotuser",
            email = "forgot@example.com",
            displayName = "Forgot User",
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            externalIdentifier = "forgotuser",
            metadata = mapOf("passwordHash" to passwordEncoder.encode("oldpassword")!!),
        )
    }

    private fun forgotMessage(email: String) =
        MessageBuilder.withPayload(ForgotPasswordRequest(email))
            .setHeader(Provenance.HEADER, provenance)
            .build()

    @Test
    fun `valid email sends reset email`() {
        val result = eventGateway.process(forgotMessage("forgot@example.com"))

        assertInstanceOf(OperationResult.Success::class.java, result)

        val messages = greenMail.receivedMessages
        assertEquals(1, messages.size)
        assertEquals("Reset your password", messages[0].subject)
        val body = messages[0].content as String
        assertTrue(body.contains("/auth/reset-password?token="))
    }

    @Test
    fun `nonexistent email still returns success but sends no email`() {
        val result = eventGateway.process(forgotMessage("nobody@example.com"))

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals(
            "If an account with that email exists, a reset link has been sent",
            (result as OperationResult.Success).payload,
        )
        assertEquals(0, greenMail.receivedMessages.size)
    }

    @Test
    fun `response message does not leak email existence`() {
        val validResult = eventGateway.process(forgotMessage("forgot@example.com"))
        val invalidResult = eventGateway.process(forgotMessage("nobody@example.com"))

        assertEquals(
            (validResult as OperationResult.Success).payload,
            (invalidResult as OperationResult.Success).payload,
        )
    }
}
