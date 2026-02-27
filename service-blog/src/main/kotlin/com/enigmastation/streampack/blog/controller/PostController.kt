/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.config.BlogProperties
import com.enigmastation.streampack.blog.model.ContentDetail
import com.enigmastation.streampack.blog.model.ContentListResponse
import com.enigmastation.streampack.blog.model.CreateContentHttpRequest
import com.enigmastation.streampack.blog.model.CreateContentRequest
import com.enigmastation.streampack.blog.model.CreateContentResponse
import com.enigmastation.streampack.blog.model.EditContentHttpRequest
import com.enigmastation.streampack.blog.model.EditContentRequest
import com.enigmastation.streampack.blog.model.FindContentRequest
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** HTTP adapter for public and authenticated post endpoints */
@RestController
@Tag(name = "Posts")
class PostController(
    private val eventGateway: EventGateway,
    private val jwtService: JwtService,
    blogProperties: BlogProperties,
) {
    private val serviceId = blogProperties.serviceId
    private val logger = LoggerFactory.getLogger(PostController::class.java)

    @Operation(summary = "List published posts")
    @ApiResponse(
        responseCode = "200",
        description = "Paginated list of published posts",
        content = [Content(schema = Schema(implementation = ContentListResponse::class))],
    )
    @GetMapping("/posts")
    fun listPosts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest)
        val payload = FindContentRequest.FindPublished(page, size)
        return dispatch(payload, "posts/list", user) { result -> mapError(result) }
    }

    @Operation(summary = "Get a published post by slug")
    @ApiResponse(
        responseCode = "200",
        description = "Post detail",
        content = [Content(schema = Schema(implementation = ContentDetail::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "Post not found",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @GetMapping("/posts/{year}/{month}/{slug}")
    fun getPost(
        @PathVariable @Schema(minimum = "2007", maximum = "3000") year: Int,
        @PathVariable @Schema(minimum = "1", maximum = "12") month: Int,
        @PathVariable slug: String,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest)
        val payload = FindContentRequest.FindBySlug("$year/${"%02d".format(month)}/$slug")
        return dispatch(payload, "posts/detail", user) { result -> mapError(result) }
    }

    @Operation(summary = "Search published posts")
    @ApiResponse(
        responseCode = "200",
        description = "Search results",
        content = [Content(schema = Schema(implementation = ContentListResponse::class))],
    )
    @GetMapping("/posts/search")
    fun searchPosts(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest)
        val payload = FindContentRequest.Search(q, page, size)
        return dispatch(payload, "posts/search", user) { result -> mapError(result) }
    }

    @Operation(summary = "Create a new blog post draft")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(
        responseCode = "201",
        description = "Post created",
        content = [Content(schema = Schema(implementation = CreateContentResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @PostMapping("/posts")
    fun createPost(
        @RequestBody request: CreateContentHttpRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest)
        val payload =
            CreateContentRequest(
                title = request.title,
                markdownSource = request.markdownSource,
                tags = request.tags,
                categoryIds = request.categoryIds,
            )
        return dispatchCreated(payload, "posts/create", user) { result -> mapError(result) }
    }

    @Operation(summary = "Edit a post")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(
        responseCode = "200",
        description = "Post updated",
        content = [Content(schema = Schema(implementation = ContentDetail::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not authorized to edit this post",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "Post not found",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))],
    )
    @PutMapping("/posts/{id}")
    fun editPost(
        @PathVariable id: UUID,
        @RequestBody request: EditContentHttpRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val user = resolveUser(httpRequest) ?: return unauthorized("Authentication required")
        val payload =
            EditContentRequest(
                id = id,
                title = request.title,
                markdownSource = request.markdownSource,
                tags = request.tags,
                categoryIds = request.categoryIds,
            )
        return dispatch(payload, "posts/edit", user) { result -> mapError(result) }
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
        user: UserPrincipal?,
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
                message.contains("Admin access required") -> HttpStatus.FORBIDDEN
                message.contains("not found", ignoreCase = true) -> HttpStatus.NOT_FOUND
                else -> HttpStatus.BAD_REQUEST
            }
        logger.debug("Operation error on post endpoint: {}", message)
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message))
    }

    private fun unauthorized(message: String): ResponseEntity<*> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message))
    }
}
