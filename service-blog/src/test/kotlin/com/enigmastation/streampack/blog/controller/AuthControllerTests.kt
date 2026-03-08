/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.core.entity.OneTimeCode
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.repository.OneTimeCodeRepository
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.JwtService
import com.enigmastation.streampack.core.service.UserRegistrationService
import com.enigmastation.streampack.test.TestChannelConfiguration
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** Integration tests for /auth endpoints, exercising the full MVC stack */
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
    @Autowired lateinit var oneTimeCodeRepository: OneTimeCodeRepository
    @Autowired lateinit var jwtService: JwtService

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
                externalIdentifier = "test@example.com",
            )
        testUser = userRepository.findByUsername("testuser")!!
        testUserToken = jwtService.generateToken(principal)
    }

    private fun seedCode(
        email: String,
        code: String,
        expiresAt: Instant = Instant.now().plusSeconds(300),
    ) {
        oneTimeCodeRepository.saveAndFlush(
            OneTimeCode(email = email, code = code, expiresAt = expiresAt)
        )
    }

    /* ── OTP Request ────────────────────────────────────── */

    @Test
    fun `otp request returns 202 and sends email`() {
        mockMvc
            .post("/auth/otp/request") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"test@example.com"}"""
            }
            .andExpect { status { isAccepted() } }

        val messages = greenMail.receivedMessages
        assertEquals(1, messages.size)
        assertTrue(messages[0].subject.contains("sign-in code"))
    }

    @Test
    fun `otp request for unknown email still returns 202`() {
        mockMvc
            .post("/auth/otp/request") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"nobody@example.com"}"""
            }
            .andExpect { status { isAccepted() } }
    }

    /* ── OTP Verify ─────────────────────────────────────── */

    @Test
    fun `valid otp verify returns token and principal`() {
        seedCode("test@example.com", "123456")

        mockMvc
            .post("/auth/otp/verify") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"test@example.com","code":"123456"}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.token") { isNotEmpty() }
                jsonPath("$.principal.username") { value("testuser") }
            }
    }

    @Test
    fun `otp verify creates new user for unknown email`() {
        seedCode("brand-new@example.com", "654321")

        mockMvc
            .post("/auth/otp/verify") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"brand-new@example.com","code":"654321"}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.token") { isNotEmpty() }
                jsonPath("$.principal.username") { value("brand-new") }
            }
    }

    @Test
    fun `invalid otp code returns 401`() {
        seedCode("test@example.com", "123456")

        mockMvc
            .post("/auth/otp/verify") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"test@example.com","code":"999999"}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Invalid or expired code") }
            }
    }

    @Test
    fun `expired otp code returns 401`() {
        seedCode("test@example.com", "123456", expiresAt = Instant.now().minusSeconds(60))

        mockMvc
            .post("/auth/otp/verify") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"test@example.com","code":"123456"}"""
            }
            .andExpect { status { isUnauthorized() } }
    }

    /* ── Logout ─────────────────────────────────────────── */

    @Test
    fun `logout returns 204`() {
        mockMvc.post("/auth/logout").andExpect { status { isNoContent() } }
    }

    /* ── Token Refresh ──────────────────────────────────── */

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

    /* ── Delete Account ─────────────────────────────────── */

    @Test
    fun `authenticated user can delete own account`() {
        mockMvc
            .delete("/auth/account") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $testUserToken")
                content = """{}"""
            }
            .andExpect { status { isOk() } }

        // Original user is hard-deleted
        assertNull(userRepository.findByUsername("testuser"))

        // Sentinel was created with erased status
        val sentinelUsername = "erased-${testUser.id.toString().substring(0, 8)}"
        val sentinel = userRepository.findByUsername(sentinelUsername)
        assertNotNull(sentinel)
        assertTrue(sentinel!!.isErased())
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

    /* ── Profile ───────────────────────────────────────── */

    @Test
    fun `authenticated user can update display name`() {
        mockMvc
            .put("/auth/profile") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $testUserToken")
                content = """{"displayName":"Updated User"}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.displayName") { value("Updated User") }
            }

        val updated = userRepository.findByUsername("testuser")
        assertEquals("Updated User", updated?.displayName)
    }

    @Test
    fun `profile update without auth returns 401`() {
        mockMvc
            .put("/auth/profile") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"displayName":"Updated User"}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Not authenticated") }
            }
    }
}
