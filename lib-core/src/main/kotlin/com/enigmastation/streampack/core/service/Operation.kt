/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import com.enigmastation.streampack.core.model.Declined
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import org.springframework.messaging.Message

/**
 * A self-selecting message handler in the global operation chain.
 *
 * Operations are the workhorses of the event system. Every Operation is registered globally and
 * decides for itself which messages it can handle via [canHandle]. The [OperationService] calls all
 * operations in [priority] order, stopping at the first one that produces a terminal
 * [OperationResult].
 *
 * ## Implementing an Operation
 * 1. Set [priority] -- lower values run first. Use 0-20 for high priority, 50 for normal, 80+ for
 *    catch-alls.
 * 2. Implement [canHandle] as a cheap check. Inspect the Provenance header, payload type, or
 *    message metadata to decide if this operation is even relevant. Return false to skip entirely.
 * 3. Implement [execute] to do the actual work. Return an [OperationResult.Success] or
 *    [OperationResult.Error] for a definitive answer, [Declined] to pass with diagnostic info
 *    (logged by OperationService), or null to silently pass to the next operation in the chain.
 *
 * ## Lifecycle
 *
 * Operations are Spring beans -- register them with @Component or @Bean. The [OperationService]
 * collects all Operation beans at startup and sorts them by priority.
 */
interface Operation {
    /** Execution order within the chain. Lower values run first. */
    val priority: Int

    /** Quick pre-flight check: is this operation relevant for this message? */
    fun canHandle(message: Message<*>): Boolean = true

    /**
     * Process the message and produce a result.
     *
     * Returns [OperationResult.Success] or [OperationResult.Error] to short-circuit the chain,
     * [Declined] to pass with diagnostic info (logged by OperationService), or null to silently
     * pass to the next operation.
     */
    fun execute(message: Message<*>): OperationOutcome?
}
