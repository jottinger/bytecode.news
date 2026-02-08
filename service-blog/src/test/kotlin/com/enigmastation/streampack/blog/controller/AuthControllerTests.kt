/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.TestChannelConfiguration
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/** Integration tests for the /auth/login HTTP endpoint, exercising the full MVC stack */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestChannelConfiguration::class)
class AuthControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder

    @BeforeEach
    fun setUp() {
        userRegistrationService.register(
            username = "testuser",
            email = "test@example.com",
            displayName = "Test User",
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            externalIdentifier = "testuser",
            metadata = mapOf("passwordHash" to passwordEncoder.encode("correctpassword")!!),
        )
    }

    @Test
    fun `successful login returns token and principal`() {
        mockMvc
            .post("/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"testuser","password":"correctpassword"}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.token") { isNotEmpty() }
                jsonPath("$.principal.username") { value("testuser") }
            }
    }

    @Test
    fun `wrong password returns 401 with problem detail`() {
        mockMvc
            .post("/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"testuser","password":"wrongpassword"}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Invalid credentials") }
            }
    }

    @Test
    fun `nonexistent user returns 401 with problem detail`() {
        mockMvc
            .post("/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"nobody","password":"anypassword"}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Invalid credentials") }
            }
    }

    @Test
    fun `missing request body returns 400`() {
        mockMvc
            .post("/auth/login") { contentType = MediaType.APPLICATION_JSON }
            .andExpect { status { isBadRequest() } }
    }
}
