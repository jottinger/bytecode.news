/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.sentiment.operation

import com.enigmastation.streampack.ai.service.AiService
import com.enigmastation.streampack.core.model.MessageDirection
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.MessageLogService
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.sentiment.model.SentimentRequest
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/**
 * Analyzes the sentiment of recent conversation for a target channel or user. Admin-only operation
 * that pulls message logs, formats them as a transcript, and sends them to an LLM for scoring.
 *
 * When the target differs from the source channel, the response is sent via DM to avoid leaking
 * cross-channel sentiment publicly.
 */
@Component
@ConditionalOnProperty(prefix = "streampack.ai", name = ["enabled"], havingValue = "true")
class SentimentOperation(
    private val aiService: AiService,
    private val messageLogService: MessageLogService,
) : TranslatingOperation<SentimentRequest>(SentimentRequest::class) {

    override val priority: Int = 40
    override val addressed: Boolean = true
    override val operationGroup: String = "sentiment"

    override fun translate(payload: String, message: Message<*>): SentimentRequest? {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("sentiment ", ignoreCase = true)) return null
        val target = trimmed.substring("sentiment ".length).trim()
        if (target.isBlank()) return null

        val provenance = message.headers[Provenance.HEADER] as? Provenance ?: return null
        val targetUri = resolveTarget(target, provenance)
        return SentimentRequest(targetUri)
    }

    override fun handle(payload: SentimentRequest, message: Message<*>): OperationOutcome {
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val role = provenance?.user?.role ?: Role.GUEST

        if (role < Role.ADMIN) {
            return OperationResult.Error("Sentiment analysis requires ADMIN role")
        }

        val botNick = message.headers[Provenance.BOT_NICK] as? String ?: "bot"
        val now = Instant.now()
        val windowStart = now.minus(4, ChronoUnit.HOURS)

        val messages = messageLogService.findMessages(payload.targetUri, windowStart, now, 100)
        if (messages.isEmpty()) {
            return OperationResult.Error("No recent messages found for ${payload.targetUri}")
        }

        val transcript = formatTranscript(messages, botNick)
        logger.info("Analyzing sentiment for {} ({} messages)", payload.targetUri, messages.size)

        val systemPrompt =
            //            """
            //            You are a conversation sentiment analyst. Analyze the following chat
            // transcript.
            //            Lines prefixed with "ext" are from external participants (humans).
            //            Lines prefixed with "int" are from the bot - include them for context but
            // do NOT
            //            factor the bot's messages into the sentiment score.
            //
            //            Score the overall sentiment from -10 (extremely negative/hostile) to +10
            //            (extremely positive/enthusiastic). Note which participants drive the score
            //            most and why.
            //
            //            Keep your response concise - under 300 characters. Format:
            //            Score: N/10. Brief explanation.
            //            """
            """
            You are a sentiment analyst.

            Input: chat transcript (up to 100 lines).
            Lines prefixed:
            - "ext" = human participants (score these)
            - "int" = bot/system (context only; ignore for scoring)

            Evaluate:
            1) Overall sentiment (-10 hostile to +10 highly positive; 0 neutral)
            2) Emotional intensity (low / moderate / high)
            3) Volatility (stable / shifting / escalating)
            4) Primary drivers (participants influencing tone)
            5) Dominant themes (topics affecting sentiment)

            Aggregation rule:
            Estimate sentiment by averaging ext-line polarity weighted by intensity
            (strong language, insults, praise, frustration).

            Responses should be under 250 characters where possible.
             
            Score: N/10. Drivers: <names>. Why: <brief>.
            """
                .trimIndent()

        val analysis = aiService.prompt(systemPrompt, transcript)

        if (analysis == null) {
            return OperationResult.Error("Failed to analyze sentiment")
        }

        val sourceUri = provenance?.encode()
        val isCrossChannel = sourceUri != null && sourceUri != payload.targetUri
        val responseProvenance =
            if (isCrossChannel && provenance != null) {
                Provenance(
                    protocol = provenance.protocol,
                    serviceId = provenance.serviceId,
                    user = provenance.user,
                    replyTo =
                        message.headers["nick"] as? String
                            ?: provenance.user?.displayName
                            ?: provenance.user?.username
                            ?: provenance.replyTo,
                )
            } else {
                null
            }

        return OperationResult.Success(
            "Sentiment for ${payload.targetUri}: $analysis",
            provenance = responseProvenance,
        )
    }

    /** Formats log entries as a transcript with ext/int prefixes */
    private fun formatTranscript(
        messages: List<com.enigmastation.streampack.core.entity.MessageLog>,
        botNick: String,
    ): String {
        return messages.joinToString("\n") { entry ->
            val prefix =
                if (entry.direction == MessageDirection.OUTBOUND || entry.sender == botNick) {
                    "int"
                } else {
                    "ext"
                }
            "$prefix ${entry.sender} ${entry.content}"
        }
    }

    /** Resolves a target string to a provenance URI, inheriting context from source */
    private fun resolveTarget(target: String, source: Provenance): String {
        if (target.contains("://")) return target
        return Provenance(
                protocol = source.protocol,
                serviceId = source.serviceId,
                replyTo = target,
            )
            .encode()
    }
}
