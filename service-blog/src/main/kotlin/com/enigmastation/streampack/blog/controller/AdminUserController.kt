/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.config.BlogProperties
import com.enigmastation.streampack.blog.model.RoleUpdateRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.AlterUserRequest
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** HTTP adapter for admin user management endpoints */
@RestController
@RequestMapping("/admin/users")
@Tag(name = "Admin - User Management")
@SecurityRequirement(name = "bearerAuth")
class AdminUserController(
    private val eventGateway: EventGateway,
    private val jwtService: JwtService,
    blogProperties: BlogProperties,
) {
    private val serviceId = blogProperties.serviceId
    private val logger = LoggerFactory.getLogger(AdminUserController::class.java)

    @Operation(summary = "Change a user's role")
    @ApiResponse(
        responseCode = "200",
        description = "Role updated",
        content = [Content(schema = Schema(implementation = UserPrincipal::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient privileges",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @PutMapping("/{username}/role")
    fun changeRole(
        @PathVariable username: String,
        @RequestBody request: RoleUpdateRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest) ?: return unauthorized("Not authenticated")

        val payload = AlterUserRequest(username = username, role = request.newRole)
        return dispatch(payload, "admin/users/role", user) { result -> mapError(result) }
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
        user: UserPrincipal,
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

    /** Maps an error to the appropriate HTTP status based on error message content */
    private fun mapError(result: OperationResult): ResponseEntity<*> {
        val message = (result as OperationResult.Error).message
        val status =
            when {
                message.contains("Not authenticated") -> HttpStatus.UNAUTHORIZED
                message.contains("Insufficient privileges") -> HttpStatus.FORBIDDEN
                else -> HttpStatus.BAD_REQUEST
            }
        logger.debug("Operation error on admin endpoint: {}", message)
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message))
    }

    private fun unauthorized(message: String): ResponseEntity<*> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message))
    }
}
