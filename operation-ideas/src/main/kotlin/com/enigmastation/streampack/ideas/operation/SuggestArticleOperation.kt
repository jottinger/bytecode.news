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
import com.fasterxml.jackson.databind.JsonNode
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
        logger.info("SuggestArticleOperation: fetching source url={}", url)
        val fetched =
            when (val result = contentFetcher.fetch(url)) {
                is FetchOutcome.Success -> result
                is FetchOutcome.Failure -> {
                    logger.info(
                        "SuggestArticleOperation: fetch failed url={} certificateInvalid={} message={}",
                        url,
                        result.certificateInvalid,
                        result.message,
                    )
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
        logger.info(
            "SuggestArticleOperation: fetch succeeded finalUrl={} extractedChars={} warnings={}",
            fetched.finalUrl,
            fetched.extractedText.length,
            fetched.warnings.size,
        )

        val aiAttempt = generateAiDraft(fetched.title, fetched.extractedText)
        val aiDraft = aiAttempt.draft
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
            if (aiAttempt.note != null) {
                append("\n\nDebug:\n- ")
                append(aiAttempt.note)
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
                logger.info(
                    "SuggestArticleOperation: draft created title='{}' tags={} aiNote={}",
                    draftTitle.take(120),
                    tags.size,
                    aiAttempt.note ?: "none",
                )
                val warningSuffix =
                    if (fetched.warnings.isEmpty()) {
                        ""
                    } else {
                        " ${fetched.warnings.joinToString(" ")}"
                    }
                val aiSuffix = aiAttempt.note?.let { " $it" } ?: ""
                OperationResult.Success(
                    "Suggested draft saved from ${fetched.finalUrl}.$warningSuffix$aiSuffix"
                )
            }
            is OperationResult.Error -> {
                logger.info("SuggestArticleOperation: create draft failed error={}", result.message)
                result
            }
            is OperationResult.NotHandled -> {
                logger.info("SuggestArticleOperation: create draft was not handled")
                OperationResult.Error("Could not create draft from suggestion")
            }
        }
    }

    private fun generateAiDraft(title: String, extractedText: String): AiDraftAttempt {
        val ai =
            aiServiceProvider.ifAvailable
                ?: return AiDraftAttempt(null, "AI unavailable; used extraction fallback.").also {
                    logger.info(
                        "SuggestArticleOperation: AI unavailable; using extraction fallback"
                    )
                }

        val systemPrompt =
            """
            You draft a technical blog summary from extracted source text.
            Return ONLY valid JSON with this exact schema:
            {"title":"string","summary":"string","tags":["tag1","tag2"]}

            Rules:
            - Keep strong signal-to-noise.
            - Preserve key technical details and tradeoffs.
            - Prefer classic essay-style prose when the source has enough depth (often ~3-5 paragraphs), but do not pad.
            - Use fewer paragraphs when source material is thin.
            - Do not include headings or bullet lists in summary.
            - Do not speculate beyond available evidence.
            - You may add brief contextual commentary only when it is well-established and clearly attributed.
            - tags must be lowercase, no leading '#', no underscores.
            - return 3-5 tags.
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

        logger.info(
            "SuggestArticleOperation: requesting AI draft sourceTitle='{}' extractedChars={}",
            title.take(120),
            extractedText.length,
        )
        val response =
            ai.promptForObjectWithRaw(systemPrompt, userPrompt, AiDraftResponse::class.java)

        val structured = response.value
        if (structured != null) {
            val parsedTitle = structured.title?.trim().orEmpty().ifBlank { title }
            val parsedSummary =
                structured.summary?.trim().orEmpty().ifBlank { null }
                    ?: return AiDraftAttempt(
                            null,
                            "AI structured response missing 'summary'; used extraction fallback.",
                        )
                        .also {
                            logger.info(
                                "SuggestArticleOperation: structured AI response missing summary; using fallback"
                            )
                        }
            val parsedTags = structured.tags.mapNotNull(::normalizeTag).take(5)
            logger.info(
                "SuggestArticleOperation: structured AI success title='{}' tags={}",
                parsedTitle.take(120),
                parsedTags.size,
            )
            return AiDraftAttempt(AiDraft(parsedTitle, parsedSummary, parsedTags), null)
        }

        val raw =
            response.raw
                ?: return AiDraftAttempt(null, "AI returned no response; used extraction fallback.")
                    .also {
                        logger.info(
                            "SuggestArticleOperation: AI returned no response; using fallback"
                        )
                    }
        val parsed =
            parseAiJson(raw)
                ?: return AiDraftAttempt(
                        null,
                        "AI structured response parse failed; used extraction fallback.",
                    )
                    .also {
                        logger.info(
                            "SuggestArticleOperation: structured parse failed rawChars={}; using fallback",
                            raw.length,
                        )
                    }

        return try {
            val parsedTitle = parsed.path("title").asText("").trim().ifBlank { title }
            val parsedSummary =
                parsed.path("summary").asText("").trim().ifBlank { null }
                    ?: return AiDraftAttempt(
                            null,
                            "AI JSON missing 'summary'; used extraction fallback.",
                        )
                        .also {
                            logger.info(
                                "SuggestArticleOperation: JSON fallback missing summary; using fallback"
                            )
                        }
            val parsedTags =
                parsed
                    .path("tags")
                    .takeIf { it.isArray }
                    ?.mapNotNull { child -> normalizeTag(child.asText("")) }
                    .orEmpty()
                    .take(5)
            logger.info(
                "SuggestArticleOperation: JSON fallback succeeded title='{}' tags={}",
                parsedTitle.take(120),
                parsedTags.size,
            )
            AiDraftAttempt(
                AiDraft(parsedTitle, parsedSummary, parsedTags),
                "AI structured parse failed; JSON fallback succeeded.",
            )
        } catch (_: Exception) {
            logger.info("SuggestArticleOperation: JSON fallback threw; using extraction fallback")
            AiDraftAttempt(null, "AI response parse failed; used extraction fallback.")
        }
    }

    private fun parseAiJson(response: String): JsonNode? {
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
                }
                .distinct()

        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            try {
                return objectMapper.readTree(candidate)
            } catch (_: Exception) {}
        }
        return null
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

    private data class AiDraftAttempt(val draft: AiDraft?, val note: String?)

    private data class AiDraftResponse(
        val title: String? = null,
        val summary: String? = null,
        val tags: List<String> = emptyList(),
    )

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
