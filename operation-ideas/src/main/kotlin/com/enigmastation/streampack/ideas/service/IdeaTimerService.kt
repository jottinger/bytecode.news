/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ideas.service

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.integration.TickListener
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ProvenanceStateService
import com.enigmastation.streampack.core.service.TransformerChainService
import com.enigmastation.streampack.ideas.config.IdeaProperties
import com.enigmastation.streampack.ideas.model.ActiveIdeaSession
import com.enigmastation.streampack.ideas.model.IdeaSessionState
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/** Monitors active article idea sessions for inactivity timeout */
@Component
class IdeaTimerService(
    @Qualifier("egressChannel") private val egressChannel: MessageChannel,
    private val stateService: ProvenanceStateService,
    private val transformerChain: TransformerChainService,
    private val eventGateway: EventGateway,
    private val ideaProperties: IdeaProperties,
) : TickListener {

    private val logger = LoggerFactory.getLogger(IdeaTimerService::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val activeSessions = ConcurrentHashMap<String, ActiveIdeaSession>()

    fun registerSession(provenanceUri: String, now: Instant) {
        activeSessions[provenanceUri] = ActiveIdeaSession(provenanceUri, now)
    }

    fun unregisterSession(provenanceUri: String) {
        activeSessions.remove(provenanceUri)
    }

    fun resetSession(provenanceUri: String) {
        activeSessions[provenanceUri]?.lastActivityAt = Instant.now()
    }

    fun hasActiveSession(provenanceUri: String): Boolean = activeSessions.containsKey(provenanceUri)

    override fun onTick(now: Instant) {
        val timeout = Duration.ofMinutes(ideaProperties.sessionTimeoutMinutes)
        activeSessions.values.toList().forEach { session ->
            val inactive = Duration.between(session.lastActivityAt, now)
            if (inactive >= timeout) {
                handleTimeout(session)
            }
        }
    }

    private fun handleTimeout(session: ActiveIdeaSession) {
        val data = stateService.getState(session.provenanceUri, IdeaSessionState.STATE_KEY)
        if (data == null) {
            activeSessions.remove(session.provenanceUri)
            return
        }

        val state = objectMapper.convertValue<IdeaSessionState>(data)
        finalizeIdea(state, session.provenanceUri)

        val blockCount = state.contentBlocks.size
        sendToEgress(
            "Idea session timed out. Saved draft: \"${state.title}\" ($blockCount content block${if (blockCount != 1) "s" else ""})",
            session.provenanceUri,
        )
        logger.info(
            "Idea session timed out for {}, saved draft: {}",
            session.provenanceUri,
            state.title,
        )
    }

    /** Creates a draft post from the session state via EventGateway */
    fun finalizeIdea(state: IdeaSessionState, provenanceUri: String) {
        val request = state.toCreateContentRequest()
        val provenance = Provenance(protocol = Protocol.HTTP, serviceId = "ideas", replyTo = "")
        val message =
            MessageBuilder.withPayload(request as Any)
                .setHeader(Provenance.HEADER, provenance)
                .build()
        eventGateway.send(message)

        stateService.clearState(provenanceUri, IdeaSessionState.STATE_KEY)
        activeSessions.remove(provenanceUri)
    }

    private fun sendToEgress(text: String, destinationUri: String) {
        try {
            val provenance = Provenance.decode(destinationUri)
            val raw = OperationResult.Success(text)
            val transformed = transformerChain.apply(raw, provenance)
            val message =
                MessageBuilder.withPayload(transformed as Any)
                    .setHeader(Provenance.HEADER, provenance)
                    .build()
            egressChannel.send(message)
        } catch (e: Exception) {
            logger.warn("Failed to send idea notification to {}: {}", destinationUri, e.message)
        }
    }
}
