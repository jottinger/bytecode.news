/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.repository

import com.enigmastation.streampack.core.entity.ServiceBinding
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.model.Protocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class ServiceBindingRepositoryTests {

    @Autowired lateinit var serviceBindingRepository: ServiceBindingRepository
    @Autowired lateinit var userRepository: UserRepository

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        testUser =
            userRepository.save(
                User(
                    username = "dreamreal",
                    email = "dreamreal@gmail.com",
                    displayName = "Joe Ottinger",
                )
            )
    }

    @Test
    fun `save and retrieve service binding`() {
        val binding =
            serviceBindingRepository.save(
                ServiceBinding(
                    user = testUser,
                    protocol = Protocol.IRC,
                    serviceId = "ircservice",
                    externalIdentifier = "dreamreal",
                    metadata = mapOf("network" to "libera"),
                )
            )
        val found = serviceBindingRepository.findById(binding.id).orElse(null)

        assertNotNull(found)
        assertEquals(Protocol.IRC, found.protocol)
        assertEquals("ircservice", found.serviceId)
        assertEquals("dreamreal", found.externalIdentifier)
    }

    @Test
    fun `resolve by protocol, serviceId, and externalIdentifier`() {
        serviceBindingRepository.save(
            ServiceBinding(
                user = testUser,
                protocol = Protocol.IRC,
                serviceId = "ircservice",
                externalIdentifier = "dreamreal",
            )
        )
        val found = serviceBindingRepository.resolve(Protocol.IRC, "ircservice", "dreamreal")

        assertNotNull(found)
        assertEquals(testUser.id, found!!.user.id)
    }

    @Test
    fun `resolution returns null for no match`() {
        val found = serviceBindingRepository.resolve(Protocol.DISCORD, "nonexistent", "nobody")
        assertNull(found)
    }

    @Test
    fun `unique constraint on protocol, serviceId, externalIdentifier`() {
        serviceBindingRepository.save(
            ServiceBinding(
                user = testUser,
                protocol = Protocol.IRC,
                serviceId = "ircservice",
                externalIdentifier = "dreamreal",
            )
        )
        serviceBindingRepository.flush()

        val otherUser =
            userRepository.save(
                User(username = "otheruser", email = "other@test.com", displayName = "Other User")
            )

        assertThrows(Exception::class.java) {
            serviceBindingRepository.save(
                ServiceBinding(
                    user = otherUser,
                    protocol = Protocol.IRC,
                    serviceId = "ircservice",
                    externalIdentifier = "dreamreal",
                )
            )
            serviceBindingRepository.flush()
        }
    }

    @Test
    fun `JSONB metadata round-trip`() {
        val metadata = mapOf("passwordHash" to "bcrypt\$hash123", "oauthProvider" to "github")
        serviceBindingRepository.save(
            ServiceBinding(
                user = testUser,
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "dreamreal",
                metadata = metadata,
            )
        )
        serviceBindingRepository.flush()

        val found = serviceBindingRepository.resolve(Protocol.HTTP, "blog-service", "dreamreal")

        assertNotNull(found)
        assertEquals("bcrypt\$hash123", found!!.metadata["passwordHash"])
        assertEquals("github", found.metadata["oauthProvider"])
    }

    @Test
    fun `multiple bindings per user across protocols`() {
        serviceBindingRepository.save(
            ServiceBinding(
                user = testUser,
                protocol = Protocol.IRC,
                serviceId = "ircservice",
                externalIdentifier = "dreamreal",
            )
        )
        serviceBindingRepository.save(
            ServiceBinding(
                user = testUser,
                protocol = Protocol.DISCORD,
                serviceId = "jvm-community",
                externalIdentifier = "dreamreal#1234",
            )
        )

        val ircBinding = serviceBindingRepository.resolve(Protocol.IRC, "ircservice", "dreamreal")
        val discordBinding =
            serviceBindingRepository.resolve(Protocol.DISCORD, "jvm-community", "dreamreal#1234")

        assertNotNull(ircBinding)
        assertNotNull(discordBinding)
        assertEquals(testUser.id, ircBinding!!.user.id)
        assertEquals(testUser.id, discordBinding!!.user.id)
    }
}
