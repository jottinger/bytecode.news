/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.operation

import com.enigmastation.streampack.core.TestChannelConfiguration
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.LinkProtocolRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.repository.ServiceBindingRepository
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
import org.springframework.transaction.annotation.Transactional

/** Integration tests for protocol identity linking via LinkProtocolOperation */
@SpringBootTest
@Transactional
@Import(TestChannelConfiguration::class)
class LinkProtocolOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var serviceBindingRepository: ServiceBindingRepository

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
                serviceId = "test-service",
                externalIdentifier = "regularuser",
            )
        adminUser =
            userRegistrationService.register(
                username = "adminuser",
                email = "admin@example.com",
                displayName = "Admin User",
                protocol = Protocol.HTTP,
                serviceId = "test-service",
                externalIdentifier = "adminuser",
                role = Role.ADMIN,
            )
        superAdmin =
            userRegistrationService.register(
                username = "superuser",
                email = "super@example.com",
                displayName = "Super Admin",
                protocol = Protocol.HTTP,
                serviceId = "test-service",
                externalIdentifier = "superuser",
                role = Role.SUPER_ADMIN,
            )
    }

    private fun linkProtocolMessage(request: LinkProtocolRequest, asUser: UserPrincipal?) =
        MessageBuilder.withPayload(request)
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "test-service",
                    replyTo = "admin/link-protocol",
                    user = asUser,
                ),
            )
            .build()

    @Test
    fun `super admin can link protocol identity`() {
        val request =
            LinkProtocolRequest(
                username = "regularuser",
                protocol = Protocol.IRC,
                serviceId = "ircservice",
                externalIdentifier = "regularuser_irc",
            )
        val result = eventGateway.process(linkProtocolMessage(request, superAdmin))

        assertInstanceOf(OperationResult.Success::class.java, result)

        val binding =
            serviceBindingRepository.resolve(Protocol.IRC, "ircservice", "regularuser_irc")
        assertNotNull(binding)
    }

    @Test
    fun `link preserves metadata`() {
        val request =
            LinkProtocolRequest(
                username = "regularuser",
                protocol = Protocol.DISCORD,
                serviceId = "jvm-community",
                externalIdentifier = "regular#1234",
                metadata = mapOf("oauthToken" to "tok_abc"),
            )
        val result = eventGateway.process(linkProtocolMessage(request, superAdmin))

        assertInstanceOf(OperationResult.Success::class.java, result)

        val binding =
            serviceBindingRepository.resolve(Protocol.DISCORD, "jvm-community", "regular#1234")
        assertNotNull(binding)
        assertEquals("tok_abc", binding!!.metadata["oauthToken"])
    }

    @Test
    fun `regular user cannot link`() {
        val request =
            LinkProtocolRequest(
                username = "regularuser",
                protocol = Protocol.IRC,
                serviceId = "ircservice",
                externalIdentifier = "regularuser_irc",
            )
        val result = eventGateway.process(linkProtocolMessage(request, regularUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Insufficient privileges", (result as OperationResult.Error).message)
    }

    @Test
    fun `admin cannot link`() {
        val request =
            LinkProtocolRequest(
                username = "regularuser",
                protocol = Protocol.IRC,
                serviceId = "ircservice",
                externalIdentifier = "regularuser_irc",
            )
        val result = eventGateway.process(linkProtocolMessage(request, adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Insufficient privileges", (result as OperationResult.Error).message)
    }

    @Test
    fun `unauthenticated request returns error`() {
        val request =
            LinkProtocolRequest(
                username = "regularuser",
                protocol = Protocol.IRC,
                serviceId = "ircservice",
                externalIdentifier = "regularuser_irc",
            )
        val result = eventGateway.process(linkProtocolMessage(request, null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Not authenticated", (result as OperationResult.Error).message)
    }

    @Test
    fun `nonexistent user returns error`() {
        val request =
            LinkProtocolRequest(
                username = "nobody",
                protocol = Protocol.IRC,
                serviceId = "ircservice",
                externalIdentifier = "nobody_irc",
            )
        val result = eventGateway.process(linkProtocolMessage(request, superAdmin))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("User not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `duplicate binding returns error`() {
        // Link the first time
        val request =
            LinkProtocolRequest(
                username = "regularuser",
                protocol = Protocol.IRC,
                serviceId = "ircservice",
                externalIdentifier = "regularuser_irc",
            )
        eventGateway.process(linkProtocolMessage(request, superAdmin))

        // Attempt duplicate
        val result = eventGateway.process(linkProtocolMessage(request, superAdmin))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Duplicate binding", (result as OperationResult.Error).message)
    }
}
