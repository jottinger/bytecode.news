/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.controller

import com.enigmastation.streampack.github.model.DeliveryMode
import com.enigmastation.streampack.github.model.GitHubIssueEvent
import com.enigmastation.streampack.github.model.GitHubPullRequestEvent
import com.enigmastation.streampack.github.model.GitHubReleaseEvent
import com.enigmastation.streampack.github.repository.GitHubRepoRepository
import com.enigmastation.streampack.github.service.GitHubWebhookService
import com.enigmastation.streampack.github.service.WebhookSecretCipher
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.net.URLDecoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhooks/github")
class GitHubWebhookController(
    private val repoRepository: GitHubRepoRepository,
    private val secretCipher: WebhookSecretCipher,
    private val webhookService: GitHubWebhookService,
) {
    private val objectMapper = ObjectMapper()
    private val logger = LoggerFactory.getLogger(GitHubWebhookController::class.java)

    @Operation(
        summary = "Receive GitHub webhook deliveries",
        description = "Validates X-Hub-Signature-256 and fans out issue/PR/release notifications.",
        responses =
            [
                ApiResponse(responseCode = "202", description = "Delivery accepted"),
                ApiResponse(
                    responseCode = "400",
                    description = "Malformed payload or repository metadata",
                    content = [Content(schema = Schema(hidden = true))],
                ),
                ApiResponse(
                    responseCode = "401",
                    description = "Signature mismatch",
                    content = [Content(schema = Schema(hidden = true))],
                ),
            ],
    )
    @PostMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE]
    )
    fun receive(
        @RequestBody body: ByteArray,
        @RequestHeader("X-Hub-Signature-256", required = false) signature: String?,
        @RequestHeader("X-GitHub-Event", required = false) event: String?,
        @RequestHeader("Content-Type", required = false) contentType: String?,
    ): ResponseEntity<Void> {
        if (signature.isNullOrBlank() || event.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

        val payload =
            extractJsonPayload(body, contentType) ?: return ResponseEntity.badRequest().build()
        val root: JsonNode =
            try {
                objectMapper.readTree(payload)
            } catch (ex: Exception) {
                logger.warn("Failed to parse GitHub webhook payload: {}", ex.message)
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }

        val fullName = root.path("repository").path("full_name").asText()
        if (fullName.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
        val parts = fullName.split("/")
        if (parts.size != 2) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
        val repo =
            repoRepository.findByOwnerAndName(parts[0], parts[1])
                ?: return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        if (repo.deliveryMode != DeliveryMode.WEBHOOK || repo.webhookSecret.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build()
        }

        val secret =
            try {
                secretCipher.decrypt(repo.webhookSecret)
            } catch (ex: Exception) {
                logger.warn("Failed to decrypt webhook secret for {}: {}", fullName, ex.message)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }

        if (!verifySignature(signature, secret, body)) {
            logger.warn("Invalid GitHub webhook signature for {}", fullName)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        when (event) {
            "issues" -> {
                val issueEvent = objectMapper.treeToValue(root, GitHubIssueEvent::class.java)
                webhookService.handleIssue(repo, issueEvent)
            }
            "pull_request" -> {
                val prEvent = objectMapper.treeToValue(root, GitHubPullRequestEvent::class.java)
                webhookService.handlePullRequest(repo, prEvent)
            }
            "release" -> {
                val releaseEvent = objectMapper.treeToValue(root, GitHubReleaseEvent::class.java)
                webhookService.handleRelease(repo, releaseEvent)
            }
            "ping" -> logger.debug("Received GitHub webhook ping for {}", fullName)
            else -> logger.debug("Ignoring unsupported GitHub event {} for {}", event, fullName)
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    private fun extractJsonPayload(body: ByteArray, contentType: String?): ByteArray? {
        if (contentType?.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE) != true) {
            return body
        }
        val encoded =
            body
                .toString(Charsets.UTF_8)
                .split("&")
                .firstOrNull { it.startsWith("payload=") }
                ?.substringAfter("payload=") ?: return null
        val decoded = URLDecoder.decode(encoded, Charsets.UTF_8)
        return decoded.toByteArray(Charsets.UTF_8)
    }

    private fun verifySignature(header: String, secret: String, body: ByteArray): Boolean {
        if (!header.startsWith("sha256=")) return false
        val expectedHex = header.removePrefix("sha256=")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val actual = mac.doFinal(body)
        val actualHex = actual.joinToString("") { "%02x".format(it) }
        val expectedBytes = hexToBytes(expectedHex)
        val actualBytes = hexToBytes(actualHex)
        if (expectedBytes.size != actualBytes.size) return false
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    private fun hexToBytes(input: String): ByteArray {
        val clean = input.trim()
        val data = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            val first = Character.digit(clean[i], 16)
            val second = Character.digit(clean[i + 1], 16)
            data[i / 2] = ((first shl 4) + second).toByte()
            i += 2
        }
        return data
    }
}
