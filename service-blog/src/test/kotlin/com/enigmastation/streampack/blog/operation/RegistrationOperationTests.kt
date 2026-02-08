/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.TestChannelConfiguration
import com.enigmastation.streampack.blog.model.LoginRequest
import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.blog.model.RegistrationRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for user registration via the event system.
 *
 * Verifies that RegistrationOperation creates users with hashed passwords and that the newly
 * registered user can authenticate via LoginOperation.
 */
@SpringBootTest
@Transactional
@Import(TestChannelConfiguration::class)
class RegistrationOperationTests {

    @Autowired lateinit var eventGateway: EventGateway

    private val provenance =
        Provenance(protocol = Protocol.HTTP, serviceId = "blog-service", replyTo = "auth/register")

    private fun registrationMessage(
        username: String,
        email: String,
        displayName: String,
        password: String,
    ) =
        MessageBuilder.withPayload(RegistrationRequest(username, email, displayName, password))
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
    fun `successful registration returns principal`() {
        val result =
            eventGateway.process(
                registrationMessage("newuser", "new@example.com", "New User", "password123")
            )

        assertInstanceOf(OperationResult.Success::class.java, result)
        val principal = (result as OperationResult.Success).payload as UserPrincipal
        assertEquals("newuser", principal.username)
        assertEquals("New User", principal.displayName)
        assertEquals(Role.USER, principal.role)
        assertNotNull(principal.id)
    }

    @Test
    fun `duplicate username returns error`() {
        eventGateway.process(
            registrationMessage("dupuser", "first@example.com", "First", "password123")
        )

        val result =
            eventGateway.process(
                registrationMessage("dupuser", "second@example.com", "Second", "password456")
            )

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Username already taken", (result as OperationResult.Error).message)
    }

    @Test
    fun `login works after registration`() {
        eventGateway.process(
            registrationMessage("logintest", "login@example.com", "Login Test", "mypassword")
        )

        val loginResult = eventGateway.process(loginMessage("logintest", "mypassword"))

        assertInstanceOf(OperationResult.Success::class.java, loginResult)
        val response = (loginResult as OperationResult.Success).payload as LoginResponse
        assertEquals("logintest", response.principal.username)
        assertNotNull(response.token)
    }
}
