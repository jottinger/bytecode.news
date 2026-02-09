/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.JwtService
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** Integration tests for admin user management endpoints, verifying privilege enforcement */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder
    @Autowired lateinit var jwtService: JwtService

    private lateinit var superAdminToken: String
    private lateinit var adminToken: String
    private lateinit var regularUserToken: String

    @BeforeEach
    fun setUp() {
        // Use the bootstrap-created "admin" user as our super admin
        val bootstrapAdmin = userRepository.findByUsername("admin")!!
        superAdminToken = jwtService.generateToken(bootstrapAdmin.toUserPrincipal())

        val adminPrincipal =
            userRegistrationService.register(
                username = "testadmin",
                email = "testadmin@example.com",
                displayName = "Test Admin",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "testadmin",
                metadata = mapOf("passwordHash" to passwordEncoder.encode("adminpass")!!),
                role = Role.ADMIN,
            )
        adminToken = jwtService.generateToken(adminPrincipal)

        val regularPrincipal =
            userRegistrationService.register(
                username = "regular",
                email = "regular@example.com",
                displayName = "Regular",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "regular",
                metadata = mapOf("passwordHash" to passwordEncoder.encode("regularpass")!!),
            )
        regularUserToken = jwtService.generateToken(regularPrincipal)
    }

    // --- Role Change ---

    @Test
    fun `super admin can change user role`() {
        mockMvc
            .put("/admin/users/regular/role") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $superAdminToken")
                content = """{"newRole":"ADMIN"}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.role") { value("ADMIN") }
                jsonPath("$.username") { value("regular") }
            }
    }

    @Test
    fun `non-super-admin gets 403 on role change`() {
        mockMvc
            .put("/admin/users/regular/role") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $adminToken")
                content = """{"newRole":"ADMIN"}"""
            }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.detail") { value("Insufficient privileges") }
            }
    }

    @Test
    fun `unauthenticated role change returns 401`() {
        mockMvc
            .put("/admin/users/regular/role") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"newRole":"ADMIN"}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Not authenticated") }
            }
    }

    // --- Admin Password Reset ---

    @Test
    fun `admin can reset user password`() {
        mockMvc
            .post("/admin/users/regular/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $adminToken")
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.username") { value("regular") }
                jsonPath("$.temporaryPassword") { isNotEmpty() }
            }
    }

    @Test
    fun `super admin can reset user password`() {
        mockMvc
            .post("/admin/users/regular/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $superAdminToken")
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.username") { value("regular") }
                jsonPath("$.temporaryPassword") { isNotEmpty() }
            }
    }

    @Test
    fun `regular user gets 403 on password reset`() {
        mockMvc
            .post("/admin/users/testadmin/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $regularUserToken")
            }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.detail") { value("Insufficient privileges") }
            }
    }

    @Test
    fun `unauthenticated password reset returns 401`() {
        mockMvc
            .post("/admin/users/regular/reset-password") {
                contentType = MediaType.APPLICATION_JSON
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Not authenticated") }
            }
    }

    @Test
    fun `reset password for nonexistent user returns 400`() {
        mockMvc
            .post("/admin/users/nobody/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $adminToken")
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Binding not found") }
            }
    }
}
