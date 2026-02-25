/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.SuspendAccountRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

/** Integration tests for account suspension via the event system */
@SpringBootTest
@Transactional
class SuspendAccountOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var userRepository: UserRepository

    private lateinit var regularUser: UserPrincipal
    private lateinit var adminUser: UserPrincipal
    private lateinit var superAdmin: UserPrincipal

    @BeforeEach
    fun setUp() {
        regularUser =
            userRegistrationService.register(
                username = "regularuser",
                email = "regular@example.com",
                displayName = "Regular User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "regular@example.com",
            )
        adminUser =
            userRegistrationService.register(
                username = "adminuser",
                email = "admin@example.com",
                displayName = "Admin User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "admin@example.com",
                role = Role.ADMIN,
            )
        superAdmin =
            userRegistrationService.register(
                username = "superuser",
                email = "super@example.com",
                displayName = "Super Admin",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "super@example.com",
                role = Role.SUPER_ADMIN,
            )
    }

    private fun suspendMessage(username: String, asUser: UserPrincipal?) =
        MessageBuilder.withPayload(SuspendAccountRequest(username))
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "admin/users/suspend",
                    user = asUser,
                ),
            )
            .build()

    @Test
    fun `admin can suspend a user`() {
        val result = eventGateway.process(suspendMessage("regularuser", adminUser))

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("Account suspended", (result as OperationResult.Success).payload)

        val user = userRepository.findByUsername("regularuser")!!
        assertTrue(user.isSuspended())
    }

    @Test
    fun `non-admin cannot suspend`() {
        val result = eventGateway.process(suspendMessage("adminuser", regularUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Insufficient privileges", (result as OperationResult.Error).message)
    }

    @Test
    fun `cannot suspend a super admin`() {
        val result = eventGateway.process(suspendMessage("superuser", adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Cannot suspend a super admin", (result as OperationResult.Error).message)
    }

    @Test
    fun `cannot suspend nonexistent user`() {
        val result = eventGateway.process(suspendMessage("nobody", adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("User not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `cannot suspend already suspended user`() {
        eventGateway.process(suspendMessage("regularuser", adminUser))
        val result = eventGateway.process(suspendMessage("regularuser", adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("User is not active", (result as OperationResult.Error).message)
    }

    @Test
    fun `unauthenticated request returns error`() {
        val result = eventGateway.process(suspendMessage("regularuser", null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Not authenticated", (result as OperationResult.Error).message)
    }
}
