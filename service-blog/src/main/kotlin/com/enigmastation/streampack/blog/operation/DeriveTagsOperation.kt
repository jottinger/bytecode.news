/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.ai.service.AiService
import com.enigmastation.streampack.blog.model.DeriveTagsRequest
import com.enigmastation.streampack.blog.model.DeriveTagsResponse
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.taxonomy.model.FindTaxonomySnapshotRequest
import com.enigmastation.streampack.taxonomy.model.TaxonomySnapshot
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/** Non-persistent admin operation that derives AI tag suggestions from editor content. */
@Component
class DeriveTagsOperation(
    private val eventGateway: EventGateway,
    private val aiServiceProvider: ObjectProvider<AiService>,
) : TypedOperation<DeriveTagsRequest>(DeriveTagsRequest::class) {

    private val objectMapper = jacksonObjectMapper()

    override fun handle(payload: DeriveTagsRequest, message: Message<*>): OperationOutcome {
        requireRole(message, Role.ADMIN)?.let {
            return it
        }

        val title = payload.title.trim()
        val markdown = payload.markdownSource.trim()
        if (title.isBlank()) return OperationResult.Error("Title is required")
        if (markdown.isBlank()) return OperationResult.Error("Content is required")

        val aiService = aiServiceProvider.ifAvailable
        if (aiService == null) {
            return OperationResult.Error("AI service unavailable")
        }

        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val knownTags = findKnownTags(provenance)

        val systemPrompt =
            """
            You derive editorial tags for a technical article draft.
            Return ONLY valid JSON with this exact schema:
            {"tags":["tag1","tag2"]}

            Rules:
            - 3-10 tags.
            - lowercase tags, no leading '#', no underscores.
            - prefer precise technical terms over broad generic words.
            - use known tags where appropriate.
            - do not include markdown fences or prose.
            """
                .trimIndent()

        val prompt =
            """
            Title: $title

            Existing tags: ${payload.existingTags.joinToString(", ")}
            Known tags: ${knownTags.joinToString(", ")}

            Draft content:
            $markdown
            """
                .trimIndent()

        val response =
            aiService.prompt(systemPrompt, prompt)
                ?: return OperationResult.Error("Failed to derive tags")

        val tags = parseTags(response)
        if (tags.isEmpty()) {
            return OperationResult.Error("Failed to derive tags")
        }

        return OperationResult.Success(DeriveTagsResponse(tags))
    }

    private fun findKnownTags(provenance: Provenance?): List<String> {
        if (provenance == null) return emptyList()
        val message =
            MessageBuilder.withPayload(FindTaxonomySnapshotRequest as Any)
                .setHeader(Provenance.HEADER, provenance)
                .build()

        return when (val result = eventGateway.process(message)) {
            is OperationResult.Success -> {
                val snapshot = result.payload as? TaxonomySnapshot
                snapshot?.aggregate?.keys?.take(250).orEmpty()
            }
            else -> emptyList()
        }
    }

    private fun parseTags(response: String): List<String> {
        val node = parseAiJson(response)
        if (node != null) {
            val tagNode = if (node.has("tags")) node.path("tags") else node
            if (tagNode.isArray) {
                return tagNode
                    .mapNotNull { child -> normalizeTag(child.asText("")) }
                    .distinct()
            }
            if (tagNode.isTextual) {
                return tagNode
                    .asText("")
                    .split(',')
                    .mapNotNull(::normalizeTag)
                    .distinct()
            }
        }

        return response
            .split(',')
            .mapNotNull(::normalizeTag)
            .distinct()
    }

    private fun parseAiJson(response: String): com.fasterxml.jackson.databind.JsonNode? {
        val raw = response.trim()
        if (raw.isBlank()) return null

        val strippedPrefix = raw.removePrefix("json").trim()
        val candidates =
            buildList {
                add(raw)
                add(strippedPrefix)
                add(raw.removeSurrounding("```json", "```").trim())
                add(raw.removeSurrounding("```", "```").trim())
                val firstBrace = raw.indexOf('{')
                val lastBrace = raw.lastIndexOf('}')
                if (firstBrace >= 0 && lastBrace > firstBrace) {
                    add(raw.substring(firstBrace, lastBrace + 1).trim())
                }
            }.distinct()

        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            try {
                return objectMapper.readTree(candidate)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun normalizeTag(raw: String): String? {
        val cleaned = raw.trim().lowercase().removePrefix("#")
        if (cleaned.isBlank()) return null
        if (cleaned.startsWith("_")) return null
        return cleaned
    }
}
