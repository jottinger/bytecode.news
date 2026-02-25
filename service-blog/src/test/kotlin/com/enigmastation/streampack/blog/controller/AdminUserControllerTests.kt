/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.JwtService
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** Integration tests for admin user management endpoints, verifying privilege enforcement */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var jwtService: JwtService

    private lateinit var superAdminToken: String
    private lateinit var adminToken: String
    private lateinit var regularUserToken: String

    @BeforeEach
    fun setUp() {
        val superAdminPrincipal =
            userRegistrationService.register(
                username = "superadmin",
                email = "superadmin@example.com",
                displayName = "Super Admin",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "superadmin@example.com",
                role = Role.SUPER_ADMIN,
            )
        superAdminToken = jwtService.generateToken(superAdminPrincipal)

        val adminPrincipal =
            userRegistrationService.register(
                username = "testadmin",
                email = "testadmin@example.com",
                displayName = "Test Admin",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "testadmin@example.com",
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
                externalIdentifier = "regular@example.com",
            )
        regularUserToken = jwtService.generateToken(regularPrincipal)
    }

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

    @Test
    fun `regular user gets 403 on role change`() {
        mockMvc
            .put("/admin/users/testadmin/role") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $regularUserToken")
                content = """{"newRole":"ADMIN"}"""
            }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.detail") { value("Insufficient privileges") }
            }
    }

    @Test
    fun `role change for nonexistent user returns 400`() {
        mockMvc
            .put("/admin/users/nobody/role") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $superAdminToken")
                content = """{"newRole":"ADMIN"}"""
            }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `super admin can promote user to admin`() {
        mockMvc
            .put("/admin/users/regular/role") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $superAdminToken")
                content = """{"newRole":"ADMIN"}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.role") { value("ADMIN") }
            }
    }

    @Test
    fun `super admin can demote admin to user`() {
        mockMvc
            .put("/admin/users/testadmin/role") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $superAdminToken")
                content = """{"newRole":"USER"}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.role") { value("USER") }
            }
    }

    @Test
    fun `admin cannot promote to super admin`() {
        mockMvc
            .put("/admin/users/regular/role") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $adminToken")
                content = """{"newRole":"SUPER_ADMIN"}"""
            }
            .andExpect { status { isForbidden() } }
    }
}
