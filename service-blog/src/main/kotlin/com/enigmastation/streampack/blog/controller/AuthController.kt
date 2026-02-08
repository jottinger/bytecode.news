/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.config.BlogProperties
import com.enigmastation.streampack.blog.model.ChangePasswordRequest
import com.enigmastation.streampack.blog.model.DeleteAccountRequest
import com.enigmastation.streampack.blog.model.ForgotPasswordRequest
import com.enigmastation.streampack.blog.model.LoginRequest
import com.enigmastation.streampack.blog.model.RegistrationRequest
import com.enigmastation.streampack.blog.model.ResetPasswordRequest
import com.enigmastation.streampack.blog.model.TokenRefreshRequest
import com.enigmastation.streampack.blog.model.VerifyEmailRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.service.JwtService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** HTTP adapter for authentication and account management endpoints */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val eventGateway: EventGateway,
    private val jwtService: JwtService,
    blogProperties: BlogProperties,
) {
    private val serviceId = blogProperties.serviceId
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<*> {
        return dispatch(request, "auth/login") { result ->
            mapError(result, HttpStatus.UNAUTHORIZED)
        }
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegistrationRequest): ResponseEntity<*> {
        return dispatch(request, "auth/register") { result ->
            mapError(result) { message ->
                when {
                    message.contains("already taken") -> HttpStatus.CONFLICT
                    else -> HttpStatus.BAD_REQUEST
                }
            }
        }
    }

    @PostMapping("/verify")
    fun verify(@RequestBody request: VerifyEmailRequest): ResponseEntity<*> {
        return dispatch(request, "auth/verify") { result ->
            mapError(result, HttpStatus.BAD_REQUEST)
        }
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Void> {
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@RequestBody request: ForgotPasswordRequest): ResponseEntity<*> {
        return dispatch(request, "auth/forgot-password") { result ->
            mapError(result, HttpStatus.BAD_REQUEST)
        }
    }

    @PostMapping("/reset-password")
    fun resetPassword(@RequestBody request: ResetPasswordRequest): ResponseEntity<*> {
        return dispatch(request, "auth/reset-password") { result ->
            mapError(result, HttpStatus.BAD_REQUEST)
        }
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: TokenRefreshRequest): ResponseEntity<*> {
        return dispatch(request, "auth/refresh") { result ->
            mapError(result, HttpStatus.UNAUTHORIZED)
        }
    }

    @PutMapping("/password")
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest) ?: return unauthorized("Not authenticated")

        return dispatch(request, "auth/password", user) { result ->
            mapError(result) { message ->
                when {
                    message.contains("Not authenticated") -> HttpStatus.UNAUTHORIZED
                    else -> HttpStatus.BAD_REQUEST
                }
            }
        }
    }

    @DeleteMapping("/account")
    fun deleteAccount(
        @RequestBody request: DeleteAccountRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest) ?: return unauthorized("Not authenticated")

        return dispatch(request, "auth/account", user) { result ->
            mapError(result) { message ->
                when {
                    message.contains("Not authenticated") -> HttpStatus.UNAUTHORIZED
                    message.contains("Insufficient privileges") -> HttpStatus.FORBIDDEN
                    else -> HttpStatus.BAD_REQUEST
                }
            }
        }
    }

    /** Extracts and validates the Bearer token from the Authorization header */
    private fun resolveUser(request: HttpServletRequest): UserPrincipal? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        val token = header.substring(7)
        return jwtService.validateToken(token)
    }

    /** Sends a payload through the event system and maps the result to an HTTP response */
    private fun dispatch(
        payload: Any,
        replyTo: String,
        user: UserPrincipal? = null,
        onError: (OperationResult) -> ResponseEntity<*>,
    ): ResponseEntity<*> {
        val provenance =
            Provenance(
                protocol = Protocol.HTTP,
                serviceId = serviceId,
                replyTo = replyTo,
                user = user,
            )
        val message =
            MessageBuilder.withPayload(payload).setHeader(Provenance.HEADER, provenance).build()

        return when (val result = eventGateway.process(message)) {
            is OperationResult.Success -> ResponseEntity.ok(result.payload)
            is OperationResult.Error -> onError(result)
            is OperationResult.NotHandled -> {
                logger.warn("Request to {} was not handled by any operation", replyTo)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                        ProblemDetail.forStatusAndDetail(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Unhandled request",
                        )
                    )
            }
        }
    }

    /** Maps an error result to a ProblemDetail response with a fixed status */
    private fun mapError(result: OperationResult, status: HttpStatus): ResponseEntity<*> {
        val message = (result as OperationResult.Error).message
        logger.debug("Operation error on auth endpoint: {}", message)
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message))
    }

    /**
     * Maps an error result to a ProblemDetail response with status determined by message content
     */
    private fun mapError(
        result: OperationResult,
        statusMapper: (String) -> HttpStatus,
    ): ResponseEntity<*> {
        val message = (result as OperationResult.Error).message
        val status = statusMapper(message)
        logger.debug("Operation error on auth endpoint: {}", message)
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message))
    }

    private fun unauthorized(message: String): ResponseEntity<*> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message))
    }
}
