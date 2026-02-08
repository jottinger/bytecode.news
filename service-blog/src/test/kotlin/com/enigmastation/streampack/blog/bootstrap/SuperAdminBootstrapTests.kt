/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.bootstrap

import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.ServiceBindingRepository
import com.enigmastation.streampack.core.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * Tests for the superadmin bootstrap process.
 *
 * The bootstrap runs automatically via ApplicationRunner, so by the time the test context is ready,
 * it has already executed. These tests verify the result of that execution.
 */
@SpringBootTest
@Transactional
class SuperAdminBootstrapTests {

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var serviceBindingRepository: ServiceBindingRepository

    @Test
    fun `superadmin user exists after bootstrap`() {
        val admin = userRepository.findByUsername("admin")

        assertNotNull(admin)
        assertEquals(Role.SUPER_ADMIN, admin!!.role)
        assertEquals("System Administrator", admin.displayName)
    }

    @Test
    fun `superadmin has HTTP service binding with password hash`() {
        val binding = serviceBindingRepository.resolve(Protocol.HTTP, "blog-service", "admin")

        assertNotNull(binding)
        val passwordHash = binding!!.metadata["passwordHash"] as? String
        assertNotNull(passwordHash)
        assertTrue(passwordHash!!.startsWith("\$2a\$") || passwordHash.startsWith("\$2b\$"))
    }

    @Test
    fun `bootstrap is idempotent`() {
        // The bootstrap already ran once during context startup.
        // Verify there's exactly one superadmin, not duplicates.
        assertTrue(userRepository.hasActiveWithRole(Role.SUPER_ADMIN))

        val admins = userRepository.findActive().filter { it.role == Role.SUPER_ADMIN }
        assertEquals(1, admins.size)
    }
}
