/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ideas.operation

import com.enigmastation.streampack.ai.service.AiService
import com.enigmastation.streampack.blog.model.CreateContentRequest
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.parser.CommandArgSpec
import com.enigmastation.streampack.core.parser.CommandMatchResult
import com.enigmastation.streampack.core.parser.CommandPattern
import com.enigmastation.streampack.core.parser.CommandPatternMatcher
import com.enigmastation.streampack.core.parser.HttpUrlArgType
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.ideas.service.FetchOutcome
import com.enigmastation.streampack.ideas.service.SuggestedContentFetcher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/** Admin command: suggest a draft article from a source URL. */
@Component
class SuggestArticleOperation(
    private val contentFetcher: SuggestedContentFetcher,
    private val aiServiceProvider: ObjectProvider<AiService>,
    private val eventGateway: com.enigmastation.streampack.core.integration.EventGateway,
) : TypedOperation<String>(String::class) {

    private val objectMapper = jacksonObjectMapper()

    override val priority: Int = 45
    override val addressed: Boolean = true
    override val operationGroup: String = "ideas"

    override fun canHandle(payload: String, message: Message<*>): Boolean =
        matcher.match(payload) != null

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        return when (val parsed = matcher.match(payload)) {
            is CommandMatchResult.Match -> {
                requireRole(message, Role.ADMIN)?.let {
                    return it
                }
                val url = parsed.captures["url"] as String
                handleSuggest(url, message)
            }
            is CommandMatchResult.InvalidArgument,
            is CommandMatchResult.MissingArguments,
            is CommandMatchResult.TooManyArguments,
            null -> OperationResult.Error("Usage: suggest <http(s)://url>")
        }
    }

    private fun handleSuggest(url: String, message: Message<*>): OperationOutcome {
        val fetched =
            when (val result = contentFetcher.fetch(url)) {
                is FetchOutcome.Success -> result
                is FetchOutcome.Failure -> {
                    return if (result.certificateInvalid) {
                        OperationResult.Error(
                            "TLS certificate validation failed for $url. " +
                                "Warning: the source certificate is invalid or untrusted."
                        )
                    } else {
                        OperationResult.Error("Could not fetch source URL: ${result.message}")
                    }
                }
            }

        val aiDraft = generateAiDraft(fetched.title, fetched.extractedText)
        val draftTitle = aiDraft?.title?.ifBlank { null } ?: fetched.title.take(180)
        val summary = aiDraft?.summary?.ifBlank { null } ?: fallbackSummary(fetched.extractedText)
        val tags =
            (listOf("_idea") + (aiDraft?.tags ?: emptyList())).mapNotNull(::normalizeTag).distinct()

        val markdown = buildString {
            append(summary.trim())
            append("\n\nSource: ")
            append(fetched.finalUrl)
            if (fetched.warnings.isNotEmpty()) {
                append("\n\nWarnings:\n")
                fetched.warnings.forEach { warning ->
                    append("- ")
                    append(warning)
                    append("\n")
                }
            }
            append("\n\n_Generated from !suggest for admin review. Edit before publishing._")
        }

        val sourceProvenance = message.headers[Provenance.HEADER] as? Provenance
        val provenance =
            Provenance(
                protocol = Protocol.HTTP,
                serviceId = "ideas",
                replyTo = "ideas/suggest",
                user = sourceProvenance?.user,
            )

        val request =
            CreateContentRequest(title = draftTitle, markdownSource = markdown, tags = tags)
        val createMessage =
            MessageBuilder.withPayload(request as Any)
                .setHeader(Provenance.HEADER, provenance)
                .build()

        return when (val result = eventGateway.process(createMessage)) {
            is OperationResult.Success -> {
                val warningSuffix =
                    if (fetched.warnings.isEmpty()) {
                        ""
                    } else {
                        " ${fetched.warnings.joinToString(" ")}"
                    }
                OperationResult.Success(
                    "Suggested draft saved from ${fetched.finalUrl}.$warningSuffix"
                )
            }
            is OperationResult.Error -> result
            is OperationResult.NotHandled ->
                OperationResult.Error("Could not create draft from suggestion")
        }
    }

    private fun generateAiDraft(title: String, extractedText: String): AiDraft? {
        val ai = aiServiceProvider.ifAvailable ?: return null

        val systemPrompt =
            """
            You draft a technical blog summary from extracted source text.
            Return ONLY valid JSON with this exact schema:
            {"title":"string","summary":"string","tags":["tag1","tag2"]}

            Rules:
            - Keep strong signal-to-noise.
            - Preserve key technical details and tradeoffs.
            - tags must be lowercase, no leading '#', no underscores.
            - no markdown fences.
            """
                .trimIndent()

        val userPrompt =
            """
            Source title: $title

            Extracted text:
            ${extractedText.take(14000)}
            """
                .trimIndent()

        val response = ai.prompt(systemPrompt, userPrompt) ?: return null
        return try {
            val node = objectMapper.readTree(response)
            val parsedTitle = node.path("title").asText("").trim().ifBlank { title }
            val parsedSummary =
                node.path("summary").asText("").trim().ifBlank { null } ?: return null
            val parsedTags =
                node
                    .path("tags")
                    .takeIf { it.isArray }
                    ?.mapNotNull { child -> normalizeTag(child.asText("")) }
                    .orEmpty()
            AiDraft(parsedTitle, parsedSummary, parsedTags)
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackSummary(extractedText: String): String {
        return buildString {
            append("## Suggested Summary\n\n")
            append(extractedText.take(2500))
        }
    }

    private fun normalizeTag(raw: String): String? {
        val cleaned = raw.trim().lowercase().removePrefix("#")
        if (cleaned.isBlank()) return null
        if (cleaned.startsWith("_")) return null
        return cleaned
    }

    private data class AiDraft(val title: String, val summary: String, val tags: List<String>)

    private companion object {
        private val matcher =
            CommandPatternMatcher(
                listOf(
                    CommandPattern(
                        name = "suggest",
                        literals = listOf("suggest"),
                        args = listOf(CommandArgSpec("url", HttpUrlArgType)),
                    )
                )
            )
    }
}
