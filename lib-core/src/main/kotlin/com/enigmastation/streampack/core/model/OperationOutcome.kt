/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.model

/**
 * What an operation can produce when it processes a message.
 *
 * This is the return type of
 * [Operation.execute()][com.enigmastation.streampack.core.service.Operation.execute]. It separates
 * into two families:
 * - [OperationResult] (Success, Error, NotHandled) -- terminal outcomes that leave the operation
 *   chain and reach the caller via the EventGateway.
 * - [Declined] -- a non-terminal signal consumed by the OperationService. It continues the chain
 *   but carries diagnostic information for logging.
 *
 * Adapters and gateway callers only see [OperationResult]; [Declined] is internal to the chain.
 */
sealed interface OperationOutcome
