/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.bootstrap

import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.UserRegistrationService
import java.security.SecureRandom
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/** Creates a superadmin user on first boot if none exists, logging the generated password */
@Component
class SuperAdminBootstrap(
    private val userRepository: UserRepository,
    private val userRegistrationService: UserRegistrationService,
    private val passwordEncoder: BCryptPasswordEncoder,
    @Value("\${streampack.blog.service-id:blog-service}") private val serviceId: String,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(SuperAdminBootstrap::class.java)

    override fun run(args: ApplicationArguments) {
        if (userRepository.hasActiveWithRole(Role.SUPER_ADMIN)) {
            logger.debug("Superadmin already exists, skipping bootstrap")
            return
        }

        val password = generateRandomPassword()
        val hash = passwordEncoder.encode(password)!!

        userRegistrationService.register(
            username = "admin",
            email = "",
            displayName = "System Administrator",
            protocol = Protocol.HTTP,
            serviceId = serviceId,
            externalIdentifier = "admin",
            metadata = mapOf<String, Any>("passwordHash" to hash),
            role = Role.SUPER_ADMIN,
        )

        logger.info("========================================")
        logger.info("  Superadmin account created")
        logger.info("  Username: admin")
        logger.info("  Password: {}", password)
        logger.info("  Change this password immediately!")
        logger.info("========================================")
    }

    private fun generateRandomPassword(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        return (1..16).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}
