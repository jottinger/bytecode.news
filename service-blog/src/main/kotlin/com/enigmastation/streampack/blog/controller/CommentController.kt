/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.config.BlogProperties
import com.enigmastation.streampack.blog.model.CommentDetail
import com.enigmastation.streampack.blog.model.CommentThreadResponse
import com.enigmastation.streampack.blog.model.CreateCommentHttpRequest
import com.enigmastation.streampack.blog.model.CreateCommentRequest
import com.enigmastation.streampack.blog.model.EditCommentHttpRequest
import com.enigmastation.streampack.blog.model.EditCommentRequest
import com.enigmastation.streampack.blog.model.FindCommentsRequest
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.core.integration.EventGateway
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
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** HTTP adapter for comment read, create, and edit endpoints */
@RestController
@Tag(name = "Comments")
class CommentController(
    private val eventGateway: EventGateway,
    private val jwtService: JwtService,
    private val slugRepository: SlugRepository,
    blogProperties: BlogProperties,
) {
    private val serviceId = blogProperties.serviceId
    private val logger = LoggerFactory.getLogger(CommentController::class.java)

    @Operation(summary = "Get threaded comments for a post")
    @ApiResponse(
        responseCode = "200",
        description = "Comment thread",
        content = [Content(schema = Schema(implementation = CommentThreadResponse::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "Post not found",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @GetMapping("/posts/{year}/{month}/{slug}/comments", produces = ["application/json"])
    fun getComments(
        @PathVariable @Schema(minimum = "2007", maximum = "3000") year: Int,
        @PathVariable @Schema(minimum = "1", maximum = "12") month: Int,
        @PathVariable slug: String,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val postId =
            resolvePostId("$year/${"%02d".format(month)}/$slug")
                ?: return notFound("Post not found")
        val user = resolveUser(httpRequest)
        val payload = FindCommentsRequest(postId)
        return dispatch(payload, "posts/comments", user) { result -> mapError(result) }
    }

    @Operation(summary = "Add a comment to a post")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(
        responseCode = "201",
        description = "Comment created",
        content = [Content(schema = Schema(implementation = CommentDetail::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "Post or parent comment not found",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @PostMapping(
        "/posts/{year}/{month}/{slug}/comments",
        produces = ["application/json"],
        consumes = ["application/json"],
    )
    fun createComment(
        @PathVariable @Schema(minimum = "2007", maximum = "3000") year: Int,
        @PathVariable @Schema(minimum = "1", maximum = "12") month: Int,
        @PathVariable slug: String,
        @RequestBody request: CreateCommentHttpRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest) ?: return unauthorized("Authentication required")
        val postId =
            resolvePostId("$year/${"%02d".format(month)}/$slug")
                ?: return notFound("Post not found")
        val payload =
            CreateCommentRequest(
                postId = postId,
                parentCommentId = request.parentCommentId,
                markdownSource = request.markdownSource,
            )
        return dispatchCreated(payload, "posts/comments", user) { result -> mapError(result) }
    }

    @Operation(summary = "Edit a comment within the edit window")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(
        responseCode = "200",
        description = "Comment updated",
        content = [Content(schema = Schema(implementation = CommentDetail::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not authorized or edit window expired",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "Comment not found",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @PutMapping("/comments/{id}", produces = ["application/json"], consumes = ["application/json"])
    fun editComment(
        @PathVariable id: UUID,
        @RequestBody request: EditCommentHttpRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest) ?: return unauthorized("Authentication required")
        val payload = EditCommentRequest(id = id, markdownSource = request.markdownSource)
        return dispatch(payload, "comments/edit", user) { result -> mapError(result) }
    }

    /** Resolves slug path to a post ID via the slug repository */
    private fun resolvePostId(slugPath: String): UUID? {
        val resolved = slugRepository.resolve(slugPath) ?: return null
        return resolved.post.id
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

    /** Sends a payload and returns 201 Created on success */
    private fun dispatchCreated(
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
            is OperationResult.Success ->
                ResponseEntity.status(HttpStatus.CREATED).body(result.payload)
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
                message.contains("Authentication required") -> HttpStatus.UNAUTHORIZED
                message.contains("Not authorized") -> HttpStatus.FORBIDDEN
                message.contains("Insufficient privileges") -> HttpStatus.FORBIDDEN
                message.contains("Edit window has expired") -> HttpStatus.FORBIDDEN
                message.contains("not found", ignoreCase = true) -> HttpStatus.NOT_FOUND
                else -> HttpStatus.BAD_REQUEST
            }
        logger.debug("Operation error on comment endpoint: {}", message)
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message))
    }

    private fun unauthorized(message: String): ResponseEntity<*> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message))
    }

    private fun notFound(message: String): ResponseEntity<*> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, message))
    }
}
