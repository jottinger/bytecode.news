/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import com.enigmastation.streampack.core.model.Declined
import com.enigmastation.streampack.core.model.OperationResult
import org.slf4j.LoggerFactory
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
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
class OperationService(operations: List<Operation>) {
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
        for (op in sortedOperations) {
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
                if (result is OperationResult) {
                    logger.debug(
                        "Operation {} produced {}",
                        op::class.simpleName,
                        result::class.simpleName,
                    )
                    return result
                }
                logger.debug("Operation {} returned null, continuing chain", op::class.simpleName)
            }
        }
        logger.debug("No operation handled message {}", message.headers.id)
        return OperationResult.NotHandled
    }
}
