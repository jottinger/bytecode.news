/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.model.LoginRequest
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** HTTP adapter for authentication endpoints */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val eventGateway: EventGateway,
    @Value("\${streampack.blog.service-id:blog-service}") private val serviceId: String,
) {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<*> {
        val provenance =
            Provenance(protocol = Protocol.HTTP, serviceId = serviceId, replyTo = "auth/login")
        val message =
            MessageBuilder.withPayload(request).setHeader(Provenance.HEADER, provenance).build()

        return when (val result = eventGateway.process(message)) {
            is OperationResult.Success -> ResponseEntity.ok(result.payload)
            is OperationResult.Error -> {
                logger.debug("Login failed: {}", result.message)
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, result.message))
            }
            is OperationResult.NotHandled -> {
                logger.warn("Login request was not handled by any operation")
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
}
