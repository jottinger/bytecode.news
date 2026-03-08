/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.config.BlogProperties
import com.enigmastation.streampack.blog.model.DeleteAccountRequest
import com.enigmastation.streampack.blog.model.ExportUserDataRequest
import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.blog.model.OtpRequest
import com.enigmastation.streampack.blog.model.OtpVerifyRequest
import com.enigmastation.streampack.blog.model.TokenRefreshRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.EditProfileRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.service.JwtService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** HTTP adapter for authentication and account management endpoints */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication")
class AuthController(
    private val eventGateway: EventGateway,
    private val jwtService: JwtService,
    blogProperties: BlogProperties,
) {
    private val serviceId = blogProperties.serviceId
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    @Operation(summary = "Request a one-time sign-in code")
    @ApiResponse(responseCode = "202", description = "Code sent if email is valid")
    @PostMapping("/otp/request", produces = ["application/json"], consumes = ["application/json"])
    fun requestOtp(@RequestBody request: OtpRequest): ResponseEntity<*> {
        return dispatch(request, "auth/otp/request", successStatus = HttpStatus.ACCEPTED) { result
            ->
            mapError(result, HttpStatus.BAD_REQUEST)
        }
    }

    @Operation(summary = "Verify a one-time sign-in code")
    @ApiResponse(
        responseCode = "200",
        description = "Authentication successful",
        content = [Content(schema = Schema(implementation = LoginResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Invalid or expired code",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @PostMapping("/otp/verify", produces = ["application/json"], consumes = ["application/json"])
    fun verifyOtp(@RequestBody request: OtpVerifyRequest): ResponseEntity<*> {
        return dispatch(request, "auth/otp/verify") { result ->
            mapError(result, HttpStatus.UNAUTHORIZED)
        }
    }

    @Operation(summary = "Log out the current session")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @PostMapping("/logout")
    fun logout(): ResponseEntity<Void> {
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Refresh an expired JWT token")
    @ApiResponse(
        responseCode = "200",
        description = "Token refreshed",
        content = [Content(schema = Schema(implementation = LoginResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Invalid or expired refresh token",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @PostMapping("/refresh", produces = ["application/json"], consumes = ["application/json"])
    fun refresh(@RequestBody request: TokenRefreshRequest): ResponseEntity<*> {
        return dispatch(request, "auth/refresh") { result ->
            mapError(result, HttpStatus.UNAUTHORIZED)
        }
    }

    @Operation(summary = "Erase the authenticated user's account")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/account", produces = ["application/json"], consumes = ["application/json"])
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

    @Operation(summary = "Export the authenticated user's data")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "User data export")
    @GetMapping("/export", produces = ["application/json"])
    fun exportUserData(httpRequest: HttpServletRequest): ResponseEntity<*> {
        val user = resolveUser(httpRequest) ?: return unauthorized("Not authenticated")

        return dispatch(ExportUserDataRequest(), "auth/export", user) { result ->
            mapError(result, HttpStatus.BAD_REQUEST)
        }
    }

    @Operation(summary = "Update the authenticated user's profile")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(
        responseCode = "200",
        description = "Updated user principal",
        content = [Content(schema = Schema(implementation = UserPrincipal::class))],
    )
    @PutMapping("/profile", produces = ["application/json"], consumes = ["application/json"])
    fun editProfile(
        @RequestBody request: EditProfileRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest) ?: return unauthorized("Not authenticated")

        return dispatch(request, "auth/profile", user) { result ->
            mapError(result, HttpStatus.BAD_REQUEST)
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
        successStatus: HttpStatus = HttpStatus.OK,
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
            is OperationResult.Success -> ResponseEntity.status(successStatus).body(result.payload)
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
