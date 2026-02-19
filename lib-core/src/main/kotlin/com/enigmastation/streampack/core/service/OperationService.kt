/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import com.enigmastation.streampack.core.config.StreampackProperties
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.Declined
import com.enigmastation.streampack.core.model.FanOut
import com.enigmastation.streampack.core.model.LoggingRequest
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service

/**
 * Orchestrates the global operation chain.
 *
 * Collects all [Operation] beans, sorts them by priority, and runs them against each incoming
 * message. The first operation to return a terminal [OperationResult] wins -- the chain
 * short-circuits and that result is returned to the caller via the egress path.
 *
 * [Declined] results are consumed here (logged with operation context) and the chain continues. If
 * no operation handles the message, [OperationResult.NotHandled] is returned.
 */
@Service
class OperationService(
    operations: List<Operation>,
    private val properties: StreampackProperties,
    @Lazy private val eventGateway: EventGateway,
    @Qualifier("egressChannel") private val egressChannel: MessageChannel,
) {
    private val logger = LoggerFactory.getLogger(OperationService::class.java)
    private val sortedOperations = operations.sortedBy { it.priority }

    /**
     * Checks whether any non-addressed operation is interested in this message.
     *
     * Protocol adapters with trigger detection (e.g. IRC) call this before submitting unaddressed
     * messages. If no non-addressed operation claims interest, the message is dropped silently.
     */
    fun hasUnaddressedInterest(message: Message<*>): Boolean =
        sortedOperations.any { !it.addressed && it.canHandle(message) }

    /** Receives from the ingress channel and returns the result to the gateway's reply channel */
    @ServiceActivator(inputChannel = "ingressChannel")
    fun process(message: Message<*>): OperationResult {
        val hopCount = message.headers[FanOut.HOP_COUNT_HEADER] as? Int ?: 0
        val result = processChain(message)
        publishToEgress(result, message, hopCount)
        return result
    }

    /** Runs the message through the operation chain and returns the result */
    private fun processChain(message: Message<*>): OperationResult {
        if (message.payload is LoggingRequest) {
            return OperationResult.NotHandled
        }

        val hopCount = message.headers[FanOut.HOP_COUNT_HEADER] as? Int ?: 0
        if (hopCount > properties.maxHops) {
            logger.warn(
                "Message {} exceeded maximum hop count ({}/{})",
                message.headers.id,
                hopCount,
                properties.maxHops,
            )
            return OperationResult.Error("Maximum hop count exceeded")
        }

        val isAddressed = message.headers[Provenance.ADDRESSED] as? Boolean ?: true

        for (op in sortedOperations) {
            if (op.addressed && !isAddressed) continue
            if (op.canHandle(message)) {
                logger.debug(
                    "Operation {} handling message {}",
                    op::class.simpleName,
                    message.headers.id,
                )
                val result = op.execute(message)
                if (result is Declined) {
                    logger.info(
                        "Operation {} declined message {}: {}",
                        op::class.simpleName,
                        message.headers.id,
                        result.reason,
                    )
                    continue
                }
                if (result is FanOut) {
                    logger.debug(
                        "Operation {} produced FanOut with {} messages",
                        op::class.simpleName,
                        result.messages.size,
                    )
                    return dispatchFanOut(result, hopCount)
                }
                if (result is OperationResult) {
                    logger.debug(
                        "Operation {} produced {}",
                        op::class.simpleName,
                        result::class.simpleName,
                    )
                    if (result is OperationResult.Success) {
                        logger.debug("Message {} generated {}", message.headers.id, result.payload)
                    }
                    return result
                }
                logger.debug("Operation {} returned null, continuing chain", op::class.simpleName)
            }
        }
        logger.debug("No operation handled message {}", message.headers.id)
        return OperationResult.NotHandled
    }

    /** Publishes a terminal result to the egress channel, then re-injects if loopback is set */
    private fun publishToEgress(result: OperationResult, inputMessage: Message<*>, hopCount: Int) {
        val inputProvenance = inputMessage.headers[Provenance.HEADER] as? Provenance ?: return
        val provenance =
            if (result is OperationResult.Success && result.provenance != null) {
                result.provenance
            } else {
                inputProvenance
            }
        val egressMessage =
            MessageBuilder.withPayload(result as Any)
                .setHeader(Provenance.HEADER, provenance)
                .build()
        try {
            egressChannel.send(egressMessage)
        } catch (e: Exception) {
            logger.warn("Failed to publish to egress channel: {}", e.message)
        }

        if (result is OperationResult.Success && result.loopback && hopCount < properties.maxHops) {
            val loopbackMessage =
                MessageBuilder.withPayload(result.payload.toString())
                    .setHeader(Provenance.HEADER, provenance)
                    .setHeader(Provenance.ADDRESSED, true)
                    .setHeader(FanOut.HOP_COUNT_HEADER, hopCount + 1)
                    .build()
            try {
                logger.debug(
                    "Loopback: re-injecting result as addressed message (hop {})",
                    hopCount + 1,
                )
                eventGateway.send(loopbackMessage)
            } catch (e: Exception) {
                logger.warn("Failed to re-inject loopback message: {}", e.message)
            }
        }
    }

    /** Dispatches each child message with an incremented hop count */
    private fun dispatchFanOut(fanOut: FanOut, currentHopCount: Int): OperationResult {
        var dispatched = 0
        for (child in fanOut.messages) {
            val enriched =
                MessageBuilder.fromMessage(child)
                    .setHeader(FanOut.HOP_COUNT_HEADER, currentHopCount + 1)
                    .build()
            try {
                eventGateway.process(enriched)
                dispatched++
            } catch (e: Exception) {
                logger.warn("Fan-out child message {} failed: {}", child.headers.id, e.message)
            }
        }
        return OperationResult.Success("Dispatched $dispatched messages")
    }
}
