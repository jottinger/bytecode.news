/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.TestChannelConfiguration
import com.enigmastation.streampack.blog.model.ChangeRoleRequest
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
 * Integration tests for role changes via the event system.
 *
 * Verifies that only SUPER_ADMIN can change roles, that self-role-change is prevented, and that the
 * updated principal reflects the new role.
 */
@SpringBootTest
@Transactional
@Import(TestChannelConfiguration::class)
class ChangeRoleOperationTests {

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

    private fun changeRoleMessage(username: String, newRole: Role, asUser: UserPrincipal?) =
        MessageBuilder.withPayload(ChangeRoleRequest(username, newRole))
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "admin/change-role",
                    user = asUser,
                ),
            )
            .build()

    @Test
    fun `super admin can promote user to admin`() {
        val result = eventGateway.process(changeRoleMessage("regularuser", Role.ADMIN, superAdmin))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val principal = (result as OperationResult.Success).payload as UserPrincipal
        assertEquals("regularuser", principal.username)
        assertEquals(Role.ADMIN, principal.role)
    }

    @Test
    fun `super admin can demote admin to user`() {
        val result = eventGateway.process(changeRoleMessage("adminuser", Role.USER, superAdmin))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val principal = (result as OperationResult.Success).payload as UserPrincipal
        assertEquals(Role.USER, principal.role)
    }

    @Test
    fun `admin cannot change roles`() {
        val result = eventGateway.process(changeRoleMessage("regularuser", Role.ADMIN, adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Insufficient privileges", (result as OperationResult.Error).message)
    }

    @Test
    fun `regular user cannot change roles`() {
        val result = eventGateway.process(changeRoleMessage("adminuser", Role.USER, regularUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Insufficient privileges", (result as OperationResult.Error).message)
    }

    @Test
    fun `super admin cannot change own role`() {
        val result = eventGateway.process(changeRoleMessage("superuser", Role.USER, superAdmin))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Cannot change own role", (result as OperationResult.Error).message)
    }

    @Test
    fun `changing nonexistent user returns error`() {
        val result = eventGateway.process(changeRoleMessage("nobody", Role.ADMIN, superAdmin))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("User not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `unauthenticated request returns error`() {
        val result = eventGateway.process(changeRoleMessage("regularuser", Role.ADMIN, null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Not authenticated", (result as OperationResult.Error).message)
    }
}
