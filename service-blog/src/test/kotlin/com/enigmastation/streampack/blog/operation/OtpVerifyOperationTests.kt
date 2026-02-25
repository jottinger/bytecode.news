/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.blog.model.OtpVerifyRequest
import com.enigmastation.streampack.core.entity.OneTimeCode
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.OneTimeCodeRepository
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.UserRegistrationService
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class OtpVerifyOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var oneTimeCodeRepository: OneTimeCodeRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userRegistrationService: UserRegistrationService

    private val provenance =
        Provenance(
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            replyTo = "auth/otp/verify",
        )

    private fun verifyMessage(email: String, code: String) =
        MessageBuilder.withPayload(OtpVerifyRequest(email, code))
            .setHeader(Provenance.HEADER, provenance)
            .build()

    private fun seedCode(
        email: String,
        code: String,
        expiresAt: Instant = Instant.now().plusSeconds(300),
    ): OneTimeCode {
        return oneTimeCodeRepository.saveAndFlush(
            OneTimeCode(email = email, code = code, expiresAt = expiresAt)
        )
    }

    @Test
    fun `valid code creates new user and returns JWT`() {
        seedCode("newuser@example.com", "123456")

        val result = eventGateway.process(verifyMessage("newuser@example.com", "123456"))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as LoginResponse
        assertNotNull(response.token)
        assertEquals("newuser", response.principal.username)

        val user = userRepository.findByEmail("newuser@example.com")
        assertNotNull(user)
        assertTrue(user!!.emailVerified)
    }

    @Test
    fun `valid code for existing user returns JWT without creating duplicate`() {
        userRegistrationService.register(
            username = "existing",
            email = "existing@example.com",
            displayName = "Existing User",
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            externalIdentifier = "existing@example.com",
        )
        seedCode("existing@example.com", "654321")

        val result = eventGateway.process(verifyMessage("existing@example.com", "654321"))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as LoginResponse
        assertEquals("existing", response.principal.username)
    }

    @Test
    fun `wrong code returns error`() {
        seedCode("user@example.com", "123456")

        val result = eventGateway.process(verifyMessage("user@example.com", "999999"))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Invalid or expired code", (result as OperationResult.Error).message)
    }

    @Test
    fun `expired code returns error`() {
        seedCode("user@example.com", "123456", expiresAt = Instant.now().minusSeconds(60))

        val result = eventGateway.process(verifyMessage("user@example.com", "123456"))

        assertInstanceOf(OperationResult.Error::class.java, result)
    }

    @Test
    fun `code for nonexistent email returns error`() {
        val result = eventGateway.process(verifyMessage("nobody@example.com", "123456"))

        assertInstanceOf(OperationResult.Error::class.java, result)
    }

    @Test
    fun `deleted user returns error`() {
        val principal =
            userRegistrationService.register(
                username = "deleted",
                email = "deleted@example.com",
                displayName = "Deleted User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "deleted@example.com",
            )
        val user = userRepository.findActiveById(principal.id)!!
        userRepository.saveAndFlush(
            user.copy(status = com.enigmastation.streampack.core.model.UserStatus.ERASED)
        )
        seedCode("deleted@example.com", "111111")

        val result = eventGateway.process(verifyMessage("deleted@example.com", "111111"))

        assertInstanceOf(OperationResult.Error::class.java, result)
    }

    @Test
    fun `username collision is handled by appending suffix`() {
        userRegistrationService.register(
            username = "collision",
            email = "other@example.com",
            displayName = "Other User",
            protocol = Protocol.HTTP,
            serviceId = "blog-service",
            externalIdentifier = "other@example.com",
        )
        seedCode("collision@example.com", "222222")

        val result = eventGateway.process(verifyMessage("collision@example.com", "222222"))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as LoginResponse
        assertEquals("collision1", response.principal.username)
    }
}
