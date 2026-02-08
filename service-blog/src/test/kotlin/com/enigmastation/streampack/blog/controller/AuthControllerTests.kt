/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.TestChannelConfiguration
import com.enigmastation.streampack.core.entity.TokenType
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.JwtService
import com.enigmastation.streampack.core.service.UserRegistrationService
import com.enigmastation.streampack.core.service.VerificationTokenService
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** Integration tests for all /auth endpoints, exercising the full MVC stack */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestChannelConfiguration::class)
class AuthControllerTests {

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var passwordEncoder: BCryptPasswordEncoder
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var verificationTokenService: VerificationTokenService

    private lateinit var testUser: User
    private lateinit var testUserToken: String

    @BeforeEach
    fun setUp() {
        greenMail.reset()
        val principal =
            userRegistrationService.register(
                username = "testuser",
                email = "test@example.com",
                displayName = "Test User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "testuser",
                metadata = mapOf("passwordHash" to passwordEncoder.encode("correctpassword")!!),
            )
        testUser = userRepository.findByUsername("testuser")!!
        testUserToken = jwtService.generateToken(principal)
    }

    // --- Login ---

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
    fun `wrong password returns 401`() {
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
    fun `nonexistent user login returns 401`() {
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
    fun `missing login body returns 400`() {
        mockMvc
            .post("/auth/login") { contentType = MediaType.APPLICATION_JSON }
            .andExpect { status { isBadRequest() } }
    }

    // --- Registration ---

    @Test
    fun `successful registration returns principal and sends verification email`() {
        mockMvc
            .post("/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"username":"newuser","email":"new@example.com","displayName":"New User","password":"password123"}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.username") { value("newuser") }
            }

        val messages = greenMail.receivedMessages
        org.junit.jupiter.api.Assertions.assertTrue(messages.isNotEmpty())
        org.junit.jupiter.api.Assertions.assertEquals(
            "Verify your email address",
            messages[0].subject,
        )
    }

    @Test
    fun `duplicate username registration returns 409`() {
        mockMvc
            .post("/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"username":"testuser","email":"other@example.com","displayName":"Other","password":"password123"}"""
            }
            .andExpect {
                status { isConflict() }
                jsonPath("$.detail") { value("Username already taken") }
            }
    }

    // --- Email Verification ---

    @Test
    fun `valid verification token verifies email`() {
        val token = verificationTokenService.createToken(testUser, TokenType.EMAIL_VERIFICATION)

        mockMvc
            .post("/auth/verify") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"${token.token}"}"""
            }
            .andExpect { status { isOk() } }

        val updatedUser = userRepository.findByUsername("testuser")!!
        org.junit.jupiter.api.Assertions.assertTrue(updatedUser.emailVerified)
    }

    @Test
    fun `invalid verification token returns 400`() {
        mockMvc
            .post("/auth/verify") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"not-a-real-token"}"""
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Invalid or expired token") }
            }
    }

    // --- Logout ---

    @Test
    fun `logout returns 204`() {
        mockMvc.post("/auth/logout").andExpect { status { isNoContent() } }
    }

    // --- Forgot Password ---

    @Test
    fun `forgot password with valid email sends reset email`() {
        mockMvc
            .post("/auth/forgot-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"test@example.com"}"""
            }
            .andExpect { status { isOk() } }

        val messages = greenMail.receivedMessages
        org.junit.jupiter.api.Assertions.assertTrue(messages.isNotEmpty())
        org.junit.jupiter.api.Assertions.assertEquals("Reset your password", messages[0].subject)
    }

    @Test
    fun `forgot password with unknown email still returns success`() {
        mockMvc
            .post("/auth/forgot-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"nobody@example.com"}"""
            }
            .andExpect { status { isOk() } }

        org.junit.jupiter.api.Assertions.assertEquals(0, greenMail.receivedMessages.size)
    }

    // --- Reset Password ---

    @Test
    fun `valid reset token sets new password`() {
        val token = verificationTokenService.createToken(testUser, TokenType.PASSWORD_RESET)

        mockMvc
            .post("/auth/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"${token.token}","newPassword":"newpass123"}"""
            }
            .andExpect { status { isOk() } }

        // Verify new password works for login
        mockMvc
            .post("/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"testuser","password":"newpass123"}"""
            }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `invalid reset token returns 400`() {
        mockMvc
            .post("/auth/reset-password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"bad-token","newPassword":"newpass123"}"""
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Invalid or expired token") }
            }
    }

    // --- Token Refresh ---

    @Test
    fun `valid token refresh returns new token`() {
        mockMvc
            .post("/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$testUserToken"}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.token") { isNotEmpty() }
                jsonPath("$.principal.username") { value("testuser") }
            }
    }

    @Test
    fun `invalid token refresh returns 401`() {
        mockMvc
            .post("/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"not.a.valid.jwt"}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Invalid or expired token") }
            }
    }

    // --- Change Password ---

    @Test
    fun `authenticated user can change password`() {
        mockMvc
            .put("/auth/password") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $testUserToken")
                content = """{"oldPassword":"correctpassword","newPassword":"changedpass"}"""
            }
            .andExpect { status { isOk() } }

        // Verify new password works for login
        mockMvc
            .post("/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"testuser","password":"changedpass"}"""
            }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `change password without auth returns 401`() {
        mockMvc
            .put("/auth/password") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"oldPassword":"correctpassword","newPassword":"changedpass"}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Not authenticated") }
            }
    }

    @Test
    fun `change password with wrong old password returns 400`() {
        mockMvc
            .put("/auth/password") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $testUserToken")
                content = """{"oldPassword":"wrongpassword","newPassword":"changedpass"}"""
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Invalid current password") }
            }
    }

    // --- Delete Account ---

    @Test
    fun `authenticated user can delete own account`() {
        mockMvc
            .delete("/auth/account") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $testUserToken")
                content = """{}"""
            }
            .andExpect { status { isOk() } }

        val deletedUser = userRepository.findByUsername("testuser")!!
        org.junit.jupiter.api.Assertions.assertTrue(deletedUser.deleted)
    }

    @Test
    fun `delete account without auth returns 401`() {
        mockMvc
            .delete("/auth/account") {
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Not authenticated") }
            }
    }
}
