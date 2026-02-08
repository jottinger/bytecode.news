/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.TestChannelConfiguration
import com.enigmastation.streampack.blog.model.DeleteAccountRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for account deletion via the event system.
 *
 * Covers self-deletion, admin-deletion of other users, privilege enforcement, and super admin
 * protection.
 */
@SpringBootTest
@Transactional
@Import(TestChannelConfiguration::class)
class DeleteAccountOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder

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
                externalIdentifier = "regularuser",
                metadata = mapOf("passwordHash" to passwordEncoder.encode("password")!!),
            )
        adminUser =
            userRegistrationService.register(
                username = "adminuser",
                email = "admin@example.com",
                displayName = "Admin User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "adminuser",
                metadata = mapOf("passwordHash" to passwordEncoder.encode("password")!!),
                role = Role.ADMIN,
            )
        superAdmin =
            userRegistrationService.register(
                username = "superuser",
                email = "super@example.com",
                displayName = "Super Admin",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "superuser",
                metadata = mapOf("passwordHash" to passwordEncoder.encode("password")!!),
                role = Role.SUPER_ADMIN,
            )
    }

    private fun deleteMessage(username: String? = null, asUser: UserPrincipal?) =
        MessageBuilder.withPayload(DeleteAccountRequest(username))
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "auth/delete",
                    user = asUser,
                ),
            )
            .build()

    @Test
    fun `self-deletion succeeds`() {
        val result = eventGateway.process(deleteMessage(asUser = regularUser))

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("Account deleted", (result as OperationResult.Success).payload)
    }

    @Test
    fun `admin can delete another user`() {
        val result =
            eventGateway.process(deleteMessage(username = "regularuser", asUser = adminUser))

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("Account deleted", (result as OperationResult.Success).payload)
    }

    @Test
    fun `non-admin cannot delete another user`() {
        val result =
            eventGateway.process(deleteMessage(username = "adminuser", asUser = regularUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Insufficient privileges", (result as OperationResult.Error).message)
    }

    @Test
    fun `cannot delete a super admin`() {
        val result = eventGateway.process(deleteMessage(username = "superuser", asUser = adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Cannot delete a super admin", (result as OperationResult.Error).message)
    }

    @Test
    fun `unauthenticated request returns error`() {
        val result = eventGateway.process(deleteMessage(asUser = null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Not authenticated", (result as OperationResult.Error).message)
    }

    @Test
    fun `deleting nonexistent user returns error`() {
        val result = eventGateway.process(deleteMessage(username = "nobody", asUser = adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("User not found", (result as OperationResult.Error).message)
    }
}
