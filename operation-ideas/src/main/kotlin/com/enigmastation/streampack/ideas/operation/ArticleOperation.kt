/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ideas.operation

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ProvenanceStateService
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.ideas.model.IdeaSessionState
import com.enigmastation.streampack.ideas.service.IdeaTimerService
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/** Captures article ideas through a stateful conversation flow across all protocols */
@Component
class ArticleOperation(
    private val stateService: ProvenanceStateService,
    private val timerService: IdeaTimerService,
    private val eventGateway: EventGateway,
) : TypedOperation<String>(String::class) {

    private val objectMapper = jacksonObjectMapper()

    override val priority: Int = 50
    override val addressed: Boolean = true
    override val operationGroup: String = "ideas"

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        val cmd = payload.compress().lowercase()
        if (cmd == "article" || cmd.startsWith("article ")) return true

        if (!timerService.hasActiveSession(userStateKey(message))) return false

        return cmd.startsWith("content ") || cmd == "done" || cmd == "cancel"
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome? {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance available.")
        val userKey = userStateKey(message)
        val compressed = payload.compress()
        val cmd = compressed.lowercase()

        return when {
            cmd == "article" || cmd.startsWith("article ") -> {
                val args = compressed.substringAfter("article", "").trim()
                startSession(args, userKey, provenance.encode(), message)
            }
            cmd.startsWith("content ") -> {
                val text = compressed.substringAfter("content", "").trim()
                addContent(text, userKey)
            }
            cmd == "done" -> finalize(userKey)
            cmd == "cancel" -> cancel(userKey)
            else -> null
        }
    }

    private fun startSession(
        args: String,
        userKey: String,
        channelUri: String,
        message: Message<*>,
    ): OperationOutcome {
        val existing = stateService.getState(userKey, IdeaSessionState.STATE_KEY)
        if (existing != null) {
            val state = objectMapper.convertValue<IdeaSessionState>(existing)
            return OperationResult.Error(
                "An idea session is already active: \"${state.title}\". " +
                    "Use '{{ref:done}}' to save or '{{ref:cancel}}' to discard it first."
            )
        }

        val title = parseTitle(args)
        if (title.isBlank()) {
            return OperationResult.Error(
                "Title is required. Usage: '{{ref:article \"My Article Title\"}}' or '{{ref:article My Title}}'"
            )
        }

        val playerName = senderName(message)
        val now = Instant.now()

        val state =
            IdeaSessionState(
                title = title,
                submitterName = playerName,
                sourceProvenance = channelUri,
                startedAt = now.epochSecond,
            )

        stateService.setState(
            userKey,
            IdeaSessionState.STATE_KEY,
            objectMapper.convertValue<Map<String, Any>>(state),
        )
        timerService.registerSession(userKey, now)

        return OperationResult.Success(
            "Idea session started: \"$title\". " +
                "Use '{{ref:content <text>}}' to add body paragraphs, " +
                "'{{ref:done}}' to save, or '{{ref:cancel}}' to discard."
        )
    }

    private fun addContent(text: String, userKey: String): OperationOutcome {
        val data =
            stateService.getState(userKey, IdeaSessionState.STATE_KEY)
                ?: return OperationResult.Error(
                    "No idea session in progress. Use '{{ref:article \"title\"}}' to start one."
                )

        if (text.isBlank()) {
            return OperationResult.Error("Content text is required after '{{ref:content}}'.")
        }

        val state = objectMapper.convertValue<IdeaSessionState>(data)
        val updated = state.copy(contentBlocks = state.contentBlocks + text)

        stateService.setState(
            userKey,
            IdeaSessionState.STATE_KEY,
            objectMapper.convertValue<Map<String, Any>>(updated),
        )
        timerService.resetSession(userKey)

        val count = updated.contentBlocks.size
        return OperationResult.Success(
            "Content block #$count added to \"${state.title}\". " +
                "Use '{{ref:content <text>}}' to add more, or '{{ref:done}}' to save."
        )
    }

    private fun finalize(userKey: String): OperationOutcome {
        val data =
            stateService.getState(userKey, IdeaSessionState.STATE_KEY)
                ?: return OperationResult.Error(
                    "No idea session in progress. Use '{{ref:article \"title\"}}' to start one."
                )

        val state = objectMapper.convertValue<IdeaSessionState>(data)
        dispatchDraftPost(state)

        stateService.clearState(userKey, IdeaSessionState.STATE_KEY)
        timerService.unregisterSession(userKey)

        val blockCount = state.contentBlocks.size
        return OperationResult.Success(
            "Idea saved as draft: \"${state.title}\" " +
                "($blockCount content block${if (blockCount != 1) "s" else ""})."
        )
    }

    private fun cancel(userKey: String): OperationOutcome {
        val data = stateService.getState(userKey, IdeaSessionState.STATE_KEY)
        if (data == null) {
            return OperationResult.Success("No idea session in progress.")
        }

        val state = objectMapper.convertValue<IdeaSessionState>(data)
        stateService.clearState(userKey, IdeaSessionState.STATE_KEY)
        timerService.unregisterSession(userKey)

        return OperationResult.Success("Idea session cancelled. \"${state.title}\" was discarded.")
    }

    /** Dispatches a CreateContentRequest through EventGateway to create a draft post */
    private fun dispatchDraftPost(state: IdeaSessionState) {
        val request = state.toCreateContentRequest()
        val provenance = Provenance(protocol = Protocol.HTTP, serviceId = "ideas", replyTo = "")
        val message =
            MessageBuilder.withPayload(request as Any)
                .setHeader(Provenance.HEADER, provenance)
                .build()
        eventGateway.send(message)
    }

    /** Extracts the title, stripping surrounding quotes if present */
    private fun parseTitle(args: String): String {
        val trimmed = args.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length > 1) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
        return trimmed
    }

    /** Derives a per-user state key by appending the sender nick to the channel provenance */
    private fun userStateKey(message: Message<*>): String {
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val channelUri = provenance?.encode() ?: "unknown"
        val nick =
            message.headers["nick"] as? String ?: provenance?.user?.username ?: return channelUri
        return "$channelUri/$nick"
    }
}
